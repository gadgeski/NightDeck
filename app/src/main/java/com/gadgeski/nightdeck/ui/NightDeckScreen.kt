package com.gadgeski.nightdeck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gadgeski.nightdeck.data.AppEntry
import com.gadgeski.nightdeck.data.AppIconCache
import com.gadgeski.nightdeck.data.FavoritesStore
import com.gadgeski.nightdeck.data.SystemStatus
import com.gadgeski.nightdeck.ui.theme.NightDeckTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val RowHeightDp = 42.dp
private val IconSizeDp = 18.dp
private val TapInvertDuration = 80.milliseconds

@Composable
fun NightDeckScreen(
    viewModel: NightDeckViewModel,
    listState: LazyListState = rememberLazyListState()
) {
    val colors = NightDeckTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // タップ時の80ms反転表示対象行ID
    var flashingRowId by remember { mutableStateOf<String?>(null) }

    // 戻るキーでランチャーが閉じないように制御
    BackHandler(enabled = true) {
        when {
            state.menuTargetApp != null -> viewModel.closeMenu()
            state.searchQuery.isNotEmpty() -> {
                viewModel.clearSearch()
                focusManager.clearFocus()
            }
            else -> {
                // ルート画面では戻るキーを消費してランチャーを維持
                focusManager.clearFocus()
            }
        }
    }

    // 検索入力が変わったらリスト先頭へ戻す
    LaunchedEffect(state.searchQuery) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    // 行タップ処理：選択移動 → 80ms反転表示 → アプリ起動
    val onAppTap: (String, AppEntry) -> Unit = remember(viewModel, coroutineScope) {
        { rowId, app ->
            viewModel.selectRow(rowId)
            focusManager.clearFocus()
            coroutineScope.launch {
                flashingRowId = rowId
                delay(TapInvertDuration)
                flashingRowId = null
                viewModel.launchApp(app)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 上部ステータスバー（時刻・日付・バッテリー残量）
            ConsoleStatusHeader(status = state.systemStatus)

            HairlineDivider()

            // インクリメンタル検索バー
            ConsoleSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = {
                    viewModel.clearSearch()
                    focusManager.clearFocus()
                },
                onSubmit = {
                    // Enter/Done押下で現在選択中または先頭のアプリを起動
                    val target = state.filteredApps.firstOrNull { "app:${it.key}" == state.selectedRowId }
                        ?: state.pinnedApps.firstOrNull { "pin:${it.key}" == state.selectedRowId }
                        ?: state.filteredApps.firstOrNull()
                    if (target != null) {
                        val rowId = state.selectedRowId ?: "app:${target.key}"
                        onAppTap(rowId, target)
                    }
                }
            )

            HairlineDivider()

            // アプリ一覧（左側にスプリング＆スクロール慣性付き「▸」カーソルを重ねて描画）
            AppConsoleList(
                state = state,
                listState = listState,
                iconCache = viewModel.iconCache,
                flashingRowId = flashingRowId,
                onTap = onAppTap,
                onLongPress = { rowId, app ->
                    viewModel.selectRow(rowId)
                    focusManager.clearFocus()
                    viewModel.openMenu(app)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 長押し時のコンソールアクションメニュー
        val menuApp = state.menuTargetApp
        if (menuApp != null) {
            AppActionOverlay(
                app = menuApp,
                isPinned = menuApp.key in state.pinnedKeySet,
                isPinFull = state.pinnedKeySet.size >= FavoritesStore.MAX_FAVORITES,
                onTogglePin = { viewModel.toggleFavorite(menuApp) },
                onAppInfo = { viewModel.openAppInfo(menuApp) },
                onUninstall = { viewModel.requestUninstall(menuApp) },
                onDismiss = viewModel::closeMenu
            )
        }
    }
}

@Composable
private fun ConsoleStatusHeader(status: SystemStatus) {
    val colors = NightDeckTheme.colors
    val typography = NightDeckTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            BasicText(
                text = status.timeText,
                style = typography.clock.copy(color = colors.text)
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = status.dateText,
                style = typography.date.copy(color = colors.textMuted)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            BasicText(
                text = "NIGHTDECK",
                style = typography.brandTag.copy(color = colors.textMuted)
            )
            Spacer(modifier = Modifier.height(6.dp))
            val batPrefix = if (status.isCharging) "CHG" else "BAT"
            val batColor = if (status.isCharging) colors.accent else colors.text
            BasicText(
                text = "$batPrefix ${status.batteryPercent.toString().padStart(3, ' ')}%",
                style = typography.statusMeta.copy(color = batColor)
            )
        }
    }
}

@Composable
private fun ConsoleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    val colors = NightDeckTheme.colors
    val typography = NightDeckTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = ">",
            style = typography.searchPrompt.copy(color = colors.accent)
        )
        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText(
                    text = "FILTER APPS...",
                    style = typography.searchPlaceholder.copy(color = colors.textMuted)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = typography.searchInput.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            BasicText(
                text = "[CLR]",
                style = typography.badge.copy(color = colors.accent),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AppConsoleList(
    state: NightDeckUiState,
    listState: LazyListState,
    iconCache: AppIconCache,
    flashingRowId: String?,
    onTap: (String, AppEntry) -> Unit,
    onLongPress: (String, AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NightDeckTheme.colors
    val typography = NightDeckTheme.typography
    val density = LocalDensity.current
    val cursorHeightPx = with(density) { RowHeightDp.toPx() }
    val iconSizePx = with(density) { IconSizeDp.roundToPx() }
    val isSearching = state.searchQuery.isNotBlank()

    // 選択中行のビューポート内Y座標を算出（スクロール中も毎フレーム更新）
    val targetCursorY by remember(state.selectedRowId) {
        derivedStateOf {
            val selectedId = state.selectedRowId ?: return@derivedStateOf null
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == selectedId }
                ?: return@derivedStateOf null
            itemInfo.offset.toFloat() + (itemInfo.size - cursorHeightPx) / 2f
        }
    }

    // 直近の有効Y座標を保持（画面外に出た際の急激なジャンプを防止）
    var lastValidCursorY by remember { mutableFloatStateOf(0f) }
    if (targetCursorY != null) {
        lastValidCursorY = targetCursorY!!
    }

    // カーソル移動スプリング：
    // ・行選択時は減衰強め＋わずかなオーバーシュートで「吸い付く」感覚（dampingRatio=0.72, stiffness=620）
    // ・スクロール中はわずかに遅れて追従する軽い慣性（dampingRatio=0.86, stiffness=380）
    val animatedCursorY by animateFloatAsState(
        targetValue = lastValidCursorY,
        animationSpec = if (listState.isScrollInProgress) {
            spring(
                dampingRatio = 0.86f,
                stiffness = 380f
            )
        } else {
            spring(
                dampingRatio = 0.72f,
                stiffness = 620f
            )
        },
        label = "cursor_spring_y"
    )

    val cursorAlpha by animateFloatAsState(
        targetValue = if (targetCursorY != null) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cursor_alpha"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "INDEXING PACKAGES...",
                    style = typography.emptyState.copy(color = colors.textMuted)
                )
            }
        } else if (state.filteredApps.isEmpty() && (!isSearching || state.pinnedApps.isEmpty())) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "NO MATCHING APPS",
                    style = typography.emptyState.copy(color = colors.textMuted)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ピン留めお気に入りセクション（検索未入力時のみ表示、最大6件）
                if (!isSearching && state.pinnedApps.isNotEmpty()) {
                    item(key = "header_pinned", contentType = "header") {
                        SectionHeader(
                            title = "PINNED",
                            meta = "${state.pinnedApps.size}/${FavoritesStore.MAX_FAVORITES}"
                        )
                    }
                    itemsIndexed(
                        items = state.pinnedApps,
                        key = { _, app -> "pin:${app.key}" },
                        contentType = { _, _ -> "app_row" }
                    ) { index, app ->
                        val rowId = "pin:${app.key}"
                        AppListRow(
                            app = app,
                            iconCache = iconCache,
                            iconSizePx = iconSizePx,
                            isSelected = state.selectedRowId == rowId,
                            isFlashing = flashingRowId == rowId,
                            trailingBadge = "0${index + 1}",
                            onTap = { onTap(rowId, app) },
                            onLongPress = { onLongPress(rowId, app) }
                        )
                    }
                }

                // 全アプリ（または検索結果）セクション
                item(key = "header_apps", contentType = "header") {
                    SectionHeader(
                        title = if (isSearching) "MATCHES" else "APPS",
                        meta = "${state.filteredApps.size}"
                    )
                }

                itemsIndexed(
                    items = state.filteredApps,
                    key = { _, app -> "app:${app.key}" },
                    contentType = { _, _ -> "app_row" }
                ) { _, app ->
                    val rowId = "app:${app.key}"
                    val isPinned = app.key in state.pinnedKeySet
                    AppListRow(
                        app = app,
                        iconCache = iconCache,
                        iconSizePx = iconSizePx,
                        isSelected = state.selectedRowId == rowId,
                        isFlashing = flashingRowId == rowId,
                        trailingBadge = if (isPinned) "*" else null,
                        onTap = { onTap(rowId, app) },
                        onLongPress = { onLongPress(rowId, app) }
                    )
                }
            }

            // 選択行の左に吸い付く「▸」カーソルオーバーレイ
            Box(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .height(RowHeightDp)
                    .graphicsLayer {
                        translationY = animatedCursorY
                    }
                    .alpha(cursorAlpha),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicText(
                    text = "▸",
                    style = typography.cursor.copy(color = colors.accent)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, meta: String) {
    val colors = NightDeckTheme.colors
    val typography = NightDeckTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = typography.sectionHeader.copy(color = colors.textMuted)
        )
        BasicText(
            text = "[$meta]",
            style = typography.sectionMeta.copy(color = colors.textMuted)
        )
    }
}

@Composable
private fun AppListRow(
    app: AppEntry,
    iconCache: AppIconCache,
    iconSizePx: Int,
    isSelected: Boolean,
    isFlashing: Boolean,
    trailingBadge: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val colors = NightDeckTheme.colors
    val typography = NightDeckTheme.typography
    val context = LocalContext.current

    // キャッシュにあれば即座に表示し、なければ非同期でデコード（60fps維持）
    val iconBitmap by produceState(
        initialValue = iconCache.getCached(app.key),
        key1 = app.key
    ) {
        if (value == null) {
            value = iconCache.loadIcon(context, app, iconSizePx)
        }
    }

    // タップ時の80ms反転表示
    val rowBg = if (isFlashing) colors.text else Color.Transparent
    val labelColor = when {
        isFlashing -> colors.background
        isSelected -> colors.text
        else -> colors.textSecondary
    }
    val badgeColor = when {
        isFlashing -> colors.background
        else -> colors.accent
    }
    val labelStyle = if (isSelected || isFlashing) {
        typography.appRowLabelSelected
    } else {
        typography.appRowLabel
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeightDp)
            .background(rowBg)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // デフォルトのリップルを無効化し、80ms反転で手触りを統一
                onClick = onTap,
                onLongClick = onLongPress
            )
            // 左側34dpは「▸」カーソル専用レーン
            .padding(start = 34.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 小さなアプリアイコン（未読み込み時は固定幅の空スペースでレイアウト揺れを防ぐ）
        Box(
            modifier = Modifier.size(IconSizeDp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = iconBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(IconSizeDp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // アプリ名（等幅テキスト）
        BasicText(
            text = app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = labelStyle.copy(color = labelColor),
            modifier = Modifier.weight(1f)
        )

        if (trailingBadge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            BasicText(
                text = trailingBadge,
                style = typography.badge.copy(color = badgeColor)
            )
        }
    }
}

/**
 * 長押し時に表示されるフラットなコンソール操作メニュー。
 */
@Composable
private fun AppActionOverlay(
    app: AppEntry,
    isPinned: Boolean,
    isPinFull: Boolean,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NightDeckTheme.colors
    val typography = NightDeckTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(colors.background)
                .border(1.dp, colors.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // パネル内タップで閉じないようイベントを消費
                )
                .padding(16.dp)
        ) {
            BasicText(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.menuTitle.copy(color = colors.text)
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = app.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.menuSubtitle.copy(color = colors.textMuted)
            )

            Spacer(modifier = Modifier.height(14.dp))
            HairlineDivider()
            Spacer(modifier = Modifier.height(6.dp))

            val pinLabel = when {
                isPinned -> "[1] UNPIN FROM DECK"
                isPinFull -> "[1] PIN TO DECK (FULL 6/6)"
                else -> "[1] PIN TO DECK"
            }
            val pinEnabled = isPinned || !isPinFull

            ActionMenuItem(
                label = pinLabel,
                textColor = if (pinEnabled) colors.accent else colors.textMuted,
                enabled = pinEnabled,
                onClick = onTogglePin
            )
            ActionMenuItem(
                label = "[2] APP INFO",
                textColor = colors.text,
                enabled = true,
                onClick = onAppInfo
            )
            ActionMenuItem(
                label = "[3] UNINSTALL",
                textColor = colors.text,
                enabled = true,
                onClick = onUninstall
            )

            Spacer(modifier = Modifier.height(6.dp))
            HairlineDivider()
            Spacer(modifier = Modifier.height(6.dp))

            ActionMenuItem(
                label = "[ESC] CANCEL",
                textColor = colors.textMuted,
                enabled = true,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun ActionMenuItem(
    label: String,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val typography = NightDeckTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = typography.menuItem.copy(color = textColor)
        )
    }
}

@Composable
private fun HairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NightDeckTheme.colors.hairline)
    )
}