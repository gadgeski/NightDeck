package com.gadgeski.nightdeck.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gadgeski.nightdeck.data.AppEntry
import com.gadgeski.nightdeck.data.AppIconCache
import com.gadgeski.nightdeck.data.AppRepository
import com.gadgeski.nightdeck.data.FavoritesStore
import com.gadgeski.nightdeck.data.SystemStatus
import com.gadgeski.nightdeck.data.observeSystemStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 画面全体のUI状態。
 */
@Immutable
data class NightDeckUiState(
    val systemStatus: SystemStatus = SystemStatus("--:--", "---- -- --", 0, false),
    val searchQuery: String = "",
    val pinnedApps: List<AppEntry> = emptyList(),
    val filteredApps: List<AppEntry> = emptyList(),
    val pinnedKeySet: Set<String> = emptySet(),
    val totalAppCount: Int = 0,
    val selectedRowId: String? = null,
    val menuTargetApp: AppEntry? = null,
    val isLoading: Boolean = true
)

class NightDeckViewModel(
    private val application: Application,
    private val repository: AppRepository,
    private val favoritesStore: FavoritesStore,
    val iconCache: AppIconCache
) : ViewModel() {

    private val allAppsFlow = MutableStateFlow<List<AppEntry>>(emptyList())
    private val searchQueryFlow = MutableStateFlow("")
    private val selectedRowIdFlow = MutableStateFlow<String?>(null)
    private val menuTargetFlow = MutableStateFlow<AppEntry?>(null)
    private val isLoadingFlow = MutableStateFlow(true)

    private val systemStatusFlow = observeSystemStatus(application).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SystemStatus("--:--", "---- -- --", 0, false)
    )

    // 検索クエリ・アプリ一覧・お気に入りを合成してUI状態を生成
    val uiState: StateFlow<NightDeckUiState> = combine(
        systemStatusFlow,
        allAppsFlow,
        favoritesStore.pinnedKeysFlow,
        searchQueryFlow,
        selectedRowIdFlow,
        menuTargetFlow,
        isLoadingFlow
    ) { values ->
        val status = values[0] as SystemStatus
        @Suppress("UNCHECKED_CAST")
        val allApps = values[1] as List<AppEntry>
        @Suppress("UNCHECKED_CAST")
        val pinnedKeys = values[2] as List<String>
        val query = values[3] as String
        val selectedId = values[4] as String?
        val menuTarget = values[5] as AppEntry?
        val loading = values[6] as Boolean

        val appByKey = allApps.associateBy { it.key }
        val pinnedApps = pinnedKeys.mapNotNull { appByKey[it] }
        val pinnedKeySet = pinnedKeys.toSet()

        val trimmedQuery = query.trim().lowercase(Locale.ROOT)
        val filteredApps = if (trimmedQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.normalizedLabel.contains(trimmedQuery) }
        }

        // 選択中の行IDが存在しない場合は先頭行に自動フォールバック
        val validRowIds = buildSet {
            if (trimmedQuery.isEmpty()) {
                pinnedApps.forEach { add("pin:${it.key}") }
            }
            filteredApps.forEach { add("app:${it.key}") }
        }
        val resolvedSelectedId = when {
            selectedId != null && selectedId in validRowIds -> selectedId
            trimmedQuery.isEmpty() && pinnedApps.isNotEmpty() -> "pin:${pinnedApps.first().key}"
            filteredApps.isNotEmpty() -> "app:${filteredApps.first().key}"
            else -> null
        }

        NightDeckUiState(
            systemStatus = status,
            searchQuery = query,
            pinnedApps = pinnedApps,
            filteredApps = filteredApps,
            pinnedKeySet = pinnedKeySet,
            totalAppCount = allApps.size,
            selectedRowId = resolvedSelectedId,
            menuTargetApp = menuTarget,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NightDeckUiState()
    )

    private var refreshJob: Job? = null

    init {
        refreshApps()
        observePackageUpdates()
    }

    /** インストール済みアプリ一覧を再取得する */
    fun refreshApps() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val apps = repository.loadInstalledApps()
            allAppsFlow.value = apps
            isLoadingFlow.value = false
            favoritesStore.pruneMissing(apps.map { it.key }.toSet())
        }
    }

    /** パッケージの追加・削除・変更を監視して一覧とキャッシュを更新 */
    private fun observePackageUpdates() {
        viewModelScope.launch {
            repository.observePackageChanges().collect {
                iconCache.clear()
                refreshApps()
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQueryFlow.value = newQuery
    }

    fun clearSearch() {
        searchQueryFlow.value = ""
    }

    fun selectRow(rowId: String) {
        selectedRowIdFlow.value = rowId
    }

    fun openMenu(app: AppEntry) {
        menuTargetFlow.value = app
    }

    fun closeMenu() {
        menuTargetFlow.value = null
    }

    /** ホームボタン再押下時などに検索・メニューをリセット */
    fun resetConsole() {
        searchQueryFlow.value = ""
        menuTargetFlow.value = null
    }

    fun toggleFavorite(app: AppEntry) {
        viewModelScope.launch {
            favoritesStore.toggleFavorite(app.key)
            menuTargetFlow.value = null
        }
    }

    /** アプリを起動する */
    fun launchApp(app: AppEntry) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            application.startActivity(intent)
        } catch (_: Exception) {
            // フォールバック：パッケージ単位の起動インテントを試行
            try {
                val fallback = application.packageManager.getLaunchIntentForPackage(app.packageName)
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    application.startActivity(fallback)
                }
            } catch (_: Exception) {
                // 起動不可アプリの場合は安全に無視
            }
        }
    }

    /** Android標準のアプリ情報画面を開く */
    fun openAppInfo(app: AppEntry) {
        menuTargetFlow.value = null
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${app.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            application.startActivity(intent)
        } catch (_: Exception) {
            // 無視
        }
    }

    /** Android標準のアンインストール確認画面を開く */
    fun requestUninstall(app: AppEntry) {
        menuTargetFlow.value = null
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = "package:${app.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            application.startActivity(intent)
        } catch (_: Exception) {
            // 無視
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NightDeckViewModel(
                application = application,
                repository = AppRepository(application),
                favoritesStore = FavoritesStore(application),
                iconCache = AppIconCache()
            ) as T
        }
    }
}