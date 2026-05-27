package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val symbol: String, val displayName: String, val rank: Int) {
    SPADES("♠", "Spades", 0),
    CLUBS("♣", "Clubs", 1),
    DIAMONDS("♦", "Diamonds", 2),
    HEARTS("♥", "Hearts", 3);
}
