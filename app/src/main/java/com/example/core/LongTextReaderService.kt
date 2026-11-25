package com.example.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.AlMakhzanjiApplication
import com.example.MainActivity
import com.example.R

class LongTextReaderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val manager = (applicationContext as? AlMakhzanjiApplication)?.appContainer?.audioPlayerManager
        if (manager == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PAUSE -> manager.pauseLongReading(fromService = true)
            ACTION_RESUME -> manager.resumeLongReading(fromService = true)
            ACTION_NEXT -> manager.skipNext(fromService = true)
            ACTION_PREV -> manager.skipPrevious(fromService = true)
            ACTION_STOP -> {
                manager.stopPlayback(fromService = true)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val state = manager.readerState.value
        if (!state.isActive || state.sentences.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "قارئ النصوص الصوتية",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "تشغيل قراءة النصوص الطويلة في الخلفية"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(state: AudioPlayerManager.ReaderState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val total = state.sentences.size.coerceAtLeast(1)
        val index = (state.currentIndex + 1).coerceIn(1, total)
        val snippet = state.sentences.getOrNull(state.currentIndex).orEmpty()
            .replace('\n', ' ')
            .trim()
            .take(120)

        val statusTitle = if (state.isPaused) {
            "متوقف مؤقتاً • جملة $index من $total"
        } else {
            "جاري القراءة • جملة $index من $total"
        }

        val prevIntent = servicePendingIntent(ACTION_PREV, 101)
        val toggleAction = if (state.isPaused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (state.isPaused) "استئناف" else "إيقاف مؤقت"
        val toggleIntent = servicePendingIntent(toggleAction, 102)
        val nextIntent = servicePendingIntent(ACTION_NEXT, 103)
        val stopIntent = servicePendingIntent(ACTION_STOP, 104)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_audio)
            .setContentTitle(statusTitle)
            .setContentText(snippet.ifBlank { "معجم المخزنجي" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(snippet.ifBlank { "معجم المخزنجي" }))
            .setContentIntent(contentPendingIntent)
            .setOngoing(!state.isPaused)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "السابق", prevIntent)
            .addAction(0, toggleLabel, toggleIntent)
            .addAction(0, "التالي", nextIntent)
            .addAction(0, "إيقاف", stopIntent)
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, LongTextReaderService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "almakhzanji_reader_channel"
        const val NOTIFICATION_ID = 4109

        const val ACTION_START = "com.example.action.READER_START"
        const val ACTION_SYNC = "com.example.action.READER_SYNC"
        const val ACTION_PAUSE = "com.example.action.READER_PAUSE"
        const val ACTION_RESUME = "com.example.action.READER_RESUME"
        const val ACTION_PREV = "com.example.action.READER_PREV"
        const val ACTION_NEXT = "com.example.action.READER_NEXT"
        const val ACTION_STOP = "com.example.action.READER_STOP"

        fun intentFor(context: Context, action: String): Intent =
            Intent(context, LongTextReaderService::class.java).apply {
                this.action = action
            }
    }
}
