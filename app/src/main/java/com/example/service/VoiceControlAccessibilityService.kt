package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.voice.VoiceEngineStateHolder

class VoiceControlAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var trackedSpeedIndex = 3 // 0=0.25, 1=0.5, 2=0.75, 3=Normal(1x), 4=1.25, 5=1.5, 6=1.75, 7=2.0

    companion object {
        @Volatile
        var instance: VoiceControlAccessibilityService? = null
            private set

        fun isEnabledInSettings(context: Context): Boolean {
            val expectedComponent = ComponentName(context, VoiceControlAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = enabledServices.split(":")
            for (componentString in colonSplitter) {
                val enabledComponent = ComponentName.unflattenFromString(componentString)
                if (enabledComponent == expectedComponent) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        VoiceEngineStateHolder.updateAccessibilityStatus(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                VoiceEngineStateHolder.updateForegroundPackage(pkg)
            }
        }
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        VoiceEngineStateHolder.updateAccessibilityStatus(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        VoiceEngineStateHolder.updateAccessibilityStatus(false)
        super.onDestroy()
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun lockScreen(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun takeScreenshotAction(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    fun readCurrentAppLabel(): String {
        val pkg = rootInActiveWindow?.packageName?.toString()
            ?: VoiceEngineStateHolder.state.value.currentForegroundApp
            ?: return ""
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    fun clickElementByText(query: String): String? {
        val root = rootInActiveWindow ?: return null
        val normQuery = com.example.voice.RussianVoiceCommandParser.normalize(query)
        if (normQuery.isBlank()) return null

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        val candidates = allNodes.filter { node ->
            if (!node.isVisibleToUser) return@filter false
            val label = nodeLabel(node)
            label.isNotBlank() && (label == normQuery || label.contains(normQuery) || normQuery.contains(label) && label.length >= 3)
        }

        for (node in candidates) {
            val clickable = findClickableAncestorOrSelf(node)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                val spokenLabel = node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                    ?: query
                return spokenLabel
            }
        }
        return null
    }

    fun tapWhatsAppVideoCallIfVisible() {
        fun tryVideo(): Boolean {
            val root = rootInActiveWindow ?: return false
            val pkg = root.packageName?.toString().orEmpty()
            if (!pkg.contains("whatsapp", ignoreCase = true)) return false
            return clickNodeMatchingKeywords(
                listOf("видеозвонок", "видеовызов", "video call", "видео")
            )
        }
        mainHandler.postDelayed({
            if (!tryVideo()) {
                mainHandler.postDelayed({ tryVideo() }, 950L)
            }
        }, 750L)
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            val scrollable = findScrollableNode(root)
            if (scrollable != null) {
                val ok = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (ok) return true
            }
        }
        return performSwipeGesture(upward = true)
    }

    fun scrollBackward(): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            val scrollable = findScrollableNode(root)
            if (scrollable != null) {
                val ok = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                if (ok) return true
            }
        }
        return performSwipeGesture(upward = false)
    }

    fun clickFirstVideoResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectClickableCandidates(root, candidates)

        val rect = Rect()
        val best = candidates.firstOrNull { node ->
            node.getBoundsInScreen(rect)
            val text = nodeLabel(node)
            val isTopBar = rect.top < 220
            val isBigEnough = rect.width() > 240 && rect.height() > 110
            val looksLikeNav = text.contains("поиск") ||
                text.contains("search") ||
                text.contains("назад") ||
                text.contains("фильтры")
            !isTopBar && isBigEnough && !looksLikeNav
        }

        return best?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    fun tapMediaControlButton(wantPause: Boolean) {
        val keywords = if (wantPause) {
            listOf("приостановить", "пауза", "pause")
        } else {
            listOf("воспроизвести", "смотреть", "play", "продолжить")
        }

        if (clickNodeMatchingKeywords(keywords)) return

        // Reveal player overlay controls by tapping player area, then retry
        tapPlayerArea {
            mainHandler.postDelayed({
                clickNodeMatchingKeywords(keywords)
            }, 350L)
        }
    }

    fun adjustYouTubePlaybackSpeed(faster: Boolean, onCompleted: (Boolean, String) -> Unit) {
        val gearKeywords = listOf(
            "дополнительные настройки",
            "настройки видео",
            "настройки воспроизведения",
            "другие параметры",
            "настройки",
            "playback settings",
            "more options",
            "settings"
        )

        fun proceedFromGear() {
            val clickedGear = clickNodeMatchingKeywords(gearKeywords, preferTopHalf = true)
            if (!clickedGear) {
                onCompleted(false, "Откройте видео на YouTube, чтобы изменить скорость")
                return
            }

            mainHandler.postDelayed({
                val clickedSpeedMenu = clickNodeMatchingKeywords(
                    listOf("скорость воспроизведения", "скорость", "playback speed")
                )
                if (!clickedSpeedMenu) {
                    onCompleted(false, "Не найден пункт «Скорость воспроизведения»")
                    return@postDelayed
                }

                mainHandler.postDelayed({
                    val resultMsg = selectAdjacentSpeedOption(faster)
                    if (resultMsg != null) {
                        onCompleted(true, resultMsg)
                    } else {
                        onCompleted(false, "Не удалось выбрать скорость в списке")
                    }
                }, 480L)
            }, 480L)
        }

        if (findNodeMatchingKeywords(gearKeywords, preferTopHalf = true) != null) {
            proceedFromGear()
        } else {
            tapPlayerArea {
                mainHandler.postDelayed({ proceedFromGear() }, 380L)
            }
        }
    }

    fun confirmWhatsAppCallIfNeeded() {
        fun tryConfirm(): Boolean {
            val root = rootInActiveWindow ?: return false
            val pkg = root.packageName?.toString().orEmpty()
            if (!pkg.contains("whatsapp", ignoreCase = true)) return false
            return clickNodeMatchingKeywords(
                listOf("позвонить", "аудиозвонок", "голосовой вызов", "voice call", "call")
            )
        }

        mainHandler.postDelayed({
            if (!tryConfirm()) {
                mainHandler.postDelayed({ tryConfirm() }, 900L)
            }
        }, 700L)
    }

    fun tapWhatsAppSendButtonIfVisible() {
        fun trySend(): Boolean {
            val root = rootInActiveWindow ?: return false
            val pkg = root.packageName?.toString().orEmpty()
            if (!pkg.contains("whatsapp", ignoreCase = true) &&
                !pkg.contains("mms", ignoreCase = true) &&
                !pkg.contains("messaging", ignoreCase = true)
            ) {
                return false
            }
            return clickNodeMatchingKeywords(listOf("отправить", "send"))
        }
        mainHandler.postDelayed({
            if (!trySend()) {
                mainHandler.postDelayed({ trySend() }, 1000L)
            }
        }, 900L)
    }

    fun seekYouTube(forward: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * if (forward) 0.78f else 0.22f
        val y = metrics.heightPixels * 0.22f

        // Two rapid strokes simulate YouTube double-tap to seek +-10 seconds
        val tap1Path = Path().apply { moveTo(x, y) }
        val tap2Path = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(tap1Path, 0L, 45L)
        val stroke2 = GestureDescription.StrokeDescription(tap2Path, 95L, 45L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        return dispatchGesture(gesture, null, mainHandler)
    }

    fun restartYouTubeVideo(onCompleted: (Boolean) -> Unit) {
        if (clickNodeMatchingKeywords(listOf("посмотреть еще раз", "повторить", "replay"))) {
            onCompleted(true)
            return
        }

        fun tryResetSeekBar(): Boolean {
            val root = rootInActiveWindow ?: return false
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            collectAllNodes(root, allNodes)
            val seekBar = allNodes.firstOrNull { node ->
                node.isVisibleToUser &&
                    (node.className?.toString()?.contains("SeekBar", ignoreCase = true) == true ||
                        nodeLabel(node).contains("ползунок") ||
                        nodeLabel(node).contains("seek"))
            } ?: return false

            val rect = Rect()
            seekBar.getBoundsInScreen(rect)
            if (rect.width() <= 20) return false
            val tapX = (rect.left + 8).toFloat()
            val tapY = rect.centerY().toFloat()
            val path = Path().apply { moveTo(tapX, tapY) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, mainHandler)
        }

        if (tryResetSeekBar()) {
            onCompleted(true)
            return
        }
        tapPlayerArea {
            mainHandler.postDelayed({
                val ok = tryResetSeekBar()
                onCompleted(ok)
            }, 380L)
        }
    }

    fun clickNextOrPreviousVideo(next: Boolean, onFallback: () -> Unit) {
        val keywords = if (next) {
            listOf("следующее видео", "следующий", "next video", "next")
        } else {
            listOf("предыдущее видео", "предыдущий", "previous video", "previous")
        }
        if (clickNodeMatchingKeywords(keywords)) return

        tapPlayerArea {
            mainHandler.postDelayed({
                if (!clickNodeMatchingKeywords(keywords)) {
                    onFallback()
                }
            }, 360L)
        }
    }

    fun setFullscreen(enter: Boolean, onCompleted: (Boolean) -> Unit) {
        val keywords = if (enter) {
            listOf("полноэкранный режим", "на весь экран", "enter fullscreen", "fullscreen")
        } else {
            listOf("выйти из полноэкранного режима", "выход из полноэкранного", "свернуть экран", "exit fullscreen")
        }
        if (clickNodeMatchingKeywords(keywords)) {
            onCompleted(true)
            return
        }
        tapPlayerArea {
            mainHandler.postDelayed({
                val ok = clickNodeMatchingKeywords(keywords)
                onCompleted(ok)
            }, 360L)
        }
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes, maxNodes = 260, maxDepth = 18)

        val collected = LinkedHashSet<String>()
        var totalChars = 0
        for (node in allNodes) {
            if (!node.isVisibleToUser) continue
            val rawText = node.text?.toString()?.trim().orEmpty()
            val rawDesc = node.contentDescription?.toString()?.trim().orEmpty()
            val candidate = when {
                rawText.isNotBlank() -> rawText
                rawDesc.isNotBlank() -> rawDesc
                else -> ""
            }
            if (candidate.length < 2) continue
            if (collected.add(candidate)) {
                totalChars += candidate.length + 2
                if (totalChars >= 550) break
            }
        }
        return collected.joinToString(". ")
    }

    private fun selectAdjacentSpeedOption(faster: Boolean): String? {
        val root = rootInActiveWindow ?: return null
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        // Speed rows contain patterns like 0.25, 0.5, 0.75, Обычная / Normal / 1x, 1.25, 1.5, 1.75, 2
        val speedLabels = listOf(
            "0.25" to listOf("0.25", "0,25"),
            "0.5" to listOf("0.5", "0,5"),
            "0.75" to listOf("0.75", "0,75"),
            "Обычная (1x)" to listOf("обычная", "normal", "1x", "1.0", "1,0"),
            "1.25" to listOf("1.25", "1,25"),
            "1.5" to listOf("1.5", "1,5"),
            "1.75" to listOf("1.75", "1,75"),
            "2x" to listOf("2x", "2.0", "2,0", " 2", "2 ")
        )

        val matchedRows = mutableMapOf<Int, AccessibilityNodeInfo>()
        var detectedCurrentIdx: Int? = null

        for (node in allNodes) {
            val label = nodeLabel(node)
            if (label.isBlank() || label.length > 35) continue

            for ((idx, pair) in speedLabels.withIndex()) {
                val aliases = pair.second
                val matches = aliases.any { alias ->
                    label == alias.trim() ||
                        label.startsWith("${alias.trim()}x") ||
                        label.startsWith("${alias.trim()}х") ||
                        (idx == 7 && (label == "2" || label == "2x" || label == "2х"))
                }
                if (matches) {
                    val clickable = findClickableAncestorOrSelf(node)
                    if (clickable != null && idx !in matchedRows) {
                        matchedRows[idx] = clickable
                        if (node.isChecked || node.isSelected || clickable.isChecked || clickable.isSelected) {
                            detectedCurrentIdx = idx
                        }
                    }
                }
            }
        }

        if (matchedRows.isEmpty()) return null
        val currentIdx = detectedCurrentIdx ?: trackedSpeedIndex
        val availableIndices = matchedRows.keys.sorted()

        val targetIdx = if (faster) {
            availableIndices.firstOrNull { it > currentIdx } ?: availableIndices.last()
        } else {
            availableIndices.lastOrNull { it < currentIdx } ?: availableIndices.first()
        }

        val targetNode = matchedRows[targetIdx] ?: return null
        val clicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) {
            trackedSpeedIndex = targetIdx
            return "Скорость ${speedLabels[targetIdx].first}"
        }
        return null
    }

    private fun clickNodeMatchingKeywords(
        keywords: List<String>,
        preferTopHalf: Boolean = false
    ): Boolean {
        val node = findNodeMatchingKeywords(keywords, preferTopHalf) ?: return false
        val target = findClickableAncestorOrSelf(node) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeMatchingKeywords(
        keywords: List<String>,
        preferTopHalf: Boolean = false
    ): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        val screenHeight = resources.displayMetrics.heightPixels
        val rect = Rect()

        for (kw in keywords) {
            val matching = allNodes.filter { node ->
                if (!node.isVisibleToUser) return@filter false
                val label = nodeLabel(node)
                label.contains(kw)
            }
            if (matching.isNotEmpty()) {
                if (preferTopHalf) {
                    val topMatch = matching.firstOrNull {
                        it.getBoundsInScreen(rect)
                        rect.centerY() < (screenHeight * 0.55f)
                    }
                    if (topMatch != null) return topMatch
                }
                return matching.first()
            }
        }
        return null
    }

    private fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        return "$text $desc".lowercase().replace('ё', 'е').trim()
    }

    private fun collectAllNodes(
        root: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        maxNodes: Int = 320,
        maxDepth: Int = 18
    ) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty() && out.size < maxNodes) {
            val (node, depth) = queue.removeFirst()
            out.add(node)
            if (depth >= maxDepth) continue
            val count = node.childCount.coerceAtMost(48)
            for (i in 0 until count) {
                if (out.size + queue.size >= maxNodes) break
                val child = node.getChild(i) ?: continue
                queue.addLast(child to (depth + 1))
            }
        }
    }

    private fun findScrollableNode(
        root: AccessibilityNodeInfo,
        maxNodes: Int = 180,
        maxDepth: Int = 14
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (node.isScrollable) return node
            if (depth >= maxDepth) continue
            val count = node.childCount.coerceAtMost(40)
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                queue.addLast(child to (depth + 1))
            }
        }
        return null
    }

    private fun collectClickableCandidates(
        root: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        maxNodes: Int = 220,
        maxDepth: Int = 16
    ) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes && out.size < 40) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (node.isClickable && node.isVisibleToUser) {
                out.add(node)
            }
            if (depth >= maxDepth) continue
            val count = node.childCount.coerceAtMost(40)
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                queue.addLast(child to (depth + 1))
            }
        }
    }

    private fun tapPlayerArea(onDone: () -> Unit) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val y = metrics.heightPixels * 0.22f
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone()
            }
        }, mainHandler)
        if (!dispatched) onDone()
    }

    private fun performSwipeGesture(upward: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val x = width * 0.5f
        val startY = if (upward) height * 0.72f else height * 0.30f
        val endY = if (upward) height * 0.28f else height * 0.72f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
