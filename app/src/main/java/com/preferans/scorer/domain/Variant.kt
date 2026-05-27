package com.preferans.scorer.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Variant(val displayName: String, val description: String) {
    SOCHINKA(
        "Sochinka",
        "Standard scoring. Base point values."
    ),
    LENINGRADKA(
        "Leningradka",
        "Mountain and whist points doubled; bullets doubled at settlement."
    ),
    ROSTOV(
        "Rostov",
        "No mountain — each mountain unit becomes 5 whist for each opponent. Other whist halved."
    );
}
