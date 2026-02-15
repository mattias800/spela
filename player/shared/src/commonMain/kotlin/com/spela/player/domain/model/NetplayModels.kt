package com.spela.player.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NetplaySession(
    val id: String,
    val gameId: String,
    val gameTitle: String = "",
    val gameCoverUrl: String? = null,
    val gameConsoleName: String = "",
    val coverAspectRatio: Float = 0.75f,
    val hostUserId: String,
    val hostUsername: String = "",
    val hostAvatarUrl: String? = null,
    val clientUserId: String? = null,
    val clientUsername: String? = null,
    val clientAvatarUrl: String? = null,
    val status: NetplaySessionStatus = NetplaySessionStatus.WAITING,
    val inputDelay: Int = 3,
    val inviteCode: String = "",
    val endReason: String? = null,
    val createdAt: String = "",
    val startedAt: String? = null,
    val endedAt: String? = null,
)

enum class NetplaySessionStatus { WAITING, IN_PROGRESS, ENDED }

/** Consoles that support deterministic netplay in Phase 1. */
val NETPLAY_SUPPORTED_CONSOLES = setOf("nes", "snes", "gb", "gbc", "gba", "genesis", "megadrive")
