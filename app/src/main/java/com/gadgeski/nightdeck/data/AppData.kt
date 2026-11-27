package com.gadgeski.nightdeck.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// DataStoreのシングルトン定義
private val Context.nightDeckDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nightdeck_preferences"
)

/**
 * インストール済みアプリ1件を表すイミュータブルなモデル。
 * Composeのスキップ最適化のため @Immutable を付与。
 */
@Immutable
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val normalizedLabel: String
) {
    /** 一意識別子（同一パッケージに複数ランチャーActivityがある場合にも対応） */
    val key: String get() = "$packageName/$activityName"
}

/**
 * 上部コンソールヘッダー用のシステムステータスモデル。
 */
@Immutable
data class SystemStatus(
    val timeText: String,
    val dateText: String,
    val batteryPercent: Int,
    val isCharging: Boolean
)

/**
 * お気に入り（最大6件）を順序付きでDataStoreに保存・読み込みするストア。
 */
class FavoritesStore(private val context: Context) {

    companion object {
        const val MAX_FAVORITES = 6
        private val KEY_PINNED_APPS = stringPreferencesKey("pinned_app_keys")
        private const val DELIMITER = "\n"
    }

    /** ピン留めされたアプリのkeyリストを順序通りに流すFlow */
    val pinnedKeysFlow: Flow<List<String>> = context.nightDeckDataStore.data.map { prefs ->
        val raw = prefs[KEY_PINNED_APPS].orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(DELIMITER)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_FAVORITES)
        }
    }

    /**
     * 指定キーのお気に入り登録をトグルする。
     * 上限(6件)に達している場合は追加せず false を返す。
     */
    suspend fun toggleFavorite(appKey: String): Boolean {
        var changed = false
        context.nightDeckDataStore.edit { prefs ->
            val current = prefs[KEY_PINNED_APPS]
                .orEmpty()
                .split(DELIMITER)
                .filter { it.isNotBlank() }
                .toMutableList()

            if (current.contains(appKey)) {
                current.remove(appKey)
                changed = true
            } else if (current.size < MAX_FAVORITES) {
                current.add(appKey)
                changed = true
            }

            if (changed) {
                prefs[KEY_PINNED_APPS] = current.joinToString(DELIMITER)
            }
        }
        return changed
    }

    /** アンインストールされたアプリがお気に入りに残っていた場合に整理する */
    suspend fun pruneMissing(validKeys: Set<String>) {
        context.nightDeckDataStore.edit { prefs ->
            val current = prefs[KEY_PINNED_APPS]
                .orEmpty()
                .split(DELIMITER)
                .filter { it.isNotBlank() }
            val filtered = current.filter { it in validKeys }
            if (filtered.size != current.size) {
                prefs[KEY_PINNED_APPS] = filtered.joinToString(DELIMITER)
            }
        }
    }
}

/**
 * アプリアイコンの非同期読み込みとメモリキャッシュ（LruCache）を管理するクラス。
 * メインスレッドでのデコードを避け、200個以上のアプリでも60fpsを維持する。
 */
class AppIconCache(maxEntries: Int = 300) {

    private val cache = object : LruCache<String, ImageBitmap>(maxEntries) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }

    /** 同期キャッシュ参照（Compose描画時のちらつき防止） */
    fun getCached(key: String): ImageBitmap? = cache.get(key)

    /**
     * キャッシュになければ Dispatchers.IO 上でアイコンを読み込み、
     * 指定ピクセルサイズに縮小してメモリキャッシュへ格納する。
     */
    suspend fun loadIcon(
        context: Context,
        entry: AppEntry,
        sizePx: Int
    ): ImageBitmap? {
        cache.get(entry.key)?.let { return it }

        return withContext(Dispatchers.IO) {
            cache.get(entry.key)?.let { return@withContext it }

            try {
                val pm = context.packageManager
                val component = ComponentName(entry.packageName, entry.activityName)
                val drawable = pm.getActivityIcon(component)

                val safeSize = sizePx.coerceAtLeast(16)
                val bitmap = createBitmap(safeSize, safeSize)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, safeSize, safeSize)
                drawable.draw(canvas)

                val imageBitmap = bitmap.asImageBitmap()
                cache.put(entry.key, imageBitmap)
                imageBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    /** パッケージ更新・削除時にキャッシュを破棄する */
    fun clear() {
        cache.evictAll()
    }
}

/**
 * インストール済みアプリの取得と、インストール/削除イベントの監視を担当するリポジトリ。
 */
class AppRepository(private val context: Context) {

    /**
     * ランチャー起動可能な全アプリを非同期で取得し、ラベル順にソートして返す。
     * AndroidManifestの <queries> に基づいて取得される。
     */
    suspend fun loadInstalledApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val selfPackage = context.packageName
        val seenKeys = HashSet<String>(resolveInfos.size)

        resolveInfos.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val pkg = activityInfo.packageName ?: return@mapNotNull null
            if (pkg == selfPackage) return@mapNotNull null

            val act = activityInfo.name ?: return@mapNotNull null
            val key = "$pkg/$act"
            if (!seenKeys.add(key)) return@mapNotNull null

            val label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
            AppEntry(
                packageName = pkg,
                activityName = act,
                label = label,
                normalizedLabel = label.lowercase(Locale.ROOT)
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * アプリのインストール・削除・更新・有効/無効化を検知してイベントを流すFlow。
     */
    fun observePackageChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // 既に解除済みの場合は無視
            }
        }
    }
}

/**
 * 時刻・日付・バッテリー残量をイベント駆動（ポーリングなし）で監視するFlow。
 */
fun observeSystemStatus(context: Context): Flow<SystemStatus> = callbackFlow {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.US)

    fun readBattery(batteryIntent: Intent?): Pair<Int, Boolean> {
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    fun buildStatus(batteryIntent: Intent?): SystemStatus {
        val now = ZonedDateTime.now()
        val (percent, charging) = readBattery(batteryIntent)
        return SystemStatus(
            timeText = now.format(timeFormatter),
            dateText = now.format(dateFormatter).uppercase(Locale.US),
            batteryPercent = percent,
            isCharging = charging
        )
    }

    // 初回値（スティッキーブロードキャストから現在のバッテリー状態を取得）
    var lastBatteryIntent: Intent? = ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_EXPORTED
    )
    trySend(buildStatus(lastBatteryIntent))

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                lastBatteryIntent = intent
            }
            trySend(buildStatus(lastBatteryIntent))
        }
    }

    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_TICK)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_BATTERY_CHANGED)
    }

    ContextCompat.registerReceiver(
        context,
        receiver,
        filter,
        ContextCompat.RECEIVER_EXPORTED
    )

    awaitClose {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // 既に解除済みの場合は無視
        }
    }
}