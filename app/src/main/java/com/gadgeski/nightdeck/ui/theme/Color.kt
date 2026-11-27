package com.gadgeski.nightdeck.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// 厳格な3色パレット（グラデーション・グロー・グラスモーフィズム不使用）
val ConsoleBackground = Color(0xFF0B0D10)
val ConsoleText = Color(0xFFC8D0D8)
val ConsoleAccent = Color(0xFF4FD1C5)

/**
 * NightDeckのコンソール配色トークン。
 * 補助色（ミュート文字・区切り線・オーバーレイ背景）はすべて基本3色の透過度のみで表現する。
 */
@Immutable
data class NightDeckColors(
    val background: Color = ConsoleBackground,
    val text: Color = ConsoleText,
    val textSecondary: Color = ConsoleText.copy(alpha = 0.86f),
    val textMuted: Color = ConsoleText.copy(alpha = 0.48f),
    val accent: Color = ConsoleAccent,
    val hairline: Color = ConsoleText.copy(alpha = 0.16f),
    val scrim: Color = ConsoleBackground.copy(alpha = 0.86f)
)

val DefaultNightDeckColors = NightDeckColors()