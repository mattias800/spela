package com.spela.player.domain.model

import kotlinx.serialization.Serializable

/**
 * A game available on a connected federation server but not (necessarily) in the
 * local library. The catalog is metadata-only — we only know the cross-server
 * key, title, console, a resolved cover, and how many connected servers offer it.
 */
@Serializable
data class RemoteGame(
    val key: String, // cross-server identity, e.g. "igdb:1022"
    val title: String,
    val console: String, // console abbreviation, e.g. "SNES"
    val coverUrl: String?, // null when no cover could be resolved
    val originCount: Int, // how many connected servers offer it
    val local: Boolean, // already present in the local library
)

/** A console abbreviation + how many connected-server games it has (browse overview). */
@Serializable
data class ConnectedConsole(
    val console: String,
    val count: Int,
)

/** Lifecycle of an import job, mirroring the server's state machine. */
enum class ImportStatus {
    PENDING,
    DOWNLOADING,
    INGESTING,
    SCRAPING,
    COMPLETED,
    FAILED,
    UNKNOWN;

    /** Still doing work — not yet in a terminal state. */
    val isActive: Boolean
        get() = this != COMPLETED && this != FAILED && this != UNKNOWN

    companion object {
        fun fromWire(value: String): ImportStatus =
            when (value) {
                "pending" -> PENDING
                "downloading" -> DOWNLOADING
                "ingesting" -> INGESTING
                "scraping" -> SCRAPING
                "completed" -> COMPLETED
                "failed" -> FAILED
                else -> UNKNOWN
            }
    }
}

/**
 * A player active on a connected server right now (cross-mesh presence). Sourced
 * live from the federation presence aggregate. [hops] is 0 for a local player and
 * >= 1 for a player on a connected server; [serverName] is that server's label
 * (blank for local).
 */
@Serializable
data class FriendPresence(
    val username: String,
    val gameKey: String, // cross-server game identity, links to the remote game
    val gameTitle: String,
    val serverName: String,
    val hops: Int,
)

/** An import of a connected-server game into the local library. */
@Serializable
data class ImportJob(
    val id: Long,
    val status: ImportStatus,
    val key: String,
    val title: String,
    val console: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val errorMessage: String?,
    val gameId: Long?, // the resulting local game id once completed
)
