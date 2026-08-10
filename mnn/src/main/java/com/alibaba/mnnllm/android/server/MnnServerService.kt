// Foreground service hosting the local OpenAI-compatible API on 127.0.0.1.
// Mirrors the lifecycle pattern of the app's WebServerService: a foreground
// notification keeps the process alive while the server runs.

package com.alibaba.mnnllm.android.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import org.koin.android.ext.android.inject

private const val TAG = "MnnServerService"
private const val CHANNEL_ID = "mnn_local_server"

class MnnServerService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkallm.action.MNN_SERVER_START"
        const val ACTION_STOP = "me.rerere.rikkallm.action.MNN_SERVER_STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_TOKEN = "token"
        const val NOTIFICATION_ID = 2101
    }

    private val manager: LocalMnnManager by inject()

    private var server: EmbeddedServer<*, *>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, LocalMnnManager.DEFAULT_PORT)
                val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
                manager.adoptCredentials(port, token)
                if (!startForegroundCompat()) {
                    manager.onServerStopped("Foreground service was denied by the system")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startHttpServer(port)
            }

            ACTION_STOP -> {
                stopHttpServer()
                manager.onServerStopped()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                // Restarted by the system without context: stop quietly.
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopHttpServer()
        super.onDestroy()
    }

    private fun startHttpServer(port: Int) {
        if (server != null) {
            manager.onServerStarted(port)
            return
        }
        try {
            val created = embeddedServer(CIO, port = port, host = "127.0.0.1") {
                mnnOpenAiRoutes(manager)
            }
            created.start(wait = false)
            server = created
            manager.onServerStarted(port)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server on port $port", e)
            manager.onServerStopped("无法启动本地服务（端口 $port 可能被占用）：${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopHttpServer() {
        val current = server ?: return
        server = null
        runCatching { current.stop(200, 1000) }
            .onFailure { Log.w(TAG, "Server stop failed", it) }
    }

    private fun startForegroundCompat(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "本地模型引擎",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            null
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MnnServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val state = manager.state.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(me.rerere.rikkallm.mnn.R.drawable.ic_mnn_server)
            .setContentTitle("本地模型引擎运行中")
            .setContentText("http://127.0.0.1:${state.port}/v1")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "停止", stopPendingIntent)
            .build()
    }
}
