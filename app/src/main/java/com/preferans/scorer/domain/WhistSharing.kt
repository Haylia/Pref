package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * When one opponent whists and the other passes, how is the whister's *reward*
 * (the declarer-failure bonus and, under [WhistScoringRule.TRICKS_TAKEN], the
 * made-contract tricks-taken credit) divided between them?
 *
 *  - [GREEDY]: the whister keeps the whole reward; the passer gets none of it.
 *
 *  - [GENTLEMANS]: the whister shares the reward with the passer, who helped
 *    defend. On an odd total the whister keeps the larger half (ceil), the
 *    passer gets the rest (floor).
 *
 * Only the whister's reward is affected. The declarer-failure mirror (which both
 * opponents record individually) and the failed-whist mountain penalty are not
 * touched, and there is no effect when both opponents whist.
 */
@Serializable
enum class WhistSharing {
    GREEDY,
    GENTLEMANS;
}
