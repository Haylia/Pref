package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val seat: SeatId,
    val name: String,
)
