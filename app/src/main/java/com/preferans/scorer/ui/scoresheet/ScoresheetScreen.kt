package com.preferans.scorer.ui.scoresheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.GameState
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.PlayerScore
import com.preferans.scorer.domain.SeatId
import com.preferans.scorer.domain.Variant
import com.preferans.scorer.ui.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoresheetScreen(
    vm: GameViewModel,
    onAddHand: () -> Unit,
    onSettle: () -> Unit,
    onNewGame: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val game = state ?: return

    var menuOpen by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var confirmSettle by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Preferans · ${game.config.variant.displayName}") },
                actions = {
                    IconButton(
                        onClick = { vm.undoLastHand() },
                        enabled = game.hands.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo last hand")
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("Menu") }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settle now") },
                                onClick = { menuOpen = false; confirmSettle = true },
                            )
                            DropdownMenuItem(
                                text = { Text("New game") },
                                onClick = { menuOpen = false; confirmNew = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddHand,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New hand") },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ScoresheetTable(game)
            Spacer(Modifier.height(8.dp))
            WhistMatrix(game)
            Spacer(Modifier.height(8.dp))
            Text(
                "Next dealer: ${game.config.nameOf(game.nextDealerSeat)}  ·  Hand #${game.nextHandNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hand history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            if (game.hands.isEmpty()) {
                Text(
                    "No hands recorded yet. Tap “New hand” to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = game.hands.reversed(),
                        key = { it.handNumber },
                    ) { h -> HandRow(h, game.config) }
                }
            }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            confirmButton = {
                TextButton(onClick = { confirmNew = false; onNewGame() }) { Text("New game") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) { Text("Cancel") }
            },
            title = { Text("Start a new game?") },
            text = { Text("This discards the current game and its scoresheet.") },
        )
    }

    if (confirmSettle) {
        AlertDialog(
            onDismissRequest = { confirmSettle = false },
            confirmButton = {
                TextButton(onClick = { confirmSettle = false; onSettle() }) { Text("Settle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSettle = false }) { Text("Cancel") }
            },
            title = { Text("Settle the game now?") },
            text = { Text("Computes final whist totals using the current state.") },
        )
    }
}

@Composable
private fun ScoresheetTable(game: GameState) {
    val seats = game.config.seats
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CellHeader("", weight = 1.4f, align = TextAlign.Start)
                seats.forEach { s -> CellHeader(game.config.nameOf(s), weight = 1f) }
            }
            HRule()
            ScoreRow("Pulja / ${game.config.bulletTarget}", seats, game) { it.bullet.toString() }
            if (game.config.variant != Variant.ROSTOV) {
                ScoreRow("Gora", seats, game) { it.mountain.toString() }
            }
            ScoreRow("Whist (total)", seats, game) { it.totalWhist().toString() }
        }
    }
}

@Composable
private fun ScoreRow(
    label: String,
    seats: List<SeatId>,
    game: GameState,
    valueFor: (PlayerScore) -> String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CellLabel(label, weight = 1.4f)
        seats.forEach { s ->
            val score = game.sheet.scores[s] ?: PlayerScore(seat = s)
            CellValue(valueFor(score), weight = 1f)
        }
    }
}

@Composable
private fun WhistMatrix(game: GameState) {
    val seats = game.config.seats
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Whist (row scores against column)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CellHeader("", weight = 1.4f, align = TextAlign.Start)
                seats.forEach { s -> CellHeader(game.config.nameOf(s), weight = 1f) }
            }
            HRule()
            seats.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    CellLabel(game.config.nameOf(row), weight = 1.4f)
                    seats.forEach { col ->
                        val v = if (col == row) "—"
                        else (game.sheet.scores[row]?.whistAgainst?.get(col) ?: 0).toString()
                        CellValue(v, weight = 1f)
                    }
                }
            }
        }
    }
}

@Composable
private fun HRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun CellHeader(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Center,
) {
    Box(
        modifier = Modifier
            .width((weight * 80).dp)
            .padding(4.dp),
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            textAlign = align,
        )
    }
}

@Composable
private fun CellLabel(text: String, weight: Float) {
    Box(
        modifier = Modifier
            .width((weight * 80).dp)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CellValue(text: String, weight: Float) {
    Box(
        modifier = Modifier
            .width((weight * 80).dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HandRow(h: Hand, config: GameConfig) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (h) {
                is Hand.Played -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#${h.handNumber}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            h.bid.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "by ${config.nameOf(h.declarerSeat)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val tricksLine = buildString {
                        append("Tricks: ")
                        append(config.nameOf(h.declarerSeat))
                        append(" ${h.declarerTricks}")
                        for (opp in h.opponents) {
                            append(" · ")
                            append(config.nameOf(opp.seat))
                            append(" ${opp.tricks}")
                            if (opp.whisted) append(" (whist)")
                        }
                    }
                    Text(tricksLine, style = MaterialTheme.typography.bodySmall)
                }
                is Hand.Raspasovka -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#${h.handNumber}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            "Raspasovka",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val tricksLine = h.tricksBySeat.entries.joinToString(" · ") { (seat, n) ->
                        "${config.nameOf(seat)} $n"
                    }
                    Text("Tricks: $tricksLine", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
