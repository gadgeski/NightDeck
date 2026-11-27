package com.gadgeski.nightdeck.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalNightDeckColors = staticCompositionLocalOf { DefaultNightDeckColors }
private val LocalNightDeckTypography = staticCompositionLocalOf { DefaultNightDeckTypography }

/**
 * Material非依存のカスタムテーマプロバイダー。
 * 画面内の任意のComposableから NightDeckTheme.colors / NightDeckTheme.typography で参照できる。
 */
@Composable
fun NightDeckTheme(
    colors: NightDeckColors = DefaultNightDeckColors,
    typography: NightDeckTypography = DefaultNightDeckTypography,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalNightDeckColors provides colors,
        LocalNightDeckTypography provides typography,
        content = content
    )
}

object NightDeckTheme {
    val colors: NightDeckColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNightDeckColors.current

    val typography: NightDeckTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalNightDeckTypography.current
}