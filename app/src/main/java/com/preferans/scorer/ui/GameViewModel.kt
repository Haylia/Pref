package com.preferans.scorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.preferans.scorer.PreferansApp
import com.preferans.scorer.data.GameRepository
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.GameState
import com.preferans.scorer.domain.Hand
import kotlinx.coroutines.flow.StateFlow

class GameViewModel(private val repo: GameRepository) : ViewModel() {

    val state: StateFlow<GameState?> = repo.state
    val loaded: StateFlow<Boolean> = repo.loaded

    /** One-shot prefill: NewHandScreen consumes this as the initial declarer. */
    private var pendingDeclarerSeat: Int? = null
    fun setPendingDeclarer(seat: Int?) { pendingDeclarerSeat = seat }
    fun consumePendingDeclarer(): Int? {
        val v = pendingDeclarerSeat
        pendingDeclarerSeat = null
        return v
    }

    fun startNewGame(config: GameConfig, firstDealerSeat: Int) =
        repo.startNewGame(config, firstDealerSeat)

    fun recordHand(hand: Hand) = repo.recordHand(hand)
    fun undoLastHand() = repo.undoLastHand()
    fun renamePlayer(seat: Int, name: String) = repo.renamePlayer(seat, name)
    fun endGame() = repo.endGame()
    fun clearGame() = repo.clearGame()

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GameViewModel(PreferansApp.instance.gameRepository) as T
            }
        }
    }
}
