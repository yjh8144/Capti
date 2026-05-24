package com.capti.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.capti.R
import com.capti.audio.AudioCaptureManager
import com.capti.engine.EngineManager
import com.capti.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CaptionService : Service() {

    companion object {
        const val CHANNEL_ID = "capti_caption_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.capti.action.START"
        const val ACTION_STOP = "com.capti.action.STOP"
    }

    @Inject lateinit var audioCaptureManager: AudioCaptureManager
    @Inject lateinit var engineManager: EngineManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCaption()
            ACTION_STOP -> stopCaption()
        }
        return START_STICKY
    }

    private fun startCaption() {
        startForeground(NOTIFICATION_ID, createNotification())

        audioCaptureManager.onAudioData = { data ->
            scope.launch {
                engineManager.feedAudio(data)
            }
        }

        scope.launch {
            engineManager.start()
        }
        scope.launch {
            audioCaptureManager.startRecording()
        }
    }

    private fun stopCaption() {
        audioCaptureManager.stopRecording()
        scope.launch {
            engineManager.stop()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        audioCaptureManager.stopRecording()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "实时字幕",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "实时字幕服务运行中"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Capti 实时字幕")
            .setContentText("正在识别中...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
