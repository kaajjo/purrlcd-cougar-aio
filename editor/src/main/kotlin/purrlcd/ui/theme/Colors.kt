package purrlcd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme

/** Warm neutral surfaces and peach accents, shared by native M3 and editor components. */
val EditorColorScheme = darkColorScheme(
    primary = Color(0xFFFFB787), onPrimary = Color(0xFF502400),
    primaryContainer = Color(0xFF713711), onPrimaryContainer = Color(0xFFFFDCC5),
    secondary = Color(0xFFE4BFA8), onSecondary = Color(0xFF422B1C),
    secondaryContainer = Color(0xFF594131), onSecondaryContainer = Color(0xFFFFDCC5),
    tertiary = Color(0xFFB8CEA4), onTertiary = Color(0xFF253518),
    tertiaryContainer = Color(0xFF3B4C2D), onTertiaryContainer = Color(0xFFD4EABE),
    background = Color(0xFF141210), onBackground = Color(0xFFECE0D9),
    surface = Color(0xFF141210), onSurface = Color(0xFFECE0D9),
    surfaceVariant = Color(0xFF51443B), onSurfaceVariant = Color(0xFFD6C3B7),
    surfaceDim = Color(0xFF141210), surfaceBright = Color(0xFF3D3834),
    surfaceContainerLowest = Color(0xFF0F0D0C), surfaceContainerLow = Color(0xFF1D1916),
    surfaceContainer = Color(0xFF231F1C), surfaceContainerHigh = Color(0xFF2E2926),
    surfaceContainerHighest = Color(0xFF393430),
    outline = Color(0xFF9E8E83), outlineVariant = Color(0xFF51443B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFECE0D9), inverseOnSurface = Color(0xFF362F2B),
    inversePrimary = Color(0xFF914D24), surfaceTint = Color(0xFFFFB787)
)

val Bg = EditorColorScheme.background
val Panel = EditorColorScheme.surfaceContainerLow
val Raised = EditorColorScheme.surfaceContainerHigh
val Line = EditorColorScheme.outlineVariant
val Ink = EditorColorScheme.onSurface
val Muted = EditorColorScheme.onSurfaceVariant
val Orange = EditorColorScheme.primary
val Good = Color(0xFF87C9AA)

val SubtleInk = EditorColorScheme.onSurfaceVariant
val FaintInk = EditorColorScheme.onSurfaceVariant
val WarningSurface = EditorColorScheme.primaryContainer
val SidebarSurface = EditorColorScheme.surfaceContainer
val PreviewBorder = Color(0xFF363C47)
val PreviewBackground = Color.Black
val SelectedBorder = Orange.copy(alpha = .5f)

val SceneColorPresets = listOf("#FFFFFF", "#FF9B54", "#87C9AA", "#88B9EE", "#111318", "#000000")
