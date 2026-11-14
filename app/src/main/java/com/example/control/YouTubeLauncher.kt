package com.example.control

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.service.VoiceControlAccessibilityService
import com.example.voice.VoiceEngineStateHolder
import java.net.URLEncoder

data class YouTubeLaunchResult(
    val launched: Boolean,
    val targetLabel: String,
    val isNativeClient: Boolean,
    val launchedViaBackgroundOverlay: Boolean = false,
    val blockedByMissingPermission: Boolean = false
)

object YouTubeLauncher {

    private const val LAUNCH_CHANNEL_ID = "golosgid_launch_prompts"
    private const val LAUNCH_NOTIFICATION_ID = 4202
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ordered by preference for Honor / Huawei devices without official Google Mobile Services
    private val preferredPackages = listOf(
        "com.google.android.youtube" to "YouTube",
        "app.revanced.android.youtube" to "YouTube ReVanced",
        "com.vanced.android.youtube" to "YouTube Vanced",
        "org.schabi.newpipe" to "NewPipe"
    )

    fun findInstalledYouTubeClient(context: Context): Pair<String, String>? {
        val pm = context.packageManager
        for ((pkg, label) in preferredPackages) {
            try {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                return pkg to label
            } catch (_: Throwable) {
                try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                    return pkg to label
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    fun openYouTubeHome(context: Context): YouTubeLaunchResult {
        val installed = findInstalledYouTubeClient(context)
        if (installed != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(installed.first)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                return dispatchLaunch(
                    context = context,
                    intent = launchIntent,
                    targetLabel = installed.second,
                    isNativeClient = true,
                    promptTitle = "Открыть ${installed.second}"
                )
            }
        }
        return openUrlInBrowser(context, "https://m.youtube.com", "Открыть YouTube")
    }

    fun openYouTubeSearch(context: Context, query: String): YouTubeLaunchResult {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        val installed = findInstalledYouTubeClient(context)

        if (installed != null) {
            val (pkg, label) = installed
            val intent = if (pkg == "org.schabi.newpipe") {
                Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(pkg)
                    putExtra("query", query.trim())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent(Intent.ACTION_VIEW, searchUri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            return dispatchLaunch(
                context = context,
                intent = intent,
                targetLabel = label,
                isNativeClient = true,
                promptTitle = "Открыть YouTube: $query"
            )
        }

        return openUrlInBrowser(context, searchUri.toString(), "Найти на YouTube: $query")
    }

    fun openYouTubeSubscriptions(context: Context): YouTubeLaunchResult {
        val subsUri = Uri.parse("https://www.youtube.com/feed/subscriptions")
        val installed = findInstalledYouTubeClient(context)
        if (installed != null) {
            val (pkg, label) = installed
            val intent = Intent(Intent.ACTION_VIEW, subsUri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return dispatchLaunch(
                context = context,
                intent = intent,
                targetLabel = "$label (Подписки)",
                isNativeClient = true,
                promptTitle = "Открыть подписки $label"
            )
        }
        return openUrlInBrowser(context, subsUri.toString(), "Открыть подписки YouTube")
    }

    private fun openUrlInBrowser(context: Context, url: String, promptTitle: String): YouTubeLaunchResult {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return dispatchLaunch(
            context = context,
            intent = browserIntent,
            targetLabel = "Браузер (YouTube)",
            isNativeClient = false,
            promptTitle = promptTitle
        )
    }

    private fun dispatchLaunch(
        context: Context,
        intent: Intent,
        targetLabel: String,
        isNativeClient: Boolean,
        promptTitle: String
    ): YouTubeLaunchResult {
        val inForeground = VoiceEngineStateHolder.state.value.isAppInForeground
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasAccessibility = VoiceControlAccessibilityService.instance != null

        val ok = startActivityReliably(context, intent)
        if (!ok) {
            return YouTubeLaunchResult(
                launched = false,
                targetLabel = "Не найден браузер или YouTube",
                isNativeClient = isNativeClient
            )
        }

        val mayBeBlockedBySystem = !inForeground && !hasOverlay && !hasAccessibility
        if (mayBeBlockedBySystem) {
            postLaunchNotificationFallback(context, intent, promptTitle)
        }

        return YouTubeLaunchResult(
            launched = true,
            targetLabel = targetLabel,
            isNativeClient = isNativeClient,
            launchedViaBackgroundOverlay = !inForeground && hasOverlay,
            blockedByMissingPermission = mayBeBlockedBySystem
        )
    }

    fun startActivityReliably(context: Context, intent: Intent): Boolean {
        val appContext = context.applicationContext
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val inForeground = VoiceEngineStateHolder.state.value.isAppInForeground
        val hasOverlay = Settings.canDrawOverlays(appContext)
        val accService = VoiceControlAccessibilityService.instance

        // When minimized on Honor/Huawei Magic UI / EMUI, briefly attaching a 1x1 transparent
        // SYSTEM_ALERT_WINDOW view satisfies both Android BAL and OEM background-popup filters.
        if (!inForeground && hasOverlay) {
            launchWithBriefOverlayWindow(appContext, intent, accService)
            return true
        }

        val launchContext = accService ?: appContext
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                val pi = PendingIntent.getActivity(
                    launchContext,
                    (System.currentTimeMillis() and 0x7FFF).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                pi.send(launchContext, 0, null, null, null, null, options.toBundle())
                return true
            }
        } catch (_: Exception) {
        }

        return try {
            launchContext.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                appContext.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun launchWithBriefOverlayWindow(
        appContext: Context,
        intent: Intent,
        accService: VoiceControlAccessibilityService?
    ) {
        mainHandler.post {
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            var overlayView: View? = null
            if (wm != null) {
                try {
                    val view = View(appContext)
                    val params = WindowManager.LayoutParams(
                        1,
                        1,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_PHONE
                        },
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = 0
                        y = 0
                    }
                    wm.addView(view, params)
                    overlayView = view
                } catch (_: Exception) {
                }
            }

            mainHandler.postDelayed({
                val launchCtx = accService ?: appContext
                try {
                    launchCtx.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        appContext.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            }, 70L)

            if (overlayView != null && wm != null) {
                mainHandler.postDelayed({
                    try {
                        wm.removeViewImmediate(overlayView)
                    } catch (_: Exception) {
                    }
                }, 850L)
            }
        }
    }

    private fun postLaunchNotificationFallback(
        context: Context,
        intent: Intent,
        title: String
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    LAUNCH_CHANNEL_ID,
                    "Быстрый переход в YouTube",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
            val pi = PendingIntent.getActivity(
                context,
                LAUNCH_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, LAUNCH_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText("Нажмите, чтобы открыть (или разрешите «Поверх других окон»)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(LAUNCH_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }
}
