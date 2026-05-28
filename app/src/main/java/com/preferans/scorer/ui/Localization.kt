package com.preferans.scorer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.preferans.scorer.R
import com.preferans.scorer.domain.Bid
import com.preferans.scorer.domain.Suit
import com.preferans.scorer.domain.Variant
import com.preferans.scorer.domain.WhistChoice

@Composable
fun Suit.localizedName(): String = when (this) {
    Suit.SPADES -> stringResource(R.string.suit_spades)
    Suit.CLUBS -> stringResource(R.string.suit_clubs)
    Suit.DIAMONDS -> stringResource(R.string.suit_diamonds)
    Suit.HEARTS -> stringResource(R.string.suit_hearts)
}

@Composable
fun Variant.localizedName(): String = when (this) {
    Variant.SOCHINKA -> stringResource(R.string.variant_sochinka)
    Variant.LENINGRADKA -> stringResource(R.string.variant_leningradka)
    Variant.ROSTOV -> stringResource(R.string.variant_rostov)
}

@Composable
fun Variant.localizedDescription(): String = when (this) {
    Variant.SOCHINKA -> stringResource(R.string.variant_sochinka_desc)
    Variant.LENINGRADKA -> stringResource(R.string.variant_leningradka_desc)
    Variant.ROSTOV -> stringResource(R.string.variant_rostov_desc)
}

@Composable
fun Bid.localizedDisplayName(): String = when (this) {
    is Bid.SuitBid -> "${this.level}${this.suit.symbol}"
    is Bid.NoTrumpBid -> "${this.level}${stringResource(R.string.bid_nt)}"
    Bid.Misere -> stringResource(R.string.bid_misere)
}

@Composable
fun WhistChoice.localizedLabel(): String = when (this) {
    WhistChoice.PASS -> stringResource(R.string.whist_pass)
    WhistChoice.WHIST -> stringResource(R.string.whist_full)
    WhistChoice.HALF_WHIST -> stringResource(R.string.whist_half)
}
