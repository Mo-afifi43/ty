package com.mhma.smartlearn.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mhma.smartlearn.ui.components.SessionExpiredBanner
import com.mhma.smartlearn.ui.courses.CourseDetailScreen
import com.mhma.smartlearn.ui.courses.CoursesScreen
import com.mhma.smartlearn.ui.downloads.DownloadsScreen
import com.mhma.smartlearn.ui.overview.OverviewScreen
import com.mhma.smartlearn.ui.web.WebScreen

/** The three tabs plus the course and web pages that sit on top of them. */
@Composable
fun HomeScreen(
    sessionExpired: Boolean,
    onSignInAgain: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = currentRoute == null || HomeTab.entries.any { it.route == currentRoute }
    val onWeb = currentRoute == Routes.WEB

    Scaffold(
        // Inner screens have their own top bars; let them handle the status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                if (sessionExpired && !onWeb) {
                    SessionExpiredBanner(onSignIn = onSignInAgain)
                }
                if (onTab) {
                    NavigationBar {
                        HomeTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.OVERVIEW,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    onOpenCourse = { id -> navController.navigate(Routes.course(id)) },
                    onOpenLink = { url -> navController.navigate(Routes.web(url)) },
                    onSeeAllCourses = {
                        navController.navigate(Routes.COURSES) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.COURSES) {
                CoursesScreen(
                    onOpenCourse = { id -> navController.navigate(Routes.course(id)) },
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen()
            }
            composable(
                route = Routes.COURSE,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType }),
            ) {
                CourseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLink = { url -> navController.navigate(Routes.web(url)) },
                )
            }
            composable(
                route = Routes.WEB,
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { entry ->
                WebScreen(
                    url = entry.arguments?.getString("url").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
