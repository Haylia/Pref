package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

@Serializable
data class GameConfig(
    val variant: Variant,
    val players: List<Player>,
    /** Bullet target — game ends when all active seats reach this. */
    val bulletTarget: Int = 10,
) {
    val seats: List<SeatId> get() = players.map { it.seat }
    val playerCount: Int get() = players.size
    fun nameOf(seat: SeatId): String = players.first { it.seat == seat }.name
}

@Serializable
enum class GameStatus { ACTIVE, COMPLETED }

@Serializable
data class GameState(
    val config: GameConfig,
    val sheet: ScoreSheet,
    val hands: List<Hand> = emptyList(),
    val status: GameStatus = GameStatus.ACTIVE,
    /** Next hand's dealer seat. Rotates after each hand. */
    val nextDealerSeat: SeatId,
    val createdAtMillis: Long,
) {
    val nextHandNumber: Int get() = hands.size + 1

    /** Returns the active seats for a given hand. In 4-player the dealer sits out. */
    fun activeSeats(dealer: SeatId): List<SeatId> {
        return if (config.playerCount == 4) {
            config.seats.filter { it != dealer }
        } else {
            config.seats
        }
    }

    /** Rotates dealer by one seat. */
    fun rotateDealer(): SeatId {
        val idx = config.seats.indexOf(nextDealerSeat)
        return config.seats[(idx + 1) % config.seats.size]
    }
}
