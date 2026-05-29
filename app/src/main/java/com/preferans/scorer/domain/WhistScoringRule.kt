package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * How whisting opponents score when the declarer **makes** the contract.
 *
 * Both rules behave identically when the declarer FAILS: each opponent gets the
 * mirror whist (V × undertricks) and whisters split the flat V bonus.
 *
 *  - [FAILURE_ONLY]: faithful to catsatcards.com. On a made contract whisters
 *    score nothing (only a mountain penalty if they fell short of their whist
 *    threshold). This is the simplified American convention.
 *
 *  - [TRICKS_TAKEN]: standard preferans. On a made contract each whisting
 *    opponent additionally records V × (tricks they took) whist against the
 *    declarer, so defending well is still rewarded even when the contract holds.
 */
@Serializable
enum class WhistScoringRule {
    FAILURE_ONLY,
    TRICKS_TAKEN;
}
