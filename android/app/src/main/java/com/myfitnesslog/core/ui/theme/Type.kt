package com.myfitnesslog.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Modern & energetic type scale. Built on Material 3 defaults, with weightier,
// tighter headline/title styles so screen titles and section headers carry more
// presence. Screens read from MaterialTheme.typography, so tuning here restyles
// the whole app without touching call sites.
private val default = Typography()

val MyFitnessLogTypography = Typography(
    // Large screen titles (e.g. "Home", "Workout").
    headlineMedium = default.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = default.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),
    // Card / section headers.
    titleLarge = default.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = default.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    // Empty-state headings and emphasis.
    bodyLarge = default.bodyLarge.copy(
        lineHeight = 24.sp,
    ),
    // Buttons and chips.
    labelLarge = default.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    // Explicit heavy display style for hero numbers/streaks if needed later.
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
)
