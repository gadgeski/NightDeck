package com.gadgeski.nightdeck

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import com.gadgeski.nightdeck.ui.NightDeckScreen
import com.gadgeski.nightdeck.ui.NightDeckViewModel
import com.gadgeski.nightdeck.ui.theme.ConsoleBackground
import com.gadgeski.nightdeck.ui.theme.NightDeckTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NightDeckViewModel by viewModels {
        NightDeckViewModel.Factory(application)
    }

    // ホームボタン再押下時にリストを先頭へ戻すためのトリガー
    private val homeResetSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ステータスバー・ナビゲーションバーを背景色(#0B0D10)で統一
        val consoleBgArgb = ConsoleBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(consoleBgArgb),
            navigationBarStyle = SystemBarStyle.dark(consoleBgArgb)
        )

        setContent {
            NightDeckTheme {
                val listState = rememberLazyListState()

                LaunchedEffect(homeResetSignal.intValue) {
                    if (homeResetSignal.intValue > 0) {
                        listState.animateScrollToItem(0)
                    }
                }

                NightDeckScreen(
                    viewModel = viewModel,
                    listState = listState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // ホーム画面上で再度ホームボタンが押されたら検索・メニューをリセットして先頭へ戻す
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.resetConsole()
            homeResetSignal.intValue += 1
        }
    }
}