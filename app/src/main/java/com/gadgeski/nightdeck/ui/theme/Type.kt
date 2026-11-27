package com.gadgeski.nightdeck.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * コンソール全体で使用する等幅フォント定義。
 * JetBrains Mono / IBM Plex Mono 等の同梱フォント（SIL OFL）へ差し替える場合は、
 * app/src/main/res/font/ に .ttf を配置し、以下のように変更する：
 *
 * val ConsoleFontFamily = FontFamily(
 *     Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
 *     Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
 *     Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
 *     Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
 * )
 */
val ConsoleFontFamily: FontFamily = FontFamily.Monospace

/**
 * NightDeckのタイポグラフィトークン。
 */
@Immutable
data class NightDeckTypography(
    val fontFamily: FontFamily = ConsoleFontFamily,
    val clock: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    ),
    val date: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    ),
    val brandTag: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 10.sp,
        letterSpacing = 2.sp
    ),
    val statusMeta: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp
    ),
    val searchPrompt: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    ),
    val searchInput: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    val searchPlaceholder: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp
    ),
    val sectionHeader: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp
    ),
    val sectionMeta: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp
    ),
    val appRowLabel: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp
    ),
    val appRowLabelSelected: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp
    ),
    val badge: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    ),
    val cursor: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    ),
    val emptyState: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp
    ),
    val menuTitle: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    ),
    val menuSubtitle: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 10.sp
    ),
    val menuItem: TextStyle = TextStyle(
        fontFamily = ConsoleFontFamily,
        fontSize = 13.sp,
        letterSpacing = 0.6.sp
    )
)

val DefaultNightDeckTypography = NightDeckTypography()