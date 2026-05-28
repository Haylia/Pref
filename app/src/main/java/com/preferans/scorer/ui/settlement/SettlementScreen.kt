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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.preferans.scorer.R
import com.preferans.scorer.scoring.ScoringEngine
import com.preferans.scorer.ui.GameViewModel
import com.preferans.scorer.ui.theme.LanguageToggleButton
import com.preferans.scorer.ui.theme.ThemeToggleButton

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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settlement_title)) },
                actions = {
                    LanguageToggleButton()
                    ThemeToggleButton()
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
                            stringResource(R.string.settlement_winner),
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
                            stringResource(R.string.settlement_net_whist_fmt, settlement.netWhist[w] ?: 0),
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
                        stringResource(R.string.settlement_final_scores),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(stringResource(R.string.col_player), w = 1.6f, align = TextAlign.Start, header = true)
                        Cell(stringResource(R.string.col_bullets), w = 1f, header = true)
                        Cell(stringResource(R.string.col_mtn_norm), w = 1.2f, header = true)
                        Cell(stringResource(R.string.col_net_whist), w = 1.2f, header = true)
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
                        stringResource(R.string.settle_explain_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settle_explain_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_start_new_game))
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
