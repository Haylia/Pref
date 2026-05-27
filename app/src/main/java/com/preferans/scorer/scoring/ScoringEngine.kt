package com.preferans.scorer.scoring

import com.preferans.scorer.domain.Bid
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.PlayerScore
import com.preferans.scorer.domain.ScoreSheet
import com.preferans.scorer.domain.SeatId
import com.preferans.scorer.domain.Variant
import com.preferans.scorer.domain.valuePerTrick
import com.preferans.scorer.domain.whistThreshold

/**
 * Pure functions that compute ScoreSheet updates for a hand.
 *
 * The engine works in two stages:
 *  1. compute base deltas in "Sochinka" units (what the catsatcards page calls standard scoring)
 *  2. apply a variant transform (Leningradka doubles all mountain/whist; Rostov converts
 *     mountain → 5×whist-per-opponent then halves all whist)
 *
 * This separation keeps the rules legible and makes the variant differences explicit.
 */
object ScoringEngine {

    fun applyHand(
        sheet: ScoreSheet,
        hand: Hand,
        config: GameConfig,
    ): ScoreSheet {
        // In 4-player games the dealer sits out and does not earn whist from
        // Rostov mountain-substitute; "opponents" for variant purposes are
        // only the active seats for this hand.
        val activeSeats = if (config.playerCount == 4) {
            config.seats.filter { it != hand.dealerSeat }
        } else {
            config.seats
        }
        val baseDeltas = when (hand) {
            is Hand.Played -> scorePlayed(hand, config)
            is Hand.Raspasovka -> scoreRaspasovka(hand, config)
        }
        val transformed = applyVariantTransform(baseDeltas, config, activeSeats)
        return mergeDeltas(sheet, transformed, config)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Played hands
    // ────────────────────────────────────────────────────────────────────────

    private fun scorePlayed(hand: Hand.Played, config: GameConfig): List<ScoreDelta> {
        if (hand.bid is Bid.Misere) return scoreMisere(hand)

        val deltas = mutableListOf<ScoreDelta>()
        val level = hand.bid.level
        val v = valuePerTrick(level)
        val threshold = whistThreshold(level)
        val d = hand.declarerTricks
        val whisters = hand.opponents.filter { it.whisted }

        // Optional 4-player talon honors (whist bonus to the dealer column against declarer).
        if (hand.talonWhistBonus != 0) {
            deltas += ScoreDelta(
                seat = hand.dealerSeat,
                whistAgainst = mapOf(hand.declarerSeat to hand.talonWhistBonus),
            )
        }

        if (whisters.isEmpty()) {
            // Neither opponent whisted — declarer auto-wins, no play.
            deltas += ScoreDelta(seat = hand.declarerSeat, bullet = v)
            return deltas
        }

        val whistedTricks = whisters.sumOf { it.tricks }
        val whisterShares = splitThreshold(threshold, whisters.size)

        if (d >= level) {
            // Declarer made the contract.
            deltas += ScoreDelta(seat = hand.declarerSeat, bullet = v)

            // If whisters fell short of their share, each takes mountain for the gap.
            if (whistedTricks < threshold) {
                whisters.forEachIndexed { i, w ->
                    val gap = (whisterShares[i] - w.tricks).coerceAtLeast(0)
                    if (gap > 0) {
                        deltas += ScoreDelta(seat = w.seat, mountain = v * gap)
                    }
                }
            }
        } else {
            // Declarer failed — short by (level - d) tricks.
            val short = level - d
            deltas += ScoreDelta(seat = hand.declarerSeat, mountain = v * short)

            // Mirror whist: every opponent (whether they whisted or not) records V × short.
            for (opp in hand.opponents) {
                deltas += ScoreDelta(
                    seat = opp.seat,
                    whistAgainst = mapOf(hand.declarerSeat to v * short),
                )
            }

            // Additional whist for whisters per their overtricks above their share.
            whisters.forEachIndexed { i, w ->
                val over = (w.tricks - whisterShares[i]).coerceAtLeast(0)
                if (over > 0) {
                    deltas += ScoreDelta(
                        seat = w.seat,
                        whistAgainst = mapOf(hand.declarerSeat to v * over),
                    )
                }
                // Edge case: in a one-whists-alone scenario, the lone whister might
                // still be short of their share even when declarer failed (because
                // the passing opponent absorbed the tricks). Penalize the whister
                // for that gap as failed-whist.
                val gap = (whisterShares[i] - w.tricks).coerceAtLeast(0)
                if (gap > 0) {
                    deltas += ScoreDelta(seat = w.seat, mountain = v * gap)
                }
            }
        }
        return deltas
    }

    private fun scoreMisere(hand: Hand.Played): List<ScoreDelta> {
        val tricks = hand.declarerTricks
        return if (tricks == 0) {
            listOf(ScoreDelta(seat = hand.declarerSeat, bullet = 10))
        } else {
            listOf(ScoreDelta(seat = hand.declarerSeat, mountain = 10 * tricks))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Raspasovka
    // ────────────────────────────────────────────────────────────────────────

    private fun scoreRaspasovka(hand: Hand.Raspasovka, config: GameConfig): List<ScoreDelta> {
        val deltas = mutableListOf<ScoreDelta>()
        for ((seat, tricks) in hand.tricksBySeat) {
            when {
                tricks == 0 -> deltas += ScoreDelta(seat = seat, bullet = 1)
                tricks > 0 -> deltas += ScoreDelta(seat = seat, mountain = tricks)
            }
        }
        return deltas
    }

    // ────────────────────────────────────────────────────────────────────────
    // Variant transforms
    // ────────────────────────────────────────────────────────────────────────

    private fun applyVariantTransform(
        deltas: List<ScoreDelta>,
        config: GameConfig,
        activeSeats: List<SeatId>,
    ): List<ScoreDelta> {
        return when (config.variant) {
            Variant.SOCHINKA -> deltas
            Variant.LENINGRADKA -> deltas.map {
                it.copy(
                    mountain = it.mountain * 2,
                    whistAgainst = it.whistAgainst.mapValues { (_, v) -> v * 2 },
                )
            }
            Variant.ROSTOV -> deltas.flatMap { d ->
                rostovTransform(d, activeSeats)
            }
        }
    }

    /**
     * Rostov:
     *  - any mountain X added to seat S becomes 5×X whist to *each opponent* of S
     *    against S (and S's mountain becomes 0);
     *  - "normal" whist points (not from the mountain substitute) are halved (integer).
     *
     * "Opponents" here is restricted to seats active in the current hand — in
     * 4-player games the sitting-out dealer should not receive Rostov whist.
     */
    private fun rostovTransform(d: ScoreDelta, activeSeats: List<SeatId>): List<ScoreDelta> {
        val out = mutableListOf<ScoreDelta>()

        // Halve any whist that was already in the delta.
        val halvedWhist = d.whistAgainst.mapValues { (_, v) -> v / 2 }
        if (d.bullet != 0 || halvedWhist.any { it.value != 0 }) {
            out += ScoreDelta(
                seat = d.seat,
                bullet = d.bullet,
                whistAgainst = halvedWhist.filterValues { it != 0 },
            )
        }

        // Convert mountain → 5 whist per active opponent (against d.seat).
        if (d.mountain != 0) {
            val opponents = activeSeats.filter { it != d.seat }
            for (opp in opponents) {
                out += ScoreDelta(
                    seat = opp,
                    whistAgainst = mapOf(d.seat to 5 * d.mountain),
                )
            }
        }
        return out
    }

    // ────────────────────────────────────────────────────────────────────────
    // Merge deltas into a sheet
    // ────────────────────────────────────────────────────────────────────────

    private fun mergeDeltas(
        sheet: ScoreSheet,
        deltas: List<ScoreDelta>,
        config: GameConfig,
    ): ScoreSheet {
        // Start with a sheet that has every configured seat present.
        var s = ScoreSheet(
            scores = config.seats.associateWith { seat ->
                sheet.scores[seat] ?: PlayerScore(seat = seat)
            }
        )
        for (d in deltas) {
            s = s.update(d.seat) { p ->
                var next = p
                if (d.bullet != 0) next = next.addBullet(d.bullet)
                if (d.mountain != 0) next = next.addMountain(d.mountain)
                for ((opp, amount) in d.whistAgainst) {
                    if (amount != 0) next = next.addWhistAgainst(opp, amount)
                }
                next
            }
        }
        return s
    }

    // ────────────────────────────────────────────────────────────────────────
    // Threshold sharing
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Splits a whist threshold across N whisters as evenly as possible.
     * Remainder tricks are given to earlier whisters (index 0 first).
     * Example: T=4, N=2 → [2,2]; T=1, N=2 → [1,0]; T=2, N=2 → [1,1].
     */
    internal fun splitThreshold(threshold: Int, n: Int): List<Int> {
        if (n == 0) return emptyList()
        val base = threshold / n
        val rem = threshold % n
        return List(n) { i -> if (i < rem) base + 1 else base }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Final settlement
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Final settlement: apply mountain normalization & conversion to whist,
     * then compute net whist for each seat.
     *
     * Returns a map seat → net whist score (positive = winner). The original
     * sheet's bullet/mountain/whist values are not mutated.
     */
    fun settle(sheet: ScoreSheet, config: GameConfig): SettlementResult {
        val seats = config.seats
        val n = seats.size

        // Step 0: Leningradka doubles bullets at settlement.
        val bullets = seats.associateWith { seat ->
            val raw = sheet.scores[seat]?.bullet ?: 0
            if (config.variant == Variant.LENINGRADKA) raw * 2 else raw
        }

        // Step 1: mountain normalization — subtract the lowest mountain so the
        //         best player reaches zero. (No-op in Rostov where mountain is
        //         always zero.)
        val rawMountains = seats.associateWith { sheet.scores[it]?.mountain ?: 0 }
        val minMountain = rawMountains.values.min()
        val normMountains = rawMountains.mapValues { (_, m) -> m - minMountain }

        // Step 2: mountain → whist. Each normalized mountain × 10 / N is recorded
        //         in the whist column for *each* opponent.
        val mountainWhistAgainst: Map<SeatId, Map<SeatId, Int>> = seats.associateWith { earner ->
            val mountainOfTarget = seats.filter { it != earner }
                .associateWith { target -> normMountains.getValue(target) * 10 / n }
            mountainOfTarget
        }

        // Step 3: net whist per seat = sum(my whist against others, normal + mountain-converted)
        //                              - sum(others' whist against me).
        val net = seats.associateWith { me ->
            val myNormalAgainstOthers = sheet.scores[me]?.whistAgainst?.values?.sum() ?: 0
            val myMountainAgainstOthers = mountainWhistAgainst.getValue(me).values.sum()
            val othersAgainstMe = seats.filter { it != me }.sumOf { other ->
                val normalAgainstMe = sheet.scores[other]?.whistAgainst?.get(me) ?: 0
                val mountainAgainstMe = mountainWhistAgainst.getValue(other)[me] ?: 0
                normalAgainstMe + mountainAgainstMe
            }
            (myNormalAgainstOthers + myMountainAgainstOthers) - othersAgainstMe
        }

        return SettlementResult(
            bullets = bullets,
            normalizedMountain = normMountains,
            mountainConvertedWhist = mountainWhistAgainst,
            netWhist = net,
        )
    }
}

data class SettlementResult(
    val bullets: Map<SeatId, Int>,
    val normalizedMountain: Map<SeatId, Int>,
    val mountainConvertedWhist: Map<SeatId, Map<SeatId, Int>>,
    val netWhist: Map<SeatId, Int>,
) {
    val winner: SeatId? get() = netWhist.maxByOrNull { it.value }?.key
}
