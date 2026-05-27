package com.preferans.scorer.scoring

import com.preferans.scorer.domain.SeatId

/**
 * A single accounting entry from scoring one hand. Multiple deltas may
 * accumulate against the same seat; they merge at apply time.
 */
internal data class ScoreDelta(
    val seat: SeatId,
    val bullet: Int = 0,
    val mountain: Int = 0,
    /** Whist points scored by [seat] against the given opponent. */
    val whistAgainst: Map<SeatId, Int> = emptyMap(),
)
