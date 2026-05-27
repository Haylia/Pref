package com.preferans.scorer.ui.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.preferans.scorer.scoring.ScoringEngine
import com.preferans.scorer.ui.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    vm: GameViewModel,
    onNewGame: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val game = state ?: return
    val settlement = remember(game) { ScoringEngine.settle(game.sheet, game.config) }
    val seats = game.config.seats

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Settlement") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            settlement.winner?.let { w ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Winner",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            game.config.nameOf(w),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Net whist: ${settlement.netWhist[w]}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Final scores",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell("Player", w = 1.6f, align = TextAlign.Start)
                        Cell("Bullets", w = 1f, header = true)
                        Cell("Mtn (norm)", w = 1.2f, header = true)
                        Cell("Net whist", w = 1.2f, header = true)
                    }
                    HRule()
                    seats.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            Cell(game.config.nameOf(s), w = 1.6f, align = TextAlign.Start)
                            Cell("${settlement.bullets[s]}", w = 1f)
                            Cell("${settlement.normalizedMountain[s]}", w = 1.2f)
                            Cell(
                                "${settlement.netWhist[s]}",
                                w = 1.2f,
                                highlight = (s == settlement.winner),
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "How net whist is computed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Normalize: subtract the lowest mountain so the best player reaches 0.\n" +
                            "2. Convert: multiply each normalized mountain by 10 and divide by the number of players. " +
                            "Record that value in the whist column for each opponent.\n" +
                            "3. Net whist for a player = (whist they scored against others) − (whist others scored against them).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                Text("Start a new game")
            }
        }
    }
}

@Composable
private fun Cell(
    text: String,
    w: Float,
    align: TextAlign = TextAlign.Center,
    header: Boolean = false,
    highlight: Boolean = false,
) {
    Box(
        modifier = Modifier
            .width((w * 80).dp)
            .padding(4.dp),
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            text,
            style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (header || highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = align,
        )
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
