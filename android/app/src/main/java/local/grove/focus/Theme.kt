package local.grove.focus

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Forest palette; keep in sync with macos/Sources/Grove/GroveApp.swift.
val ForestNight = Color(0.06f, 0.14f, 0.10f)
val Pine = Color(0.11f, 0.26f, 0.17f)
val Moss = Color(0.42f, 0.62f, 0.36f)
val Fern = Color(0.64f, 0.82f, 0.48f)
val Bark = Color(0.55f, 0.40f, 0.27f)
val Mist = Color(0.93f, 0.92f, 0.84f)

@Composable
fun GroveTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Moss, onPrimary = ForestNight, secondary = Fern, onSecondary = ForestNight,
        background = ForestNight, onBackground = Mist, surface = Pine, onSurface = Mist,
        surfaceVariant = Pine, onSurfaceVariant = Mist, outline = Moss,
        secondaryContainer = Moss, onSecondaryContainer = ForestNight,
    ),
    content = content,
)
