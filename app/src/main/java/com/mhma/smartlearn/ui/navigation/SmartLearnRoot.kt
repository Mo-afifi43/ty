package com.mhma.smartlearn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mhma.smartlearn.data.network.SessionState
import com.mhma.smartlearn.ui.login.LoginScreen

/**
 * Top of the tree. Decides between the sign-in page and the app proper based on the session,
 * so no screen has to know how to log out.
 */
@Composable
fun SmartLearnRoot(viewModel: RootViewModel = hiltViewModel()) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val startDestination = remember {
        if (session == SessionState.SIGNED_OUT) Routes.LOGIN else Routes.HOME
    }

    LaunchedEffect(session) {
        val current = navController.currentBackStackEntry?.destination?.route
        when (session) {
            SessionState.SIGNED_OUT -> if (current != Routes.LOGIN) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            SessionState.ACTIVE -> if (current == Routes.LOGIN) {
                // Came back from a re-login: return to where we were. Fresh login: open home.
                if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            SessionState.EXPIRED -> Unit // the banner inside Home handles it
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            // First sign-in has nowhere to go back to; a re-login can be dismissed.
            val onClose: (() -> Unit)? = if (session == SessionState.SIGNED_OUT) {
                null
            } else {
                { navController.popBackStack() }
            }
            LoginScreen(onClose = onClose)
        }
        composable(Routes.HOME) {
            HomeScreen(
                sessionExpired = session == SessionState.EXPIRED,
                onSignInAgain = {
                    navController.navigate(Routes.LOGIN) { launchSingleTop = true }
                },
            )
        }
    }
}
