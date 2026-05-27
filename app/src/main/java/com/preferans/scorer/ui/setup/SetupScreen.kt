package com.preferans.scorer.ui.setup

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.Player
import com.preferans.scorer.domain.Variant
import com.preferans.scorer.ui.theme.ThemeToggleButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onStart: (GameConfig, firstDealerSeat: Int) -> Unit) {
    var variant by remember { mutableStateOf(Variant.SOCHINKA) }
    var playerCount by remember { mutableIntStateOf(3) }
    var bulletTarget by remember { mutableIntStateOf(10) }
    val names = remember { mutableStateListOf("Player 1", "Player 2", "Player 3", "Player 4") }
    var firstDealer by remember { mutableIntStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("New Preferans Game") },
                actions = { ThemeToggleButton() },
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
            SectionCard(title = "Variant") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Variant.values().forEach { v ->
                        FilterChip(
                            selected = v == variant,
                            onClick = { variant = v },
                            label = {
                                Column {
                                    Text(v.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        v.description,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }

            SectionCard(title = "Players") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = playerCount == 3,
                        onClick = { playerCount = 3; if (firstDealer > 2) firstDealer = 0 },
                        label = { Text("3 players") },
                    )
                    FilterChip(
                        selected = playerCount == 4,
                        onClick = { playerCount = 4 },
                        label = { Text("4 players") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(playerCount) { i ->
                        OutlinedTextField(
                            value = names[i],
                            onValueChange = { names[i] = it },
                            label = { Text("Seat ${i + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            SectionCard(title = "Bullet target") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(6, 10, 15, 20).forEach { t ->
                        FilterChip(
                            selected = bulletTarget == t,
                            onClick = { bulletTarget = t },
                            label = { Text(t.toString()) },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = bulletTarget.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { v -> if (v in 1..200) bulletTarget = v }
                        },
                        modifier = Modifier.width(96.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        label = { Text("Custom") },
                    )
                }
            }

            SectionCard(title = "First dealer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(playerCount) { i ->
                        FilterChip(
                            selected = firstDealer == i,
                            onClick = { firstDealer = i },
                            label = { Text(names[i].ifBlank { "Seat ${i + 1}" }) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val players = (0 until playerCount).map { Player(it, names[it].ifBlank { "Seat ${it + 1}" }) }
                    val config = GameConfig(
                        variant = variant,
                        players = players,
                        bulletTarget = bulletTarget,
                    )
                    onStart(config, firstDealer)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start game")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
