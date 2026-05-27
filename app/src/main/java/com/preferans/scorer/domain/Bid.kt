package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * A bid in the auction. Pass is not represented here — the absence of a bid
 * (or RaspasovkaHand) handles all-pass cases.
 *
 * Bid ordering for ranking:
 *  - SuitBid(level=6, Spades) lowest, then 6C, 6D, 6H, 6NT
 *  - Then 7S, 7C, 7D, 7H, 7NT
 *  - Then 8S, 8C, 8D, 8H, 8NT
 *  - Misere ranks between 8NT and 9S
 *  - Then 9S..9NT, 10S..10NT
 */
@Serializable
sealed class Bid : Comparable<Bid> {
    abstract val rankKey: Int

    @Serializable
    data class SuitBid(val level: Int, val suit: Suit) : Bid() {
        init {
            require(level in 6..10) { "Suit bid level must be 6..10, got $level" }
        }
        override val rankKey: Int get() = baseRank(level) + suit.rank
    }

    @Serializable
    data class NoTrumpBid(val level: Int) : Bid() {
        init {
            require(level in 6..10) { "NT bid level must be 6..10, got $level" }
        }
        override val rankKey: Int get() = baseRank(level) + 4
    }

    @Serializable
    data object Misere : Bid() {
        override val rankKey: Int get() = baseRank(8) + 5 // between 8NT and 9S
    }

    override fun compareTo(other: Bid): Int = rankKey - other.rankKey

    /** Trick level for scoring: 6/7/8/9/10. Misère is treated as 10 (10 bullet on success). */
    val level: Int
        get() = when (this) {
            is SuitBid -> level
            is NoTrumpBid -> level
            Misere -> 10
        }

    val displayName: String
        get() = when (this) {
            is SuitBid -> "$level${suit.symbol}"
            is NoTrumpBid -> "${level}NT"
            Misere -> "Misère"
        }

    companion object {
        // 6S=0..6NT=4, then misere stub spans 8s, etc. We give 10 slots per level.
        private fun baseRank(level: Int): Int = (level - 6) * 10
    }
}

/** Point value per trick at a given bid level: 6→2, 7→4, 8→6, 9→8, 10→10. */
fun valuePerTrick(level: Int): Int = when (level) {
    6 -> 2
    7 -> 4
    8 -> 6
    9 -> 8
    10 -> 10
    else -> error("Invalid level $level")
}

/** Tricks the whisting side (combined) needs to take to fulfill whist. */
fun whistThreshold(level: Int): Int = when (level) {
    6 -> 4
    7 -> 2
    8, 9 -> 1
    10 -> 0
    else -> error("Invalid level $level")
}
