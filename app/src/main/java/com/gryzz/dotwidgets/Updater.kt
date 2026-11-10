package com.gryzz.dotwidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime
import kotlin.math.sqrt

object Updater {
    const val ACTION_TICK = "com.gryzz.dotwidgets.TICK"
    const val ACTION_CYCLE = "com.gryzz.dotwidgets.CYCLE_THEME"
    const val ACTION_TOGGLE_LOC = "com.gryzz.dotwidgets.TOGGLE_LOC"
    const val ACTION_TOGGLE_SAVER = "com.gryzz.dotwidgets.TOGGLE_SAVER"
    const val ACTION_REFRESH = "com.gryzz.dotwidgets.REFRESH"
    const val ACTION_PREV = "com.gryzz.dotwidgets.PREV"
    const val ACTION_PLAY = "com.gryzz.dotwidgets.PLAY"
    const val ACTION_NEXT = "com.gryzz.dotwidgets.NEXT"

    /** Todos los widgets de la app y qué dibuja cada uno. */
    val WIDGETS: List<Pair<Class<*>, Kind>> = listOf(
        DotWidget::class.java to Kind.MAIN,
        ClockWidget::class.java to Kind.CLOCK,
        ProgressWidget::class.java to Kind.PROGRESS,
        CalendarWidget::class.java to Kind.CALENDAR,
        ClockProgressWidget::class.java to Kind.CLOCK_PROGRESS,
        ButtonsWidget::class.java to Kind.BUTTONS,
        MusicWidget::class.java to Kind.FULL,
        MusicSquareWidget::class.java to Kind.MUSIC,
        CalTempMusicWidget::class.java to Kind.CAL_TEMP_MUSIC,
        DashWidget::class.java to Kind.DASH,
        ClockCalMusicWidget::class.java to Kind.CLOCK_CAL_MUSIC,
        MiniMusicWidget::class.java to Kind.MINI_MUSIC,
    )

    /** Tipos que muestran música. */
    private val MUSIC_KINDS = setOf(Kind.FULL, Kind.MUSIC, Kind.CAL_TEMP_MUSIC, Kind.DASH, Kind.CLOCK_CAL_MUSIC, Kind.MINI_MUSIC)

    /** Icono de respuesta tras tocar un control de música (vive un instante). */
    @Volatile
    var feedback: String? = null

    private const val STEP = 60 * 1000L
    private const val MAX_PX = 1_300_000f

    /** Ejecuta trabajo fuera del hilo principal manteniendo vivo el receiver. */
    fun async(r: BroadcastReceiver, ctx: Context, block: (Context) -> Unit) {
        val pending = r.goAsync()
        val app = ctx.applicationContext
        Thread {
            try {
                block(app)
            } catch (e: Throwable) {
                Log.e("DotWidgets", "Error actualizando", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun idsOf(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    fun hasWidgets(ctx: Context): Boolean = WIDGETS.any { idsOf(ctx, it.first).isNotEmpty() }

    /** Repinta todos los widgets (o solo los que muestran música). */
    fun updateAll(ctx: Context, musicOnly: Boolean = false) {
        if (!hasWidgets(ctx)) {
            cancel(ctx)
            return
        }
        val mgr = AppWidgetManager.getInstance(ctx)
        val needMusic = WIDGETS.any { it.second in MUSIC_KINDS && idsOf(ctx, it.first).isNotEmpty() }
        val music = if (needMusic) Music.info(ctx) else null
        music?.pkg?.let { Prefs.setLastMusicPkg(ctx, it) }
        val st = State(
            now = LocalDateTime.now(),
            theme = Prefs.theme(ctx),
            weather = Prefs.weather(ctx),
            city = Prefs.city(ctx),
            locOn = Toggles.locationOn(ctx),
            saverOn = Toggles.saverOn(ctx),
            music = music,
            feedback = feedback,
        )
        for ((cls, kind) in WIDGETS) {
            if (musicOnly && kind !in MUSIC_KINDS) continue
            for (id in idsOf(ctx, cls)) {
                try {
                    update(ctx, mgr, id, st, kind)
                } catch (e: Exception) {
                    Log.e("DotWidgets", "Widget $id", e)
                }
            }
        }
        if (!musicOnly) scheduleNext(ctx)
    }

    /**
     * Próxima actualización al empezar el siguiente minuto. Alarma RTC (no despierta el móvil):
     * con la pantalla apagada no gasta nada y al encenderla se pone al día al instante.
     */
    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val next = (now / STEP + 1) * STEP + 300
        val pi = broadcast(ctx, ACTION_TICK, 10)
        if (am.canScheduleExactAlarms()) {
            am.setExact(AlarmManager.RTC, next, pi)
        } else {
            am.setWindow(AlarmManager.RTC, next, 60_000L, pi)
        }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(broadcast(ctx, ACTION_TICK, 10))
    }

    // ---------------------------------------------------------------- PendingIntents

    fun broadcast(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun activity(ctx: Context, intent: Intent, req: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, req,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Última app de música usada; si no hay ninguna, la app de música por defecto. */
    private fun lastMusicIntent(ctx: Context): Intent {
        val pkg = Prefs.lastMusicPkg(ctx)
        val launch = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        return launch ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
    }

    private fun calendarIntent(ctx: Context): Intent =
        ctx.packageManager.getLaunchIntentForPackage("com.google.android.calendar")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))

    // ---------------------------------------------------------------- Pintado

    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int, st: State, kind: Kind) {
        val o = mgr.getAppWidgetOptions(id)
        var wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        var hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (wdp <= 0) wdp = 300
        if (hdp <= 0) hdp = 280

        val density = ctx.resources.displayMetrics.density
        val realW = wdp * density
        val realH = hdp * density
        // Escala para no superar el límite de memoria de bitmaps de los widgets
        val s = if (realW * realH > MAX_PX) sqrt(MAX_PX / (realW * realH)) else 1f
        val w = (realW * s).toInt()
        val h = (realH * s).toInt()

        val (bitmap, plan) = Renderer.render(ctx, w, h, density * s, st, kind)

        val rv = RemoteViews(ctx.packageName, R.layout.widget_dot)
        rv.setInt(R.id.root, "setBackgroundResource", 0)
        rv.setImageViewBitmap(R.id.img, bitmap)

        // Zonas táctiles
        val openCal = activity(ctx, calendarIntent(ctx), 20)
        place(rv, R.id.hitClock, plan.clockHit, s)
        rv.setOnClickPendingIntent(R.id.hitClock, activity(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), 30))
        place(rv, R.id.hitWeather, plan.weatherHit, s)
        rv.setOnClickPendingIntent(R.id.hitWeather, broadcast(ctx, ACTION_REFRESH, 31))
        for (hit in listOf(R.id.hitProgress to plan.progressHit, R.id.hitChips to plan.chipsHit, R.id.hitGrid to plan.gridHit)) {
            place(rv, hit.first, hit.second, s)
            rv.setOnClickPendingIntent(hit.first, openCal)
        }
        place(rv, R.id.hitTheme, plan.theme, s)
        rv.setOnClickPendingIntent(R.id.hitTheme, broadcast(ctx, ACTION_CYCLE, 21))

        place(rv, R.id.hitLoc, plan.loc, s)
        place(rv, R.id.hitSaver, plan.saver, s)
        if (Toggles.hasSecure(ctx)) {
            rv.setOnClickPendingIntent(R.id.hitLoc, broadcast(ctx, ACTION_TOGGLE_LOC, 40))
            rv.setOnClickPendingIntent(R.id.hitSaver, broadcast(ctx, ACTION_TOGGLE_SAVER, 41))
        } else {
            rv.setOnClickPendingIntent(R.id.hitLoc, activity(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 42))
            rv.setOnClickPendingIntent(R.id.hitSaver, activity(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), 43))
        }

        // Música: si hay algo sonando (o en pausa), los toques controlan la canción;
        // si no hay sesión, abren la última app de música usada.
        val m = st.music
        val active = m != null && m.hasAccess && m.pkg != null
        val openMusic: PendingIntent = when {
            m == null || !m.hasAccess -> activity(ctx, Intent(ctx, MainActivity::class.java), 50)
            m.sessionActivity != null -> m.sessionActivity
            else -> activity(ctx, lastMusicIntent(ctx), 51)
        }
        place(rv, R.id.hitMusic, plan.musicHit, s)
        rv.setOnClickPendingIntent(R.id.hitMusic, openMusic)
        place(rv, R.id.hitPrev, plan.prev, s)
        place(rv, R.id.hitPlay, plan.play, s)
        place(rv, R.id.hitNext, plan.next, s)
        place(rv, R.id.eq, plan.eq, s)
        if (active) {
            rv.setOnClickPendingIntent(R.id.hitPrev, broadcast(ctx, ACTION_PREV, 52))
            rv.setOnClickPendingIntent(R.id.hitPlay, broadcast(ctx, ACTION_PLAY, 53))
            rv.setOnClickPendingIntent(R.id.hitNext, broadcast(ctx, ACTION_NEXT, 54))
        } else {
            for (hit in listOf(R.id.hitPrev, R.id.hitPlay, R.id.hitNext)) rv.setOnClickPendingIntent(hit, openMusic)
        }

        mgr.updateAppWidget(id, rv)
    }

    /** Coloca una vista en la posición del bitmap (convertida a píxeles reales), u oculta si no hay sitio. */
    private fun place(rv: RemoteViews, viewId: Int, box: Box?, s: Float) {
        if (box == null) {
            rv.setViewVisibility(viewId, View.GONE)
            return
        }
        val px = TypedValue.COMPLEX_UNIT_PX
        rv.setViewVisibility(viewId, View.VISIBLE)
        rv.setViewLayoutMargin(viewId, RemoteViews.MARGIN_LEFT, box.l / s, px)
        rv.setViewLayoutMargin(viewId, RemoteViews.MARGIN_TOP, box.t / s, px)
        rv.setViewLayoutWidth(viewId, box.w / s, px)
        rv.setViewLayoutHeight(viewId, box.h / s, px)
    }
}
