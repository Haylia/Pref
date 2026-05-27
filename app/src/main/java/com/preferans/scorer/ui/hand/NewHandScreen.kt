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

    var isRaspasovka by remember(handKey) { mutableStateOf(false) }
    var declarerSeat by remember(handKey) { mutableIntStateOf(activeSeats.first()) }
    var level by remember(handKey) { mutableIntStateOf(6) }
    var isMisere by remember(handKey) { mutableStateOf(false) }
    var suit by remember(handKey) { mutableStateOf<Suit?>(Suit.SPADES) }
    var autoWin by remember(handKey) { mutableStateOf(false) }
    val whisted = remember(handKey, declarerSeat) {
        mutableStateMapOf<SeatId, Boolean>().apply {
            activeSeats.filter { it != declarerSeat }.forEach { put(it, false) }
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

                SectionCard(title = "Whist") {
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
                    if (!autoWin) {
                        Spacer(Modifier.height(8.dp))
                        activeSeats.filter { it != declarerSeat }.forEach { opp ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = whisted[opp] ?: false,
                                    onCheckedChange = { whisted[opp] = it },
                                )
                                Text("${game.config.nameOf(opp)} whisted")
                            }
                        }
                    }
                }

                if (!autoWin && !isMisere) {
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
                declarerTricks = declarerTricks,
                opponentTricks = tricks,
                raspasovkaTricks = raspasovkaTricks,
                suit = suit,
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
                        val opponents = activeSeats.filter { it != declarerSeat }.map { opp ->
                            WhisterRecord(
                                seat = opp,
                                whisted = !autoWin && (whisted[opp] ?: false),
                                tricks = when {
                                    autoWin -> 0
                                    isMisere -> {
                                        val others = activeSeats.filter { it != declarerSeat }
                                        val remaining = 10 - declarerTricks
                                        // Distribute remaining evenly between misère opponents
                                        val per = remaining / others.size
                                        val rem = remaining % others.size
                                        val idx = others.indexOf(opp)
                                        if (idx < rem) per + 1 else per
                                    }
                                    else -> tricks[opp] ?: 0
                                },
                            )
                        }
                        val effectiveDeclarerTricks = if (autoWin) 10 else declarerTricks
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
    declarerTricks: Int,
    opponentTricks: Map<SeatId, Int>,
    raspasovkaTricks: Map<SeatId, Int>,
    suit: Suit?,
): Boolean {
    if (isRaspasovka) return raspasovkaTricks.values.sum() == 10
    if (isMisere) return declarerTricks in 0..10
    if (autoWin) return true
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
