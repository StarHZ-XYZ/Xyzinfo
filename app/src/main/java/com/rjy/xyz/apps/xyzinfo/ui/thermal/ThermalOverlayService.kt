package com.rjy.xyz.apps.xyzinfo.ui.thermal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 温度浮窗服务。
 *
 * 悬浮在其它应用上面显示当前最高温；同时**每分钟把温度落盘**，所以退出应用也能长期记录。
 * 浮窗可以拖动，点一下能回到温度页；通知栏里也留了关闭入口。
 *
 * 需要 `SYSTEM_ALERT_WINDOW`（"显示在其他应用上层"），由温度页引导用户授权。
 */
class ThermalOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var tempLabel: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val uiTick = object : Runnable {
        override fun run() {
            updateTemperature()
            handler.postDelayed(this, UI_INTERVAL_MILLIS)
        }
    }

    private val logTick = object : Runnable {
        override fun run() {
            runCatching { ThermalLogger.logSample(this@ThermalOverlayService) }
            handler.postDelayed(this, LOG_INTERVAL_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
        handler.post(uiTick)
        handler.post(logTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    // ---------- 浮窗 ----------

    private fun addOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val density = resources.displayMetrics.density
        val label = TextView(this).apply {
            text = "-- ℃"
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@ThermalOverlayService, R.color.text_primary))
            val pad = (12 * density).roundToInt()
            setPadding(pad, (8 * density).roundToInt(), pad, (8 * density).roundToInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(ContextCompat.getColor(this@ThermalOverlayService, R.color.surface))
                setStroke((1 * density).roundToInt(), ContextCompat.getColor(this@ThermalOverlayService, R.color.stroke))
            }
            elevation = 6f * density
            addView(label)
        }
        tempLabel = label

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).roundToInt()
            y = (120 * density).roundToInt()
        }

        // 拖动移动位置；轻点回到温度页
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > 12f) moved = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        runCatching {
                            startActivity(
                                Intent(this, ThermalActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    true
                }

                else -> false
            }
        }

        runCatching { windowManager.addView(container, params) }.onSuccess { overlay = container }
        updateTemperature()
    }

    private fun updateTemperature() {
        // 读 /sys 是 I/O：放后台线程，读完再回主线程刷文字
        Thread({
            val hottest = runCatching { ThermalLogger.readZones(this@ThermalOverlayService) }
                .getOrDefault(emptyList())
                .firstOrNull() ?: return@Thread
            handler.post {
                tempLabel?.text = String.format(Locale.US, "%.1f ℃", hottest.celsius)
                tempLabel?.contentDescription = "当前最高温 · ${hottest.name}"
            }
        }, "xyzinfo-thermal-overlay").start()
    }

    // ---------- 通知 ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "温度监控",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "温度浮窗与长期记录" }
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ThermalActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ThermalOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_module_sensor)
            .setContentTitle("温度监控运行中")
            .setContentText("正在悬浮显示温度，并每分钟记录一次")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.rjy.xyz.apps.xyzinfo.STOP_THERMAL_OVERLAY"
        /** 浮窗开关状态（温度页里读它决定按钮文案）。 */
        const val PREF_RUNNING = "thermal_overlay_running"
        private const val CHANNEL_ID = "thermal_overlay"
        private const val NOTIFICATION_ID = 7201
        private const val UI_INTERVAL_MILLIS = 5_000L
        private const val LOG_INTERVAL_MILLIS = 60_000L

        fun isRunning(context: Context): Boolean = context
            .getSharedPreferences("xyz_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_RUNNING, false)

        fun setRunning(context: Context, running: Boolean) {
            context.getSharedPreferences("xyz_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_RUNNING, running).apply()
        }

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ThermalOverlayService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ThermalOverlayService::class.java)) }
        }
    }
}
