package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

/**
 * An opponent's response to a non-misère contract.
 *
 *  - [PASS]: declined to whist; counts only for mirror-whist purposes if declarer fails.
 *  - [WHIST]: full whist — shares the threshold burden; eligible for the flat V bonus
 *    on declarer failure; pays failed-share mountain if individually short.
 *  - [HALF_WHIST]: only valid for 6- and 7-level contracts. Triggered when the *other*
 *    opponent passed and this one offered half-whist; the passer then declined a final
 *    chance to whist. The hand ends without play. Declarer is credited as having made
 *    the contract; the half-whister scores V × (threshold/2) whist against declarer.
 */
@Serializable
enum class WhistChoice {
    PASS,
    WHIST,
    HALF_WHIST;
}
