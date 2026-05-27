package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * One row of the scoresheet for one player.
 *
 *  - bullet: points earned toward closing the bullet (pulja)
 *  - mountain: penalty points (gora). In Rostov this stays at 0 always.
 *  - whistAgainst: whist points this player has scored *against* a given opponent
 *    (keyed by opponent seat). At settlement these are netted across players.
 */
@Serializable
data class PlayerScore(
    val seat: SeatId,
    val bullet: Int = 0,
    val mountain: Int = 0,
    val whistAgainst: Map<SeatId, Int> = emptyMap(),
) {
    fun addBullet(amount: Int): PlayerScore = copy(bullet = bullet + amount)
    fun addMountain(amount: Int): PlayerScore = copy(mountain = mountain + amount)
    fun addWhistAgainst(opponent: SeatId, amount: Int): PlayerScore =
        copy(whistAgainst = whistAgainst + (opponent to ((whistAgainst[opponent] ?: 0) + amount)))

    fun totalWhist(): Int = whistAgainst.values.sum()
}

@Serializable
data class ScoreSheet(
    val scores: Map<SeatId, PlayerScore>,
) {
    fun update(seat: SeatId, transform: (PlayerScore) -> PlayerScore): ScoreSheet {
        val current = scores[seat] ?: PlayerScore(seat = seat)
        return ScoreSheet(scores + (seat to transform(current)))
    }

    companion object {
        fun empty(seats: List<SeatId>): ScoreSheet =
            ScoreSheet(seats.associateWith { PlayerScore(seat = it) })
    }
}
