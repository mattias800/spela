package com.spela.player.domain.model

data class Cheat(
    val id: String,
    val gameId: String,
    val index: Int,
    val description: String,
    val code: String,
    val enabled: Boolean,
)
