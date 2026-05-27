package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * Identity of a seat (0..3). For 3-player games seats 0..2 are used.
 * In 4-player games, all four seats rotate, with the dealer sitting out each hand.
 */
typealias SeatId = Int

/**
 * Records one player's whist stance and outcome on a played hand.
 */
@Serializable
data class WhisterRecord(
    val seat: SeatId,
    /** This opponent's response to the contract: PASS / WHIST / HALF_WHIST. */
    val choice: WhistChoice,
    /**
     * Tricks this opponent took during the hand. 0 for the "neither whists / auto-win"
     * case and for the passer in a half-whist scenario (no play occurred).
     */
    val tricks: Int = 0,
) {
    /** True for a full whist; false for pass and half-whist. */
    val isFullWhist: Boolean get() = choice == WhistChoice.WHIST
}

/**
 * A single recorded hand. Sealed because the input form differs by hand type.
 */
@Serializable
sealed class Hand {
    abstract val handNumber: Int
    abstract val dealerSeat: SeatId

    /** A played contract (suit / NT / misère). */
    @Serializable
    data class Played(
        override val handNumber: Int,
        override val dealerSeat: SeatId,
        val declarerSeat: SeatId,
        val bid: Bid,
        val declarerTricks: Int,
        val opponents: List<WhisterRecord>,
        /** Optional talon honor bonus (4-player only). Whist points credited to the dealer column against the declarer. */
        val talonWhistBonus: Int = 0,
    ) : Hand() {
        init {
            require(declarerTricks in 0..10) { "Declarer tricks must be 0..10" }
            require(opponents.all { it.tricks in 0..10 }) { "Opponent tricks must be 0..10" }
            require(declarerTricks + opponents.sumOf { it.tricks } == 10) {
                "Tricks must sum to 10 (got ${declarerTricks + opponents.sumOf { it.tricks }})"
            }
        }
    }

    /** All players passed — played as raspasovka (avoid-tricks). */
    @Serializable
    data class Raspasovka(
        override val handNumber: Int,
        override val dealerSeat: SeatId,
        /** Tricks per seat. In 4-player only the three non-dealers play and dealer.tricks==0. */
        val tricksBySeat: Map<SeatId, Int>,
    ) : Hand() {
        init {
            val sum = tricksBySeat.values.sum()
            require(sum == 10) { "Raspasovka tricks must sum to 10 (got $sum)" }
        }
    }
}
