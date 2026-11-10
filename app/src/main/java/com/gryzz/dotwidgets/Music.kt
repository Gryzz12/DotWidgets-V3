package com.gryzz.dotwidgets

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

class MusicInfo(
    val hasAccess: Boolean,
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val pkg: String?,
    val sessionActivity: PendingIntent?,
)

/**
 * Lee la música que suena (Spotify, YouTube Music…) a través de las sesiones multimedia.
 * Requiere que el usuario active "Acceso a notificaciones" para Dot Widgets.
 */
object Music {
    private fun listener(ctx: Context) = ComponentName(ctx, MediaListener::class.java)

    fun hasAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener(ctx))

    fun controller(ctx: Context): MediaController? {
        if (!hasAccess(ctx)) return null
        val list = try {
            ctx.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener(ctx))
        } catch (e: SecurityException) {
            return null
        }
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }

    fun info(ctx: Context): MusicInfo {
        val access = hasAccess(ctx)
        val c = if (access) controller(ctx) else null
        if (c == null) return MusicInfo(access, null, null, null, false, null, null)
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return MusicInfo(true, title, artist, art, playing, c.packageName, c.sessionActivity)
    }

    fun isPlaying(ctx: Context): Boolean =
        controller(ctx)?.playbackState?.state == PlaybackState.STATE_PLAYING

    fun togglePlay(ctx: Context) {
        val c = controller(ctx) ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next(ctx: Context) {
        controller(ctx)?.transportControls?.skipToNext()
    }

    fun previous(ctx: Context) {
        controller(ctx)?.transportControls?.skipToPrevious()
    }
}

/**
 * Servicio que Android mantiene vivo mientras el acceso esté activo.
 * Escucha cambios de canción o de play/pausa y repinta los widgets con música al momento.
 */
class MediaListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableListOf<MediaController>()
    private var lastKey = ""

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list ?: emptyList())
        refresh()
    }

    private val doRefresh = Runnable {
        val app = applicationContext
        Thread {
            try {
                val c = Music.controller(app)
                val key = listOf(
                    c?.packageName,
                    c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    c?.playbackState?.state?.toString(),
                ).joinToString("|")
                if (key != lastKey) {
                    lastKey = key
                    Updater.updateAll(app, musicOnly = true)
                }
            } catch (e: Exception) {
                Log.w("DotWidgets", "Música", e)
            }
        }.start()
    }

    private fun refresh() {
        handler.removeCallbacks(doRefresh)
        handler.postDelayed(doRefresh, 350)
    }

    private fun attach(list: List<MediaController>) {
        watched.forEach { it.unregisterCallback(callback) }
        watched.clear()
        list.forEach {
            it.registerCallback(callback, handler)
            watched.add(it)
        }
    }

    override fun onListenerConnected() {
        try {
            val msm = getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(this, MediaListener::class.java)
            msm.addOnActiveSessionsChangedListener(sessions, cn)
            attach(msm.getActiveSessions(cn))
            refresh()
        } catch (e: SecurityException) {
            Log.w("DotWidgets", "Sin acceso a sesiones multimedia", e)
        }
    }

    override fun onListenerDisconnected() {
        try {
            getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessions)
        } catch (e: Exception) {
            // nada
        }
        attach(emptyList())
    }
}
