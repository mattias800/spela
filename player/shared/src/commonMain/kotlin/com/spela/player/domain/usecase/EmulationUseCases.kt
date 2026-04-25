package com.spela.player.domain.usecase

import com.spela.player.domain.repository.CorePrunedException
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.currentPlatform

/**
 * Classifies why (if at all) the player might want to surface a
 * decision UI before loading the core. The actual decision UI lives in
 * `EmulationViewModel` + presentation layer (see #672); this enum is
 * the signal the use case hands the VM so the VM doesn't have to
 * re-derive the reason from raw sha comparisons.
 */
enum class DecisionKind {
    /** No decision needed — proceed straight to loadCore. */
    None,

    /**
     * The session has a pinned sha that differs from the server's
     * current sha for this core. The VM should consider showing Sheet A
     * from the #672 decision flow.
     */
    UpgradeAvailable,

    /**
     * The session's pinned sha has been rotated out of server-side
     * history retention. The VM should consider Sheet B.
     */
    PinPruned,

    /**
     * The previous run on this session entered rehearsal mode and the
     * player process died before reaching a clean Sheet C/D resolution.
     * Surfaces Sheet D on the next launch so the user can recover. The
     * sentinel write happens around rehearsal entry / clean exit; see
     * `SaveManager.setSessionRehearsalCrashPending` and the spec's
     * §"Crash recovery" for the full lifecycle.
     */
    RehearsalCrashed,

    /**
     * The core name was substituted for platform compatibility (macOS
     * Metal, Android variant names). The resulting sha mismatch is
     * legitimate, not an upgrade — the VM must not show Sheet A for
     * this case.
     */
    PlatformSubstitution,
}

/**
 * Result of preparing a game for emulation. Carries the resolved file
 * paths, an optional user-facing warning when we had to fall back from
 * a pinned core version, and a classification of whether the session
 * warrants showing the core-upgrade decision UI (#672).
 */
data class PrepareGameResult(
    val gamePath: String,
    val corePath: String,
    /**
     * Non-null when the session had a pinned core sha256 but that
     * historical binary was no longer available (pruned) and we
     * silently fell back to the latest core. UI surfaces this as a
     * non-blocking warning. Null when no pin, or the pin was satisfied.
     */
    val coreVersionWarning: String? = null,
    /**
     * Why (if at all) the VM should consider showing the core-upgrade
     * decision UI before starting emulation. See [DecisionKind].
     */
    val decisionKind: DecisionKind = DecisionKind.None,
    /**
     * libretro identifier of the core the VM is about to launch —
     * the post-platform-substitution name, e.g. "mednafen_psx_hw" on
     * Android. Empty string when the use case bailed early via the
     * offline-cache fallback path. Used by the VM to build the
     * `CoreDecision.UpgradeAvailable` payload for Sheet A.
     */
    val coreName: String = "",
    /**
     * Human-readable display name for [coreName] — falls back to
     * [coreName] when the server hasn't populated a pretty label.
     */
    val coreDisplayName: String = "",
    /**
     * Server's current sha256 for [coreName] at the moment this
     * result was produced. Populated when the use case fetches the
     * manifest during decision detection; empty otherwise. The VM
     * reads this directly to build Sheet A's "Saved with version"
     * meta line without a second network round-trip.
     */
    val serverCoreSha: String = "",
)

/**
 * Some HW-accelerated cores don't work on macOS due to Metal-backed GL
 * texture incompatibilities. Map them to their software equivalents.
 */
private val MACOS_CORE_SUBSTITUTIONS = mapOf(
    "beetle_psx_hw" to "mednafen_psx",
)

/**
 * The libretro buildbot hosts Android N64 cores under variant-specific names
 * (e.g. mupen64plus_next_gles3) rather than the base name. Map accordingly.
 */
private val ANDROID_CORE_SUBSTITUTIONS = mapOf(
    "mupen64plus_next" to "mupen64plus_next_gles3",
    "beetle_psx_hw" to "mednafen_psx_hw",
    "beetle_psx" to "mednafen_psx",
    "beetle_pce" to "mednafen_pce_fast",
    "beetle_saturn" to "mednafen_saturn",
    "beetle_vb" to "mednafen_vb",
    "beetle_ngp" to "mednafen_ngp",
    "beetle_wswan" to "mednafen_wswan",
    "beetle_pcfx" to "mednafen_pcfx",
)

class PrepareGameUseCase(
    private val downloadRepository: DownloadRepository,
    private val coreRepository: CoreRepository,
) {
    /** Warning copy shown to the user when the session's pinned core is no longer on the server. */
    private val prunedWarning =
        "Original core version no longer available. The latest core may not load this save correctly."

    /**
     * Ensures both the game ROM and the required libretro core are available locally.
     *
     * When [pinnedCoreSha256] is non-null the use case first attempts a
     * hash-versioned core download (from the server-side history snapshot).
     * If the historical binary has been pruned, we fall back to the
     * unversioned download path and surface [prunedWarning] via the
     * returned [PrepareGameResult.coreVersionWarning]. Save-state failures
     * downstream still go through the existing error path.
     *
     * Returns a [PrepareGameResult] carrying the resolved paths and
     * (optionally) the fallback warning.
     */
    suspend operator fun invoke(
        gameId: String,
        pinnedCoreSha256: String? = null,
        autoUpdateCoresEnabled: Boolean = true,
        userLockedCoreVersion: Boolean = false,
        sessionHasSaves: Boolean = false,
        skipCoreDecisionPrompt: Boolean = false,
        rehearsalCrashPending: Boolean = false,
    ): Result<PrepareGameResult> {
        println("[PrepareGame] invoke(gameId=$gameId)")
        val gamePath = downloadRepository.getLocalGamePath(gameId)
            ?: return Result.failure(IllegalStateException("Game not downloaded"))
        println("[PrepareGame] gamePath=$gamePath; calling getRecommendedCore")

        val core = coreRepository.getRecommendedCore(gameId).getOrElse { networkError ->
            // Fallback: if server is unreachable, try locally cached cores
            println("[PrepareGame] getRecommendedCore failed: ${networkError.message}, trying local fallback")
            val knownCores = listOf("ppsspp", "desmume", "mednafen_psx_hw", "mednafen_psx", "beetle_psx_hw", "mupen64plus_next", "snes9x", "nestopia", "mgba", "gambatte", "genesis_plus_gx")
            for (coreName in knownCores) {
                val localPath = coreRepository.getLocalCorePath(coreName)
                if (localPath != null) {
                    println("[PrepareGame] Using local fallback core: $localPath")
                    return Result.success(
                        PrepareGameResult(
                            gamePath = gamePath,
                            corePath = localPath,
                            coreName = coreName,
                            coreDisplayName = coreName,
                        ),
                    )
                }
            }
            return Result.failure(networkError)
        }

        val coreName = platformCoreSubstitution(core.name)
        // When the core name was substituted, the server's download URL is for the
        // original name. Clear it so CoreRepository falls back to the buildbot URL
        // constructed from the substituted name.
        val downloadUrl = if (coreName != core.name) null else core.downloadUrl?.takeIf { it.isNotBlank() }
        val platformSubstituted = coreName != core.name
        println("[PrepareGame] After getRecommendedCore: coreName=$coreName, platformSubstituted=$platformSubstituted, downloadUrl=$downloadUrl")

        // #672 PR — launch-time crash recovery. If the previous run on
        // this session entered rehearsal and the player process died
        // before reaching a clean Sheet C/D resolution, route the user
        // to Sheet D for that session BEFORE doing any other detection
        // — the user explicitly needs to acknowledge the crash before
        // we either retry the new core or fall back to the pinned one.
        // skipCoreDecisionPrompt is honoured so the resolution
        // re-launches don't loop back into Sheet D.
        if (rehearsalCrashPending && !skipCoreDecisionPrompt) {
            return Result.success(
                PrepareGameResult(
                    gamePath = gamePath,
                    // corePath stays empty — the VM intercepts before
                    // loadCore is called, and Sheet D's resolution
                    // re-fires StartGame which re-runs this use case
                    // with the appropriate skipCoreDecisionPrompt /
                    // skipAutoLoad flags.
                    corePath = "",
                    decisionKind = DecisionKind.RehearsalCrashed,
                    coreName = coreName,
                    coreDisplayName = core.displayName.ifEmpty { coreName },
                ),
            )
        }

        // #672 core-upgrade decision detection. Runs BEFORE the
        // pinned-core download path so we can give the VM a chance to
        // block `loadCore` and show Sheet A when the session's pin no
        // longer matches the server's current core binary.
        //
        // The decision only applies when: the session has a pin, has
        // at least one save to risk, is not already locked to its pin,
        // and the core name was not platform-substituted (substitution
        // mismatches are legitimate — not an upgrade). When we decide a
        // prompt is needed we still return a usable PrepareGameResult
        // so the VM can use its paths after resolving the user's
        // choice without re-invoking the use case.
        if (pinnedCoreSha256 != null
            && sessionHasSaves
            && !userLockedCoreVersion
            && !platformSubstituted
            && !skipCoreDecisionPrompt
        ) {
            val serverSha = coreRepository.getServerCoreSha(coreName)
            if (serverSha != null
                && !serverSha.equals(pinnedCoreSha256, ignoreCase = true)
            ) {
                if (!autoUpdateCoresEnabled) {
                    // Honour user opt-out: tell the VM to show Sheet A.
                    // We return paths here only as a fallback for the
                    // "user picked Lock" path — the VM may choose to
                    // re-download the pinned binary itself after
                    // collecting the user's choice.
                    val pinnedPath = coreRepository.downloadCoreByHash(
                        coreName,
                        pinnedCoreSha256,
                    ).getOrNull() ?: coreRepository.getLocalCorePath(coreName) ?: ""
                    return Result.success(
                        PrepareGameResult(
                            gamePath = gamePath,
                            corePath = pinnedPath,
                            decisionKind = DecisionKind.UpgradeAvailable,
                            coreName = coreName,
                            coreDisplayName = core.displayName.ifEmpty { coreName },
                            serverCoreSha = serverSha,
                        ),
                    )
                }
                // autoUpdateCoresEnabled path: fall through to the
                // existing pinned-download / latest-download logic.
                // The server sha differs, so we'll silently upgrade
                // to latest below.
            }
        }

        // Pinned-core path — #555 Phase 3 session-to-core binding.
        // We attempt the versioned download only when the session has a
        // pin; otherwise we use the regular local-cache-or-download flow.
        // Platform core substitutions (macOS Metal, Android variants)
        // intentionally skip the hash path because the pinned sha refers
        // to the ORIGINAL core name's binary, not the substitute's.
        if (pinnedCoreSha256 != null && coreName == core.name) {
            val versioned = coreRepository.downloadCoreByHash(coreName, pinnedCoreSha256)
            val pinnedPath = versioned.getOrNull()
            if (pinnedPath != null) {
                return Result.success(
                    PrepareGameResult(
                        gamePath = gamePath,
                        corePath = pinnedPath,
                        coreName = coreName,
                        coreDisplayName = core.displayName.ifEmpty { coreName },
                    ),
                )
            }
            val error = versioned.exceptionOrNull()
            if (error is CorePrunedException) {
                println("[PrepareGame] Pinned core $pinnedCoreSha256 pruned — falling back to latest")
                val fallbackPath = coreRepository.getLocalCorePath(coreName)
                    ?: coreRepository.downloadCore(coreName, downloadUrl).getOrElse {
                        return Result.failure(it)
                    }
                // When the user explicitly locked this session to the
                // pinned sha (#672 UserLockedCoreVersion), pruning
                // warrants Sheet B rather than the silent toast —
                // their explicit choice just became impossible and
                // they need the chance to decide next steps. For
                // sessions that weren't user-locked we keep the old
                // silent-fallback-with-warning behaviour.
                val kind = if (userLockedCoreVersion && sessionHasSaves && !skipCoreDecisionPrompt) {
                    DecisionKind.PinPruned
                } else {
                    DecisionKind.None
                }
                return Result.success(
                    PrepareGameResult(
                        gamePath = gamePath,
                        corePath = fallbackPath,
                        coreVersionWarning = if (kind == DecisionKind.None) prunedWarning else null,
                        decisionKind = kind,
                        coreName = coreName,
                        coreDisplayName = core.displayName.ifEmpty { coreName },
                    ),
                )
            }
            // Any other failure: treat as a transient issue — fall back
            // to unversioned so gameplay still works. No warning surface
            // in this case: the latest core is also the pinned version
            // on every-other-update, and the user doesn't need to know
            // about a transient network glitch.
            println("[PrepareGame] Versioned core download failed (${error?.message}) — falling back to latest")
        }

        // Unpinned path: if a local copy exists but the server's current
        // sha256 differs (core was updated upstream since we last
        // downloaded), silently refresh it so the user is on the latest
        // build. `isCachedCoreCurrent` returns `null` for the
        // "can't decide" case (no local file, server hasn't fingerprinted,
        // network hiccup, hash failed) — we intentionally fall through to
        // the existing path-existence check in that case so transient
        // server issues don't block an otherwise-usable cached core.
        //
        // The staleness check is gated on the user's
        // `autoUpdateCoresEnabled` preference: when false, we keep the
        // local cache regardless of what the server says. Power users
        // who prefer to own when cores upgrade (e.g. to avoid a
        // save-state compatibility break in the middle of a long
        // playthrough) flip this off in Settings.
        //
        // See #555 Phase 2.
        val cached = coreRepository.getLocalCorePath(coreName)
        println("[PrepareGame] cached corePath for $coreName = $cached")
        val shouldCheckStaleness = autoUpdateCoresEnabled && cached != null
        val corePath = if (shouldCheckStaleness && coreRepository.isCachedCoreCurrent(coreName) == false) {
            println("[PrepareGame] Cached $coreName is stale vs server — redownloading")
            coreRepository.downloadCore(coreName, downloadUrl).getOrElse {
                // Re-download failed: fall back to the stale cache rather
                // than stranding the user. The game still runs; saves
                // made here might not load on a fresh install, but that's
                // strictly better than refusing to launch.
                println("[PrepareGame] Redownload failed, keeping stale cache: ${it.message}")
                cached
            }
        } else {
            if (cached != null) {
                println("[PrepareGame] Using cached core (no staleness check or up-to-date)")
                cached
            } else {
                println("[PrepareGame] No cached core — downloading $coreName")
                coreRepository.downloadCore(coreName, downloadUrl).getOrElse {
                    println("[PrepareGame] downloadCore FAILED: ${it::class.simpleName}: ${it.message}")
                    return Result.failure(it)
                }.also { println("[PrepareGame] downloadCore succeeded → $it") }
            }
        }

        println("[PrepareGame] Returning PrepareGameResult(corePath=$corePath, coreName=$coreName)")
        return Result.success(
            PrepareGameResult(
                gamePath = gamePath,
                corePath = corePath,
                coreName = coreName,
                coreDisplayName = core.displayName.ifEmpty { coreName },
            ),
        )
    }

    /**
     * Substitutes HW-accelerated cores with software equivalents on platforms
     * where the HW cores don't work (e.g. macOS Metal-backed GL).
     */
    private fun platformCoreSubstitution(coreName: String): String {
        val substitutions = when (currentPlatform()) {
            "macos" -> MACOS_CORE_SUBSTITUTIONS
            "android" -> ANDROID_CORE_SUBSTITUTIONS
            else -> return coreName
        }
        val substitute = substitutions[coreName] ?: return coreName
        println("[PrepareGame] ${currentPlatform()}: substituting $coreName -> $substitute")
        return substitute
    }
}

