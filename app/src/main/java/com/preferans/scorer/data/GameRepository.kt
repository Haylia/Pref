package com.preferans.scorer.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.GameState
import com.preferans.scorer.domain.GameStatus
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.ScoreSheet
import com.preferans.scorer.scoring.ScoringEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class GameRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val key = stringPreferencesKey("current_game_v1")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        scope.launch {
            try {
                // Read from the current DataStore first.
                var raw = context.appDataStore.data.map { it[key] }.first()

                // Fallback: legacy DataStore from older builds. One-shot migration.
                if (raw == null) {
                    val legacy = runCatching {
                        context.legacyGameDataStore.data.map { it[key] }.first()
                    }.getOrNull()
                    if (legacy != null) {
                        raw = legacy
                        // Copy into the new store so future reads skip the legacy path.
                        runCatching {
                            context.appDataStore.edit { it[key] = legacy }
                        }
                    }
                }

                _state.value = raw?.let {
                    runCatching { json.decodeFromString<GameState>(it) }.getOrNull()
                }
            } catch (t: Throwable) {
                android.util.Log.w("GameRepository", "Failed to load saved game", t)
                _state.value = null
            } finally {
                _loaded.value = true
            }
        }
    }

    /**
     * Persists state to memory and disk. Suspends until the DataStore write
     * completes so callers holding the write mutex can serialise updates.
     */
    private suspend fun persist(newState: GameState?) {
        _state.value = newState
        context.appDataStore.edit { prefs: MutablePreferences ->
            if (newState == null) {
                prefs.remove(key)
            } else {
                prefs[key] = json.encodeToString(GameState.serializer(), newState)
            }
        }
    }

    /** Atomic state transformation. Reads, transforms, and persists under a mutex. */
    private fun mutate(transform: (GameState?) -> GameState?) {
        scope.launch {
            writeMutex.withLock {
                val current = _state.value
                val next = transform(current)
                persist(next)
            }
        }
    }

    fun startNewGame(config: GameConfig, firstDealerSeat: Int) = mutate {
        GameState(
            config = config,
            sheet = ScoreSheet.empty(config.seats),
            hands = emptyList(),
            status = GameStatus.ACTIVE,
            nextDealerSeat = firstDealerSeat,
            createdAtMillis = System.currentTimeMillis(),
        )
    }

    fun recordHand(hand: Hand) = mutate { current ->
        if (current == null) return@mutate null
        val afterHand = ScoringEngine.applyHand(current.sheet, hand, current.config)
        val afterAid = ScoringEngine.applyAmericanAid(afterHand, current.config)
        val newHands = current.hands + hand
        val nextDealer = current.rotateDealer()
        val allReached = current.config.seats.all { seat ->
            (afterAid.scores[seat]?.bullet ?: 0) >= current.config.bulletTarget
        }
        val status = if (allReached) GameStatus.COMPLETED else GameStatus.ACTIVE
        current.copy(
            sheet = afterAid,
            hands = newHands,
            nextDealerSeat = nextDealer,
            status = status,
        )
    }

    fun undoLastHand() = mutate { current ->
        if (current == null || current.hands.isEmpty()) return@mutate current
        val withoutLast = current.hands.dropLast(1)
        var sheet = ScoreSheet.empty(current.config.seats)
        for (h in withoutLast) {
            sheet = ScoringEngine.applyHand(sheet, h, current.config)
            sheet = ScoringEngine.applyAmericanAid(sheet, current.config)
        }
        val idx = current.config.seats.indexOf(current.nextDealerSeat)
        val prevDealer = current.config.seats[
            (idx - 1 + current.config.seats.size) % current.config.seats.size
        ]
        current.copy(
            sheet = sheet,
            hands = withoutLast,
            nextDealerSeat = prevDealer,
            status = GameStatus.ACTIVE,
        )
    }

    fun renamePlayer(seat: Int, name: String) = mutate { current ->
        if (current == null) return@mutate null
        val newPlayers = current.config.players.map {
            if (it.seat == seat) it.copy(name = name) else it
        }
        current.copy(config = current.config.copy(players = newPlayers))
    }

    fun endGame() = mutate { current ->
        current?.copy(status = GameStatus.COMPLETED)
    }

    fun clearGame() = mutate { null }
}
