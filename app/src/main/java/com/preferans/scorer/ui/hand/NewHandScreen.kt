package com.preferans.scorer.ui.hand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.preferans.scorer.domain.Bid
import com.preferans.scorer.domain.GameState
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.SeatId
import com.preferans.scorer.domain.Suit
import com.preferans.scorer.domain.WhisterRecord
import androidx.compose.ui.res.stringResource
import com.preferans.scorer.R
import com.preferans.scorer.ui.GameViewModel
import com.preferans.scorer.ui.localizedName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHandScreen(
    vm: GameViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val game = state ?: return

    val activeSeats = game.activeSeats(game.nextDealerSeat)
    val handKey = game.nextHandNumber // re-seed all state when a hand is recorded

    // One-shot prefill from VM (e.g., from a "Record hand as declarer" tap on the visual chart).
    val prefilledDeclarer = remember(handKey) {
        val s = vm.consumePendingDeclarer()
        if (s != null && s in activeSeats) s else activeSeats.first()
    }

    var isRaspasovka by remember(handKey) { mutableStateOf(false) }
    var declarerSeat by remember(handKey) { mutableIntStateOf(prefilledDeclarer) }
    var level by remember(handKey) { mutableIntStateOf(6) }
    var isMisere by remember(handKey) { mutableStateOf(false) }
    var suit by remember(handKey) { mutableStateOf<Suit?>(Suit.SPADES) }
    var autoWin by remember(handKey) { mutableStateOf(false) }
    val whistChoice = remember(handKey, declarerSeat) {
        mutableStateMapOf<SeatId, com.preferans.scorer.domain.WhistChoice>().apply {
            activeSeats.filter { it != declarerSeat }
                .forEach { put(it, com.preferans.scorer.domain.WhistChoice.PASS) }
        }
    }
    val tricks = remember(handKey, declarerSeat) {
        mutableStateMapOf<SeatId, Int>().apply {
            activeSeats.filter { it != declarerSeat }.forEach { put(it, 0) }
        }
    }
    var declarerTricks by remember(handKey) { mutableIntStateOf(6) }
    var talonBonus by remember(handKey) { mutableIntStateOf(0) }
    var dealerStandsInFor by remember(handKey) { mutableStateOf<SeatId?>(null) }
    // 4-player raspasovka tracks all 4 seats including the dealer (who plays the
    // widow's two tricks). 3-player only tracks the 3 active seats.
    val raspasovkaSeats = if (game.config.playerCount == 4) game.config.seats else activeSeats
    val raspasovkaTricks = remember(handKey) {
        mutableStateMapOf<SeatId, Int>().apply {
            game.config.seats.forEach { put(it, 0) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_hand_title_fmt, game.nextHandNumber)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.section_hand_type)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isRaspasovka,
                        onClick = { isRaspasovka = false },
                        label = { Text(stringResource(R.string.hand_played_contract)) },
                    )
                    FilterChip(
                        selected = isRaspasovka,
                        onClick = { isRaspasovka = true },
                        label = { Text(stringResource(R.string.hand_all_passed)) },
                    )
                }
            }

            // Half-whist is only valid for 6- and 7-level suit/NT bids. If the
            // user picked half-whist and then changed the level or switched to
            // misère, clear the stale selection so it can't submit invalid scoring.
            LaunchedEffect(level, isMisere, isRaspasovka) {
                if (isRaspasovka || isMisere || level !in 6..7) {
                    val toReset = whistChoice.entries
                        .filter { it.value == com.preferans.scorer.domain.WhistChoice.HALF_WHIST }
                        .map { it.key }
                    toReset.forEach {
                        whistChoice[it] = com.preferans.scorer.domain.WhistChoice.PASS
                    }
                }
            }

            // Computed up-front so it's visible to both the tricks section and
            // the submit button. False unless we're in a Played contract with at
            // least one opponent set to HALF_WHIST.
            val anyHalfWhist = !isRaspasovka && activeSeats
                .filter { it != declarerSeat }
                .any { whistChoice[it] == com.preferans.scorer.domain.WhistChoice.HALF_WHIST }

            if (!isRaspasovka) {
                SectionCard(title = stringResource(R.string.section_declarer)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeSeats.forEach { s ->
                            FilterChip(
                                selected = s == declarerSeat,
                                onClick = { declarerSeat = s },
                                label = { Text(game.config.nameOf(s)) },
                            )
                        }
                    }
                }

                SectionCard(title = stringResource(R.string.section_bid)) {
                    Text(stringResource(R.string.bid_level), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 7, 8, 9, 10).forEach { lv ->
                            FilterChip(
                                selected = !isMisere && level == lv,
                                onClick = { isMisere = false; level = lv },
                                label = { Text(lv.toString()) },
                            )
                        }
                        FilterChip(
                            selected = isMisere,
                            onClick = { isMisere = true },
                            label = { Text(stringResource(R.string.bid_misere)) },
                        )
                    }
                    if (!isMisere) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.bid_trump), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Suit.entries.forEach { s ->
                                FilterChip(
                                    selected = suit == s,
                                    onClick = { suit = s },
                                    label = { Text("${s.symbol} ${s.localizedName()}") },
                                )
                            }
                            FilterChip(
                                selected = suit == null,
                                onClick = { suit = null },
                                label = { Text(stringResource(R.string.bid_nt)) },
                            )
                        }
                    }
                }

                val autoWinApplies = !isMisere && level < 10
                if (!autoWinApplies && autoWin) autoWin = false

                SectionCard(title = stringResource(R.string.section_whist)) {
                    if (autoWinApplies) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = autoWin,
                                onCheckedChange = { autoWin = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    stringResource(R.string.both_passed_title),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    stringResource(R.string.both_passed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (isMisere) {
                        Text(
                            stringResource(R.string.misere_no_whist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.ten_no_whist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Whist choice (Pass / Whist / Half-whist) — non-misère only.
                    if (!autoWin && !isMisere) {
                        Spacer(Modifier.height(8.dp))
                        val halfWhistAllowed = level == 6 || level == 7
                        val opponents = activeSeats.filter { it != declarerSeat }
                        opponents.forEach { opp ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    game.config.nameOf(opp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        com.preferans.scorer.domain.WhistChoice.PASS to R.string.whist_pass,
                                        com.preferans.scorer.domain.WhistChoice.WHIST to R.string.whist_full,
                                        com.preferans.scorer.domain.WhistChoice.HALF_WHIST to R.string.whist_half,
                                    ).forEach { (choiceValue, labelRes) ->
                                        val isHalf = choiceValue == com.preferans.scorer.domain.WhistChoice.HALF_WHIST
                                        FilterChip(
                                            selected = whistChoice[opp] == choiceValue,
                                            onClick = {
                                                whistChoice[opp] = choiceValue
                                                if (isHalf) {
                                                    // Half-whist requires the other opp to PASS.
                                                    opponents.filter { it != opp }.forEach { other ->
                                                        whistChoice[other] = com.preferans.scorer.domain.WhistChoice.PASS
                                                    }
                                                    // Half-whist and dealer-stand-in are mutually exclusive:
                                                    // half-whist ends the hand without play, dealer-stand-in
                                                    // means the dealer plays a hand. Clear the other.
                                                    dealerStandsInFor = null
                                                }
                                            },
                                            enabled = !isHalf || halfWhistAllowed,
                                            label = { Text(stringResource(labelRes)) },
                                        )
                                    }
                                }
                            }
                        }
                        if (anyHalfWhist) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.half_whist_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 4-player only: dealer can opt to whist for one opponent.
                if (game.config.playerCount == 4 && !isMisere) {
                    SectionCard(title = stringResource(R.string.section_dealer_whists)) {
                        Text(
                            stringResource(R.string.dealer_whists_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        val opps = activeSeats.filter { it != declarerSeat }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = dealerStandsInFor == null,
                                onClick = { dealerStandsInFor = null },
                                label = { Text(stringResource(R.string.dealer_whists_none)) },
                            )
                            opps.forEach { opp ->
                                FilterChip(
                                    selected = dealerStandsInFor == opp,
                                    onClick = {
                                        dealerStandsInFor = opp
                                        // Dealer stepping in means a hand is played:
                                        // disable auto-win, mark the replaced opp as
                                        // whisting (the dealer plays as them).
                                        autoWin = false
                                        whistChoice[opp] = com.preferans.scorer.domain.WhistChoice.WHIST
                                        // Clear any half-whist selections — they're
                                        // mutually exclusive with a played dealer hand.
                                        opps.filter { it != opp }.forEach { other ->
                                            if (whistChoice[other] ==
                                                com.preferans.scorer.domain.WhistChoice.HALF_WHIST
                                            ) {
                                                whistChoice[other] =
                                                    com.preferans.scorer.domain.WhistChoice.PASS
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.dealer_takes_fmt,
                                                game.config.nameOf(opp),
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (!autoWin && !isMisere && !anyHalfWhist) {
                    SectionCard(title = stringResource(R.string.section_tricks_taken)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${game.config.nameOf(declarerSeat)} ${stringResource(R.string.declarer_paren)}",
                                modifier = Modifier.width(180.dp),
                            )
                            NumberStepper(value = declarerTricks, range = 0..10) { declarerTricks = it }
                        }
                        activeSeats.filter { it != declarerSeat }.forEach { opp ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(game.config.nameOf(opp), modifier = Modifier.width(180.dp))
                                NumberStepper(value = tricks[opp] ?: 0, range = 0..10) {
                                    tricks[opp] = it
                                }
                            }
                        }
                        val sum = declarerTricks + (tricks.values.sum())
                        val color =
                            if (sum == 10) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        Text(
                            stringResource(R.string.sum_of_ten_fmt, sum),
                            color = color,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (isMisere) {
                    SectionCard(title = stringResource(R.string.declarer_tricks_taken)) {
                        NumberStepper(value = declarerTricks, range = 0..10) { declarerTricks = it }
                        Text(
                            if (declarerTricks == 0)
                                stringResource(R.string.misere_succeeded)
                            else
                                stringResource(R.string.misere_failed_fmt, 10 * declarerTricks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (game.config.playerCount == 4) {
                    SectionCard(title = stringResource(R.string.section_talon)) {
                        Text(
                            stringResource(R.string.talon_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.dealer_fmt, game.config.nameOf(game.nextDealerSeat)),
                                modifier = Modifier.width(180.dp),
                            )
                            NumberStepper(value = talonBonus, range = 0..40) { talonBonus = it }
                        }
                    }
                }
            } else {
                SectionCard(title = stringResource(R.string.section_tricks_raspasovka)) {
                    // In 4-player, include the dealer too — they play the widow's
                    // two tricks. Their stepper is bounded to 0..2.
                    raspasovkaSeats.forEach { s ->
                        val isDealer = game.config.playerCount == 4 && s == game.nextDealerSeat
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isDealer)
                                    "${game.config.nameOf(s)} ${stringResource(R.string.dealer_label)}"
                                else
                                    game.config.nameOf(s),
                                modifier = Modifier.width(180.dp),
                            )
                            NumberStepper(
                                value = raspasovkaTricks[s] ?: 0,
                                range = if (isDealer) 0..2 else 0..10,
                            ) {
                                raspasovkaTricks[s] = it
                            }
                        }
                    }
                    val sum = raspasovkaTricks.values.sum()
                    val color =
                        if (sum == 10) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    Text(
                        stringResource(R.string.sum_of_ten_fmt, sum),
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val canSubmit = canSubmit(
                isRaspasovka = isRaspasovka,
                isMisere = isMisere,
                autoWin = autoWin,
                anyHalfWhist = anyHalfWhist,
                declarerTricks = declarerTricks,
                opponentTricks = tricks,
                raspasovkaTricks = raspasovkaTricks,
            )

            Button(
                onClick = {
                    val hand: Hand = if (isRaspasovka) {
                        Hand.Raspasovka(
                            handNumber = game.nextHandNumber,
                            dealerSeat = game.nextDealerSeat,
                            tricksBySeat = raspasovkaTricks.toMap(),
                        )
                    } else {
                        val bid: Bid = when {
                            isMisere -> Bid.Misere
                            suit == null -> Bid.NoTrumpBid(level)
                            else -> Bid.SuitBid(level, suit!!)
                        }
                        val opps = activeSeats.filter { it != declarerSeat }
                        // For half-whist: synthesise the trick distribution so
                        // Hand.Played.init's sum == 10 invariant holds. The engine
                        // detects the choice and ignores the values.
                        val halfThreshold = (10 - level) / 2
                        val opponents = opps.map { opp ->
                            val opponentChoice = if (autoWin || isMisere)
                                com.preferans.scorer.domain.WhistChoice.PASS
                            else
                                whistChoice[opp] ?: com.preferans.scorer.domain.WhistChoice.PASS

                            val opponentTricks = when {
                                autoWin -> 0
                                isMisere -> {
                                    val remaining = 10 - declarerTricks
                                    val per = remaining / opps.size
                                    val rem = remaining % opps.size
                                    val idx = opps.indexOf(opp)
                                    if (idx < rem) per + 1 else per
                                }
                                anyHalfWhist -> when (opponentChoice) {
                                    com.preferans.scorer.domain.WhistChoice.HALF_WHIST -> halfThreshold
                                    else -> 0
                                }
                                else -> tricks[opp] ?: 0
                            }
                            WhisterRecord(
                                seat = opp,
                                choice = opponentChoice,
                                tricks = opponentTricks,
                            )
                        }
                        val effectiveDeclarerTricks = when {
                            autoWin -> 10
                            // Half-whist: hand wasn't played. Tricks are synthesised
                            // to satisfy the sum==10 invariant. Engine ignores them.
                            anyHalfWhist -> 10 - halfThreshold
                            else -> declarerTricks
                        }
                        Hand.Played(
                            handNumber = game.nextHandNumber,
                            dealerSeat = game.nextDealerSeat,
                            declarerSeat = declarerSeat,
                            bid = bid,
                            declarerTricks = effectiveDeclarerTricks,
                            opponents = opponents,
                            talonWhistBonus = if (game.config.playerCount == 4) talonBonus else 0,
                            dealerStandsInFor = if (game.config.playerCount == 4) dealerStandsInFor else null,
                        )
                    }
                    vm.recordHand(hand)
                    onClose()
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_record_hand))
            }
        }
    }
}

private fun canSubmit(
    isRaspasovka: Boolean,
    isMisere: Boolean,
    autoWin: Boolean,
    anyHalfWhist: Boolean,
    declarerTricks: Int,
    opponentTricks: Map<SeatId, Int>,
    raspasovkaTricks: Map<SeatId, Int>,
): Boolean {
    if (isRaspasovka) return raspasovkaTricks.values.sum() == 10
    if (isMisere) return declarerTricks in 0..10
    if (autoWin || anyHalfWhist) return true
    val sum = declarerTricks + opponentTricks.values.sum()
    return sum == 10
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NumberStepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { s ->
                s.toIntOrNull()?.let { v -> if (v in range) onChange(v) }
            },
            modifier = Modifier.width(80.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.width(8.dp))
        SmallButton("−") { if (value - 1 in range) onChange(value - 1) }
        Spacer(Modifier.width(4.dp))
        SmallButton("+") { if (value + 1 in range) onChange(value + 1) }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) { Text(label) }
}
