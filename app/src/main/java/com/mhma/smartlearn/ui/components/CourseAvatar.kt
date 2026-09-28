package com.mhma.smartlearn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A fixed set of pleasant colours; each course always gets the same one.
private val palette = listOf(
    Color(0xFF3F51B5), Color(0xFF00897B), Color(0xFF8E24AA), Color(0xFFD81B60),
    Color(0xFF6D4C41), Color(0xFF546E7A), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFF4511E), Color(0xFF5E35B1),
)

@Composable
fun CourseAvatar(
    courseId: Long,
    code: String,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val color = palette[((courseId % palette.size + palette.size) % palette.size).toInt()]
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(code, title),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun initials(code: String, title: String): String {
    // "CS301" -> "CS", "Data Structures" -> "DS"
    val letters = code.takeWhile { it.isLetter() }
    if (letters.length >= 2) return letters.take(3).uppercase()
    val words = title.split(' ', '-', '/').filter { it.isNotBlank() && it[0].isLetterOrDigit() }
    return words.take(2).joinToString("") { it[0].uppercase() }.ifEmpty { "?" }
}
