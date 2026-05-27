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
import com.preferans.scorer.ui.GameViewModel

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
    val raspasovkaTricks = remember(handKey) {
        mutableStateMapOf<SeatId, Int>().apply {
            activeSeats.forEach { put(it, 0) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("New hand · #${game.nextHandNumber}") },
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
            SectionCard(title = "Hand type") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isRaspasovka,
                        onClick = { isRaspasovka = false },
                        label = { Text("Played contract") },
                    )
                    FilterChip(
                        selected = isRaspasovka,
                        onClick = { isRaspasovka = true },
                        label = { Text("All passed (Raspasovka)") },
                    )
                }
            }

            // Computed up-front so it's visible to both the tricks section and
            // the submit button. False unless we're in a Played contract with at
            // least one opponent set to HALF_WHIST.
            val anyHalfWhist = !isRaspasovka && activeSeats
                .filter { it != declarerSeat }
                .any { whistChoice[it] == com.preferans.scorer.domain.WhistChoice.HALF_WHIST }

            if (!isRaspasovka) {
                SectionCard(title = "Declarer") {
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

                SectionCard(title = "Bid") {
                    Text("Level", style = MaterialTheme.typography.labelMedium)
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
                            label = { Text("Misère") },
                        )
                    }
                    if (!isMisere) {
                        Spacer(Modifier.height(10.dp))
                        Text("Trump", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Suit.values().forEach { s ->
                                FilterChip(
                                    selected = suit == s,
                                    onClick = { suit = s },
                                    label = { Text("${s.symbol} ${s.displayName}") },
                                )
                            }
                            FilterChip(
                                selected = suit == null,
                                onClick = { suit = null },
                                label = { Text("NT") },
                            )
                        }
                    }
                }

                // The "both passed → auto-win" rule only applies to 6–9 level
                // suit/NT bids. Level 10 is always played (rules: whist N/A),
                // and Misère has no whist concept at all.
                val autoWinApplies = !isMisere && level < 10
                if (!autoWinApplies && autoWin) autoWin = false

                SectionCard(title = "Whist") {
                    if (autoWinApplies) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = autoWin,
                                onCheckedChange = { autoWin = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Both opponents passed", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Declarer auto-wins all 10 tricks; no play.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (isMisere) {
                        Text(
                            "Misère has no whist — opponents always play.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "A 10-bid is always played; there is no whist option.",
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
                                        com.preferans.scorer.domain.WhistChoice.PASS to "Pass",
                                        com.preferans.scorer.domain.WhistChoice.WHIST to "Whist",
                                        com.preferans.scorer.domain.WhistChoice.HALF_WHIST to "Half-whist",
                                    ).forEach { (choiceValue, choiceLabel) ->
                                        val isHalf = choiceValue == com.preferans.scorer.domain.WhistChoice.HALF_WHIST
                                        FilterChip(
                                            selected = whistChoice[opp] == choiceValue,
                                            onClick = {
                                                whistChoice[opp] = choiceValue
                                                // Mutex: half-whist requires the other opp to PASS
                                                if (isHalf) {
                                                    opponents.filter { it != opp }.forEach { other ->
                                                        whistChoice[other] = com.preferans.scorer.domain.WhistChoice.PASS
                                                    }
                                                }
                                            },
                                            enabled = !isHalf || halfWhistAllowed,
                                            label = { Text(choiceLabel) },
                                        )
                                    }
                                }
                            }
                        }
                        if (anyHalfWhist) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Half-whist: hand ends without play. Declarer is credited as " +
                                    "having made the contract; the half-whister scores " +
                                    "V × (threshold / 2) whist points against declarer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!autoWin && !isMisere && !anyHalfWhist) {
                    SectionCard(title = "Tricks taken") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${game.config.nameOf(declarerSeat)} (declarer)",
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
                            "Sum: $sum / 10",
                            color = color,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (isMisere) {
                    SectionCard(title = "Declarer tricks taken") {
                        NumberStepper(value = declarerTricks, range = 0..10) { declarerTricks = it }
                        Text(
                            if (declarerTricks == 0) "Misère succeeded — +10 bullets."
                            else "Misère failed — +${10 * declarerTricks} mountain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (game.config.playerCount == 4) {
                    SectionCard(title = "Talon honors (dealer bonus)") {
                        Text(
                            "Whist points credited to the dealer column. 0 if none.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Dealer: ${game.config.nameOf(game.nextDealerSeat)}",
                                modifier = Modifier.width(180.dp),
                            )
                            NumberStepper(value = talonBonus, range = 0..40) { talonBonus = it }
                        }
                    }
                }
            } else {
                // Raspasovka
                SectionCard(title = "Tricks taken (Raspasovka)") {
                    activeSeats.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(game.config.nameOf(s), modifier = Modifier.width(180.dp))
                            NumberStepper(value = raspasovkaTricks[s] ?: 0, range = 0..10) {
                                raspasovkaTricks[s] = it
                            }
                        }
                    }
                    val sum = raspasovkaTricks.values.sum()
                    val color =
                        if (sum == 10) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    Text(
                        "Sum: $sum / 10",
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
                        )
                    }
                    vm.recordHand(hand)
                    onClose()
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Record hand")
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
