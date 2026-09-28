package com.mhma.smartlearn.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    const val OVERVIEW = "overview"
    const val COURSES = "courses"
    const val DOWNLOADS = "downloads"

    const val COURSE = "course/{courseId}"
    fun course(courseId: Long) = "course/$courseId"

    const val WEB = "web?url={url}"
    fun web(url: String) = "web?url=${Uri.encode(url)}"
}

enum class HomeTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Overview(Routes.OVERVIEW, "Today", Icons.Outlined.Today, Icons.Filled.Today),
    Courses(Routes.COURSES, "Courses", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
    Downloads(Routes.DOWNLOADS, "Downloads", Icons.Outlined.Download, Icons.Filled.Download),
}
