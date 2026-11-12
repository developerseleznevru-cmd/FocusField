package ru.seleznev.focusfield.service.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.seleznev.focusfield.FocusFieldApp
import ru.seleznev.focusfield.data.model.BlockedItem
import ru.seleznev.focusfield.data.repo.SettingsRepository
import ru.seleznev.focusfield.data.state.BlockingStateStore
import ru.seleznev.focusfield.util.DomainMatcher

/**
 * Сервис специальных возможностей: определяет текущее приложение (и адресную
 * строку браузера) и, при низкой концентрации пользователя или активной сессии,
 * запускает оверлей блокировки. Функция «помехи» даёт временное разрешение.
 */
class BlockingAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsRepository
    private lateinit var state: BlockingStateStore

    private var blockedPackages = emptySet<String>()
    private var blockedDomains = emptySet<String>()

    private var lastEventAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (applicationContext as FocusFieldApp).container
        settings = container.settingsRepository
        state = container.blockingStateStore

        container.blockRepository.observeEnabled()
            .onEach { items ->
                blockedPackages = items.filter { it.type == BlockedItem.TYPE_APP }
                    .mapNotNull { it.packageName }
                    .toSet()
                blockedDomains = items.filter { it.type == BlockedItem.TYPE_SITE }
                    .mapNotNull { it.domain }
                    .toSet()
            }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || SYSTEM_PACKAGES.contains(pkg)) return

        // Дребезг событий: обрабатываем не чаще, чем раз в 300 мс
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventAt < EVENT_DEBOUNCE_MS) return
        lastEventAt = now

        val target = when {
            blockedPackages.contains(pkg) -> BlockTarget(pkg, isSite = false)
            else -> {
                val url = readUrlFromAddressBar(pkg, event) ?: return
                val host = DomainMatcher.extractHost(url) ?: return
                if (blockedDomains.any { DomainMatcher.matches(host, it) }) {
                    BlockTarget(host, isSite = true)
                } else {
                    return
                }
            }
        }

        if (shouldBlock(target.identifier)) launchOverlay(target)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Решение о блокировке: включена ли блокировка, идёт ли сессия,
     * пройден ли порог концентрации, есть ли активная «помеха».
     */
    private fun shouldBlock(target: String): Boolean {
        if (!settings.state.value.blockEnabled) return false
        if (state.isOverrideActive(target)) return false
        val settingsState = settings.state.value

        // Режим расписания
        if (settingsState.scheduleEnabled) {
            val inSchedule = ScheduleWindow.isWithin(
                ru.seleznev.focusfield.util.DayMath.minutesOfDay(System.currentTimeMillis()),
                settingsState.scheduleStartMinutes,
                settingsState.scheduleEndMinutes
            )
            if (!inSchedule) return false
        }

        val blockingNow = if (settingsState.blockOnlyInSession) {
            state.sessionActive.value
        } else {
            true
        }
        if (!blockingNow) return false

        // Биометрия может отсутствовать (камера отключена): в сессии блокируем всегда
        val score = state.currentScore.value
        return score == BlockingStateStore.NO_DATA || score <= settingsState.blockThreshold
    }

    private fun launchOverlay(target: BlockTarget) {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        state.incrementInterruptions()
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BlockOverlayActivity.EXTRA_TARGET, target.identifier)
            putExtra(BlockOverlayActivity.EXTRA_IS_SITE, target.isSite)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Оверлей не может быть запущен (нет разрешения) — молча пропускаем
        }
    }

    /**
     * Пытается прочитать адресную строку браузера из дерева UI.
     * Работает для известных браузеров (Chrome, Firefox, Яндекс.Браузер).
     */
    private fun readUrlFromAddressBar(pkg: String, event: AccessibilityEvent): String? {
        val knownIds = BROWSER_ADDRESS_BAR_IDS[pkg] ?: return null
        val root = rootInActiveWindow ?: return null
        for (id in knownIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            for (node in nodes) {
                val text = node.text?.toString().orEmpty()
                if (text.isNotBlank()) {
                    return text
                }
            }
        }
        return null
    }

    private data class BlockTarget(val identifier: String, val isSite: Boolean)

    private companion object {
        const val EVENT_DEBOUNCE_MS = 300L
        val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin"
        )
        /** View-ID адресных строк популярных браузеров. */
        val BROWSER_ADDRESS_BAR_IDS = mapOf(
            "com.android.chrome" to listOf("url_bar"),
            "com.chrome.beta" to listOf("url_bar"),
            "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            "com.yandex.browser" to listOf("omnibox_text")
        )
    }
}
