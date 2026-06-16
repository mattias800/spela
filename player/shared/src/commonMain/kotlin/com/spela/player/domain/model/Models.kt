package com.spela.player.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class PaginatedResult<T>(
    val data: List<T>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)

@Serializable
data class ServerConnection(
    val id: String,
    val name: String,
    val url: String,
    val isActive: Boolean = false,
)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String = "",
    val role: String,
    val avatarUrl: String? = null,
    // Admins/owners can always import federation games; this flag grants the
    // capability to a plain user (see #1391 / #1385).
    val canImportGames: Boolean = false,
)

@Serializable
/**
 * Save-state size tier the server seeded for a console — drives
 * retention, slot count, and opt-out UX. Loaded from
 * [ConsoleResponse.saveStatePolicy] which is one of "small" |
 * "medium" | "large". Unknown values fall back to [Small] so a
 * future server tier doesn't crash older clients. See #804 phase 3.
 */
enum class SaveStatePolicyTier(val apiId: String) {
    Small("small"),
    Medium("medium"),
    Large("large");

    companion object {
        fun fromApiId(apiId: String?): SaveStatePolicyTier =
            entries.find { it.apiId == apiId } ?: Small
    }
}

data class Console(
    val id: String,
    val name: String,
    val abbreviation: String,
    val gameCount: Int,
    val code: String = "",
    val colorTheme: String = "#6366f1",
    val coverAspectRatio: Double = 0.75,
    val defaultCore: String = "",
    val iconUrl: String = "",
    val logoUrl: String = "",
    /**
     * Intrinsic width/height of [logoUrl] as reported by the server
     * (computed once from the SVG's viewBox at seed time). The
     * console-detail hero uses this on first render so the area-sized
     * logo container starts at the right dimensions and doesn't
     * re-layout once the image decodes (#1166). Null when the server
     * couldn't determine it — clients should fall back to the legacy
     * fluid sizing.
     */
    val logoAspectRatio: Float? = null,
    val saveStateSupport: Boolean = true,
    val saveStatePolicy: SaveStatePolicyTier = SaveStatePolicyTier.Small,
    val browserPlayable: Boolean = false,
    val playable: Boolean = true,
    val generation: Int = 0,
    val makerName: String? = null,
    val makerCode: String? = null,
    val mediaTypeName: String? = null,
    val releaseYear: Int? = null,
    val unitsSold: Long? = null,
    val summary: String? = null,
)

@Serializable
data class GameDisc(val discNumber: Int, val fileName: String, val fileSize: Long)

@Serializable
data class Game(
    val id: String,
    val title: String,
    val consoleId: String,
    val consoleName: String = "",
    /** Save-state size tier of the parent console — drives the
     *  first-launch prompt and slot/quota UX. Stored on the Game
     *  rather than fetched separately so the player doesn't need a
     *  second round-trip at game-launch time. See #804 phase 4b. */
    val consoleSaveStatePolicy: SaveStatePolicyTier = SaveStatePolicyTier.Small,
    val coverAspectRatio: Float = 0.75f,
    val coverUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val fileSize: Long = 0,
    val fileName: String = "",
    val coreOverride: String? = null,
    val scrapeAttempts: Int = 0,
    val players: Int = 0,
    val igdbCriticsRating: Double = 0.0,
    val communityRating: Double = 0.0,
    val communityRatingCount: Long = 0,
    val userRating: Int? = null,
    val isFavorite: Boolean = false,
    val isInPlayLater: Boolean = false,
    val lastPlayedAt: String? = null,
    val totalPlayTime: Long = 0,
    val discCount: Int = 0,
    val discs: List<GameDisc> = emptyList(),
    val achievementsWarning: String? = null,
    val verificationStatus: String? = null,
    val verificationTag: String? = null,
    val region: String? = null,
    val playable: Boolean = true,
    val heroUrl: String? = null,
    val logoUrl: String? = null,
    val revision: String? = null,
    val tags: String? = null,
    val isPreRelease: Boolean = false,
    val variantCount: Int = 0,
    val groupKey: String? = null,
    val timeToBeatHastily: Int = 0,
    val timeToBeatNormally: Int = 0,
    val timeToBeatCompletely: Int = 0,
    val partyInfo: String = "",
)

@Serializable
data class GameVariant(
    val id: String,
    val title: String,
    val fileName: String = "",
    val region: String? = null,
    val revision: String? = null,
    val tags: String? = null,
    val isPreRelease: Boolean = false,
    val fileSize: Long = 0,
    val verificationStatus: String? = null,
)

/** Minimal reference to a parent game for standalone ROM hacks. */
@Serializable
data class ParentGame(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
)

/** Minimal reference to a standalone ROM hack based on a game. */
@Serializable
data class RomHackGame(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
)

@Serializable
data class GameDetail(
    val game: Game,
    val screenshots: List<String> = emptyList(),
    val variants: List<GameVariant> = emptyList(),
    val parentGame: ParentGame? = null,
    val romHacks: List<RomHackGame> = emptyList(),
)

@Serializable
data class SaveState(
    val id: String,
    val name: String,
    val createdAt: Instant? = null,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
    val coreName: String? = null,
    val notes: String? = null,
    val screenshotUrl: String? = null,
    val slot: Int? = null,
    val isSynced: Boolean = true,
)

@Serializable
data class LibretroCore(
    val id: Long,
    val name: String,
    val displayName: String = "",
    val version: String? = null,
    val platforms: String = "",
    val customDownloadUrl: String? = null,
)

enum class ShaderPreset(val apiId: String, val displayName: String, val description: String) {
    NONE("none", "None (Pixel Perfect)", "Raw pixels, nearest-neighbor scaling"),
    BILINEAR("bilinear", "Smooth (Bilinear)", "Softens hard pixel edges"),
    SHARP_BILINEAR("sharp-bilinear", "Sharp Bilinear", "Smooth edges, preserves pixel grid"),
    CRT_SIMPLE("crt-simple", "CRT Classic", "Scanlines, curvature, and phosphor glow"),
    LCD_GRID("lcd-grid", "LCD Grid", "Simulates handheld LCD pixel grid"),
    SCANLINES("scanlines", "Scanlines Only", "Horizontal scanline darkening");

    companion object {
        fun fromApiId(id: String): ShaderPreset =
            entries.find { it.apiId == id } ?: NONE
    }
}

data class ConsoleKeyMappingPref(
    val selectedMapping: String,
    val customMapping: Map<String, String> = emptyMap(),
)

/**
 * User's per-console save-state opt-out choice. The map only holds
 * consoles where the user has made a deliberate choice in
 * [UserPreferences.consoleSaveStatePolicies]; absence means "use the
 * tier default" — see [effectiveSaveStateChoice]. See #804 phase 4.
 */
enum class SaveStateChoice(val apiId: String) {
    Enabled("enabled"),
    Disabled("disabled"),
    AskOnce("ask-once");

    companion object {
        fun fromApiId(apiId: String?): SaveStateChoice? =
            entries.find { it.apiId == apiId }
    }
}

data class UserPreferences(
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val autoUpdateCoresEnabled: Boolean = true,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,
    val selectedTheme: String = "default-dark",
    val consoleShaders: Map<String, ShaderPreset> = emptyMap(),
    val consoleSaveStatePolicies: Map<String, SaveStateChoice> = emptyMap(),
    /**
     * Per-game save-state opt-out overrides keyed by game ID string.
     * Layered on top of [consoleSaveStatePolicies] — a per-game choice
     * wins over the console-level one. Only contains games where the
     * user has made a deliberate per-game choice. See #804 phase 4b
     * spec point (c).
     */
    val gameSaveStatePolicies: Map<String, SaveStateChoice> = emptyMap(),
    val selectedKeyMapping: String = "default",
    val consoleKeyMappings: Map<String, ConsoleKeyMappingPref> = emptyMap(),
    val defaultSecondScreenPage: String = "art",
)

/**
 * Resolves the effective save-state choice for [consoleAbbr] given
 * the console's tier, the user's per-console overrides, and an
 * optional per-game override.
 *
 * Precedence (high → low):
 *
 *   1. per-game override in [gameOverride] (when non-null)
 *   2. explicit per-console override in [overrides] (case-insensitive
 *      abbreviation match)
 *   3. tier default — small/medium → Enabled, large → AskOnce
 *
 * The resolver intentionally returns [SaveStateChoice.AskOnce] for
 * large-tier consoles with no override so the player can fire the
 * first-launch prompt; the in-game overlay treats AskOnce the same
 * as Enabled until that prompt resolves to a deliberate choice. See
 * #804 phase 4.
 */
fun effectiveSaveStateChoice(
    consoleAbbr: String,
    tier: SaveStatePolicyTier,
    overrides: Map<String, SaveStateChoice>,
    gameOverride: SaveStateChoice? = null,
): SaveStateChoice {
    gameOverride?.let { return it }
    val key = consoleAbbr.lowercase()
    overrides[key]?.let { return it }
    return when (tier) {
        SaveStatePolicyTier.Small,
        SaveStatePolicyTier.Medium -> SaveStateChoice.Enabled
        SaveStatePolicyTier.Large -> SaveStateChoice.AskOnce
    }
}

data class DownloadedGame(
    val gameId: String,
    val title: String,
    val consoleName: String,
    val coverUrl: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
)

enum class DownloadState {
    IDLE,
    QUEUED,
    DOWNLOADING,

    /**
     * A resumable partial exists on disk but no transfer is active — the
     * download stopped and can continue from its current byte offset. Set
     * either by a user-initiated pause/cancel-keeping-partial or by a
     * *resumable* failure (network drop, server cut the connection). The UI
     * offers **Resume**. See [DownloadProgress.failureReason] for the cause
     * (null = user-initiated). (#1296)
     */
    PAUSED,
    COMPLETED,

    /**
     * The download stopped and cannot be safely resumed — the partial (if any)
     * was discarded. The only recovery is a clean restart ("Start over"). See
     * [DownloadProgress.failureReason] for which terminal condition applied.
     */
    FAILED,
}

/**
 * Why a download stopped, used to drive recovery UI copy and decide whether a
 * partial is resumable. Resumable causes leave the partial on disk and surface
 * as [DownloadState.PAUSED]; terminal causes discard the partial and surface as
 * [DownloadState.FAILED]. (#1296)
 */
enum class DownloadFailureReason(val resumable: Boolean) {
    /** Transient connectivity loss (wifi drop, timeout, reset). Resume. */
    NETWORK(resumable = true),

    /** Server cut the connection mid-stream (e.g. write timeout, 5xx). Resume. */
    SERVER(resumable = true),

    // Note: a file that changed on the server is NOT a failure reason — the
    // resume request's stale If-Range makes the server send a full 200, which
    // the client writes from scratch and completes successfully. It never
    // surfaces as a FAILED state, so there is no FILE_CHANGED value here.

    /** The partial is corrupt or its offset no longer matches the server
     *  (e.g. a 416). Restart cleanly rather than splice a bad file. */
    CORRUPT(resumable = false),

    /** Not enough free disk space to finish. Restarting won't help until the
     *  user frees space, so this is terminal with specific copy. */
    DISK_FULL(resumable = false),
}

data class DownloadProgress(
    val gameId: String,
    val gameTitle: String = "",
    val state: DownloadState,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val currentDisc: Int = 0,
    val totalDiscs: Int = 0,
    /** Rolling-window average bytes per second over the most recent
     *  ~2 s of progress samples. 0 when stalled or not yet computable. */
    val bytesPerSecond: Long = 0,
    /**
     * Why the download stopped, for PAUSED/FAILED states. null when the
     * download is healthy or was paused by the user (no error). For PAUSED
     * this is a resumable cause (or null for a user pause); for FAILED it's a
     * terminal cause. (#1296)
     */
    val failureReason: DownloadFailureReason? = null,
) {
    val progress: Float
        get() = when {
            totalBytes < 0 -> -1f
            totalBytes > 0 -> bytesDownloaded.toFloat() / totalBytes
            else -> 0f
        }

    val isIndeterminate: Boolean
        get() = totalBytes < 0

    /** True when a partial exists that can be resumed (PAUSED state). */
    val isResumable: Boolean
        get() = state == DownloadState.PAUSED
}

// RetroAchievements

data class RAStatus(
    val linked: Boolean = false,
    val username: String = "",
    val hardcoreEnabled: Boolean = false,
)

data class RACredentials(
    val username: String,
    val token: String,
)

// Shared Saves

data class SharedSaveState(
    val id: String,
    val userId: String,
    val username: String,
    val userAvatarUrl: String?,
    val gameId: String,
    val name: String,
    val description: String,
    val fileSize: Long,
    val downloadCount: Int,
    val createdAt: String,
)

// Ratings

data class GameRating(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val gameId: String,
    val rating: Int,
    val review: String,
    val createdAt: String,
)

data class RatingSummary(
    val averageRating: Double,
    val totalRatings: Long,
    val distribution: Map<Int, Int>,
)

// Social

data class OnlineUser(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val currentGame: OnlineUserGame?,
)

data class OnlineUserGame(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val consoleName: String,
)

data class ActivityEvent(
    val id: String,
    val userId: String,
    val username: String,
    val userAvatarUrl: String?,
    val eventType: String,
    val gameId: String?,
    val gameTitle: String?,
    val gameCoverUrl: String?,
    val gameConsoleName: String?,
    val createdAt: String,
)

enum class AchievementEventType(val nativeValue: Int) {
    ACHIEVEMENT_TRIGGERED(1),
    GAME_COMPLETED(2),
    CHALLENGE_INDICATOR_SHOW(3),
    CHALLENGE_INDICATOR_HIDE(4),
    LEADERBOARD_STARTED(5),
    LEADERBOARD_FAILED(6),
    LEADERBOARD_SUBMITTED(7),
    SERVER_ERROR(8);

    companion object {
        fun fromNativeValue(value: Int): AchievementEventType? =
            entries.find { it.nativeValue == value }
    }
}

data class AchievementEvent(
    val type: AchievementEventType,
    val achievementId: Long = 0L,
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val rarityPercent: Double = 0.0,
)

// Shared Session

data class SharedSession(
    val id: String,
    val name: String,
    val description: String = "",
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val ownerId: String,
    val ownerUsername: String = "",
    val status: String = "active",
    val memberCount: Int = 0,
    val activeUserId: String? = null,
    val lastActivityAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class SharedSessionDetail(
    val id: String,
    val name: String,
    val description: String = "",
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val ownerId: String,
    val ownerUsername: String = "",
    val status: String = "active",
    val memberCount: Int = 0,
    val activeUserId: String? = null,
    val members: List<SharedSessionMember> = emptyList(),
    val lastActivityAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    /**
     * Backing `GameSession` id that this shared session's saves are
     * attached to server-side. `null` when no one has played yet — in
     * that case the Clone-to-my-library action is disabled because
     * there's nothing to clone from. Populated from the server's
     * `SharedSessionDetailResponse.sessionId`.
     */
    val backingGameSessionId: String? = null,
)

data class SharedSessionMember(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val role: String = "member",
    val joinedAt: String = "",
    val lastPlayedAt: String? = null,
    val isOnline: Boolean = false,
)

data class SharedSessionInvitation(
    val id: String,
    val sharedSessionId: String,
    val sharedSessionName: String = "",
    val gameId: String = "",
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val inviterId: String = "",
    val inviterUsername: String = "",
    val inviterAvatarUrl: String? = null,
    val inviteeId: String = "",
    val inviteeUsername: String = "",
    val status: String = "",
    val createdAt: String = "",
)

data class SharedSessionSave(
    val id: Long,
    val sharedSessionId: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val name: String,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

// Collections

data class GameCollection(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false,
    val coverUrl: String? = null,
    val gameCount: Int = 0,
)

data class GameCollectionDetail(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false,
    val coverUrl: String? = null,
    val gameCount: Int = 0,
    val games: List<Game> = emptyList(),
)

// Challenges

data class Challenge(
    val id: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorAvatarUrl: String?,
    val gameId: String,
    val gameTitle: String,
    val gameCoverUrl: String?,
    val gameConsoleName: String,
    val name: String,
    val description: String,
    val type: ChallengeType,
    val difficulty: ChallengeDifficulty,
    val status: String,
    val screenshotUrl: String?,
    val coreName: String,
    val saveFileSize: Long,
    val attemptCount: Int,
    val completionCount: Int,
    val bestTimeMs: Long?,
    val expiresAt: String?,
    val createdAt: String,
)

data class ChallengeAttempt(
    val id: String,
    val challengeId: String,
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val durationMs: Long,
    val isBest: Boolean,
)

data class ChallengeLeaderboardEntry(
    val rank: Int,
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val durationMs: Long,
    val completedAt: String,
    val isCurrentUser: Boolean,
)

enum class ChallengeType(val apiId: String, val displayName: String) {
    COMPLETION("completion", "Completion"),
    SPEEDRUN("speedrun", "Speedrun"),
    SURVIVAL("survival", "Survival");

    companion object {
        /**
         * Throws on an unknown server value rather than silently coercing to a
         * default. See RuntimeNarrowing.kt for the rationale — a lying fallback
         * breaks exhaustive `when` statements downstream.
         */
        fun fromApiId(id: String): ChallengeType =
            entries.find { it.apiId == id }
                ?: error("Unexpected challenge type from server: '$id'")
    }
}

enum class ChallengeDifficulty(val apiId: String, val displayName: String) {
    EASY("easy", "Easy"),
    MEDIUM("medium", "Medium"),
    HARD("hard", "Hard");

    companion object {
        /** See [ChallengeType.fromApiId] rationale. */
        fun fromApiId(id: String): ChallengeDifficulty =
            entries.find { it.apiId == id }
                ?: error("Unexpected challenge difficulty from server: '$id'")
    }
}

// Game Stats

data class GameStats(
    val totalPlayers: Int,
    val totalPlayTime: Long,
    val averagePlayTime: Long,
    val topPlayers: List<TopPlayer>,
)

data class TopPlayer(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val playTime: Long,
)

data class GameAchievement(
    val id: Long,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String?,
    val type: String?,
    val displayOrder: Int?,
    val rarityPercent: Double = 0.0,
)

data class AchievementProgress(
    val achievementId: Long,
    val unlockedAt: String?,
    val isHardcore: Boolean,
    val playTimeAtUnlock: Long?,
)

data class AchievementTimelineEntry(
    val achievementRaId: Long,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String?,
    val unlockedAt: String,
    val isHardcore: Boolean,
    val playTimeAtUnlock: Long?,
)

data class AchievementTimelineData(
    val raGameId: Long?,
    val gameTitle: String,
    val totalPlayTime: Long,
    val timeline: List<AchievementTimelineEntry>,
    val totalAchievements: Int,
    val unlockedCount: Int,
    val totalPoints: Int,
    val earnedPoints: Int,
)

data class AchievementPlayerRanking(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val unlockedCount: Int,
    val earnedPoints: Int,
    val firstUnlockedAt: String?,
    val lastUnlockedAt: String?,
)

data class ShowcaseAchievement(
    val achievementRaId: Long,
    val raGameId: Long,
    val showcaseOrder: Int = 0,
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val badgeUrl: String? = null,
    val rarityPercent: Double = 0.0,
    val gameTitle: String = "",
)

data class UnlockedAchievement(
    val achievementRaId: Long,
    val raGameId: Long,
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val badgeUrl: String? = null,
    val rarityPercent: Double = 0.0,
    val gameTitle: String = "",
    val consoleName: String = "",
)

data class UserStats(
    val totalPlayTime: Long,
    val gamesPlayed: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val mostPlayedGame: Game?,
    val mostPlayedGameTime: Long,
    val lastPlayedAt: String?,
)

data class RecentAchievement(
    val achievementRaId: Long,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String?,
    val unlockedAt: String,
    val isHardcore: Boolean,
    val playTimeAtUnlock: Long?,
    val gameId: String,
    val gameTitle: String,
    val consoleName: String,
    val coverUrl: String?,
)

// Stats

data class MostPlayedGame(
    val game: Game,
    val totalPlayers: Int,
    val totalPlayTime: Long,
)

data class ActivePlayer(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val totalPlayTime: Long,
    val gamesPlayed: Int,
    val lastPlayed: String? = null,
)

// Public Profile

data class PublicProfile(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val memberSince: String,
    val isOnline: Boolean,
    val currentGame: OnlineUserGame?,
    val totalPlayTime: Long,
    val gamesPlayed: Long,
    val favoriteGames: List<PublicProfileGame>,
    val recentGames: List<PublicProfileGame>,
    val topGames: List<PublicProfileGame>,
)

data class HeatmapEntry(
    val date: String,
    val playTime: Long,
)

data class PublicProfileGame(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val consoleName: String,
    val playTime: Long,
)

// Top Rated

data class TopRatedGame(
    val rank: Int,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double = 0.0,
    val localGameId: String? = null,
    val consoleName: String = "",
)

// Top Lists

enum class TopListTab { TOP_RATED, LONGEST }

data class TopListGame(
    val rank: Int,
    val gameId: String,
    val name: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
    val consoleId: String = "",
    val rating: Double = 0.0,
)

data class LongestGame(
    val rank: Int,
    val gameId: String,
    val name: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
    val consoleId: String = "",
    val timeToBeatNormally: Int = 0,
    val timeToBeatHastily: Int = 0,
    val timeToBeatCompletely: Int = 0,
)

// Similar Games

data class SimilarGame(
    val igdbGameId: Int = 0,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double = 0.0,
    val localGameId: String? = null,
)

// Developer Games

data class DeveloperGame(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
)

// Game Sessions

@Serializable
data class GameSession(
    val id: String,
    val gameId: String,
    val name: String,
    val lastPlayedAt: String? = null,
    val lastPlayedByUsername: String? = null,
    val totalPlayTime: Long = 0,
    val screenshotUrl: String? = null,
    val coreName: String? = null,
    val cheatsEnabled: Boolean = false,
    val isSharedSession: Boolean = false,
    val sharedSessionId: String? = null,
    val memberCount: Int = 1,
    val memberUsernames: List<String> = emptyList(),
    val memberAvatars: List<String> = emptyList(),
    /**
     * SHA-256 of the libretro core binary that wrote this session's first
     * save state, if any. When set, the player can ask the server for the
     * historical binary by hash (#555 core history retention) so save
     * states keep loading after future core updates. `null` (serialized
     * as empty string by the server) means this session has no pin yet —
     * it will be set on the first save.
     */
    val pinnedCoreSha256: String? = null,
    /**
     * True when the user explicitly locked this session to
     * [pinnedCoreSha256] via the core-upgrade decision UI. The lock
     * always beats the global auto-update preference and short-circuits
     * the pre-play decision sheet on subsequent launches. See #672.
     */
    val userLockedCoreVersion: Boolean = false,
    /**
     * True when the next launch of this session should skip automatic
     * save-state load (set after the user picked "Start fresh on the
     * new version" on Sheet B / D). Cleared on first successful manual
     * save. See #672.
     */
    val autoLoadSuppressed: Boolean = false,
    /**
     * Sentinel set by the player around the rehearsal flow. A `true`
     * value surviving an app relaunch signals that the previous
     * rehearsal ended in a crash so the UI can route the user to
     * Sheet D after session restore. See #672.
     */
    val rehearsalCrashPending: Boolean = false,
)

data class SessionCheatConfig(
    val cheatsEnabled: Boolean,
    val enabledIndices: List<Int>,
)

// User Search

data class UserSearchResult(
    val id: String,
    val username: String,
    val avatarUrl: String?,
)

// BIOS

data class BiosConsoleStatus(
    val consoleId: String,
    val consoleName: String,
    val biosRequired: Boolean,
    val status: String,
    val missingFiles: List<BiosMissingFile>,
)

data class BiosMissingFile(
    val fileName: String,
    val description: String,
    val required: Boolean,
    val subDir: String? = null,
    val bundle: Boolean = false,
)
