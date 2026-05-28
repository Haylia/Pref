package com.preferans.scorer.ui.scoresheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.preferans.scorer.R
import com.preferans.scorer.domain.Bid
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.GameState
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.SeatId
import com.preferans.scorer.ui.WithCurrentLocale
import com.preferans.scorer.ui.localizedDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsSheet(
    game: GameState,
    seat: SeatId,
    onDismiss: () -> Unit,
    onRename: (SeatId, String) -> Unit,
    onAddHandAsDeclarer: (SeatId) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val originalName = game.config.nameOf(seat)
    var editedName by remember(seat) { mutableStateOf(originalName) }
    val nameDirty = editedName.isNotBlank() && editedName != originalName
    val score = game.sheet.scores[seat]
    val playerHands = remember(game, seat) { hansInvolving(game.hands, seat) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
      WithCurrentLocale {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Name + inline save
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (nameDirty) onRename(seat, editedName.trim())
                    },
                    enabled = nameDirty,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.cd_save_name),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stats summary cards
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    stringResource(R.string.stat_pulja),
                    "${score?.bullet ?: 0}",
                    subtitle = "/ ${game.config.bulletTarget}",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    stringResource(R.string.stat_gora),
                    "${score?.mountain ?: 0}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    stringResource(R.string.stat_whist),
                    "${score?.totalWhist() ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Quick action: add hand with this player as declarer.
            // Also auto-saves an edited name if the user didn't tap the check icon.
            Button(
                onClick = {
                    if (nameDirty) onRename(seat, editedName.trim())
                    onAddHandAsDeclarer(seat)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        R.string.record_hand_as_fmt,
                        editedName.ifBlank { originalName },
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Hand history filtered to this player
            Text(
                pluralStringResource(R.plurals.hands_involving, playerHands.size, playerHands.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (playerHands.isEmpty()) {
                Text(
                    stringResource(R.string.no_hands_for_player),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (h in playerHands) {
                        HandSummaryLine(h, seat, game.config)
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HandSummaryLine(h: Hand, seat: SeatId, config: GameConfig) {
    val summary = when (h) {
        is Hand.Played -> {
            val role = if (h.declarerSeat == seat) {
                stringResource(R.string.role_declarer_fmt, h.declarerTricks)
            } else {
                val opp = h.opponents.firstOrNull { it.seat == seat }
                val roleWord = when (opp?.choice) {
                    com.preferans.scorer.domain.WhistChoice.WHIST -> stringResource(R.string.role_whisted)
                    com.preferans.scorer.domain.WhistChoice.HALF_WHIST -> stringResource(R.string.role_half_whisted)
                    com.preferans.scorer.domain.WhistChoice.PASS, null -> stringResource(R.string.role_passed)
                }
                stringResource(R.string.role_other_fmt, roleWord, opp?.tricks ?: 0)
            }
            stringResource(
                R.string.hand_summary_played_fmt,
                h.handNumber,
                h.bid.localizedDisplayName(),
                config.nameOf(h.declarerSeat),
                role,
            )
        }
        is Hand.Raspasovka -> {
            stringResource(
                R.string.hand_summary_raspasovka_fmt,
                h.handNumber,
                h.tricksBySeat[seat] ?: 0,
            )
        }
    }
    Text(summary, style = MaterialTheme.typography.bodySmall)
}

private fun hansInvolving(hands: List<Hand>, seat: SeatId): List<Hand> =
    hands.filter { h ->
        when (h) {
            is Hand.Played -> h.declarerSeat == seat || h.opponents.any { it.seat == seat }
            is Hand.Raspasovka -> h.tricksBySeat.containsKey(seat)
        }
    }
