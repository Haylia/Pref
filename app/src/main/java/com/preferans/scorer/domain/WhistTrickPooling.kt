package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * When exactly one opponent whists and the other passes, how are the passer's
 * tricks treated for the lone whister's threshold check and (under
 * [WhistScoringRule.TRICKS_TAKEN]) their whist credit?
 *
 *  - [POOLED]: the passer is expected to help defend, so their tricks count
 *    toward the lone whister's total. The whister meets the whist threshold on
 *    the combined defensive tricks and scores for all of them. This is the
 *    common live-play convention.
 *
 *  - [INDIVIDUAL]: only the whister's own tricks count. The passer's tricks are
 *    ignored for the whister's obligation and credit.
 *
 * Has no effect when both opponents whist (there is no passer) — the threshold
 * is then split between them as usual.
 */
@Serializable
enum class WhistTrickPooling {
    POOLED,
    INDIVIDUAL;
}
