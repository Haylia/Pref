package com.preferans.scorer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.preferans.scorer.domain.GameStatus
import com.preferans.scorer.ui.hand.NewHandScreen
import com.preferans.scorer.ui.scoresheet.ScoresheetScreen
import com.preferans.scorer.ui.settlement.SettlementScreen
import com.preferans.scorer.ui.setup.SetupScreen

private object Routes {
    const val SCORESHEET = "scoresheet"
    const val NEW_HAND = "new_hand"
}

@Composable
fun PreferansApp() {
    val vm: GameViewModel = viewModel(factory = GameViewModel.Factory)
    val state by vm.state.collectAsState()
    val loaded by vm.loaded.collectAsState()

    when {
        !loaded -> Splash()
        state == null -> SetupScreen(
            onStart = { config, firstDealer -> vm.startNewGame(config, firstDealer) },
        )
        state!!.status == GameStatus.COMPLETED -> SettlementScreen(
            vm = vm,
            onNewGame = { vm.clearGame() },
        )
        else -> ActiveGameNav(vm)
    }
}

@Composable
private fun ActiveGameNav(vm: GameViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.SCORESHEET) {
        composable(Routes.SCORESHEET) {
            ScoresheetScreen(
                vm = vm,
                onAddHand = { nav.navigate(Routes.NEW_HAND) },
                onSettle = { vm.endGame() },
                onNewGame = { vm.clearGame() },
            )
        }
        composable(Routes.NEW_HAND) {
            NewHandScreen(
                vm = vm,
                onClose = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun Splash() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
