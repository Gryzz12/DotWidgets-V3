package com.gryzz.dotwidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Base de todos los widgets: cualquier evento repinta todo (comparten tema y datos). */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Updater.async(this, ctx) {
            Weather.maybeFetch(it, false)
            Updater.updateAll(it)
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        Updater.async(this, ctx) { Updater.updateAll(it) }
    }

    override fun onEnabled(ctx: Context) {
        Updater.scheduleNext(ctx)
    }

    override fun onDisabled(ctx: Context) {
        // Si ya no queda ningún widget de la app, updateAll cancela la alarma
        Updater.async(this, ctx) { Updater.updateAll(it) }
    }
}

/** Principal: todo en un solo widget (ideal 5×3). */
class DotWidget : BaseWidget()
class ClockWidget : BaseWidget()
class ProgressWidget : BaseWidget()
class CalendarWidget : BaseWidget()
class ClockProgressWidget : BaseWidget()
class ButtonsWidget : BaseWidget()
/** Todo + música con portada y controles. */
class MusicWidget : BaseWidget()
/** Música cuadrado 2×2: portada, toques a los lados para cambiar de canción. */
class MusicSquareWidget : BaseWidget()
/** Calendario + temperatura + música abajo. */
class CalTempMusicWidget : BaseWidget()
/** Hora + temperatura + música cuadrada + calendario. */
class DashWidget : BaseWidget()
/** Hora + calendario + música. */
class ClockCalMusicWidget : BaseWidget()
/** Día y hora + calendario pequeño + música estirada abajo (2 filas). */
class MiniMusicWidget : BaseWidget()

/** Vacío y transparente: no pinta nada, solo ocupa sitio en el escritorio. */
class EmptyWidget : AppWidgetProvider()

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Updater.async(this, ctx) { c ->
            when (intent.action) {
                Updater.ACTION_TICK -> {
                    if (Updater.hasWidgets(c)) Weather.maybeFetch(c, false)
                    Updater.updateAll(c)
                }
                Updater.ACTION_CYCLE -> {
                    Prefs.cycleTheme(c)
                    Updater.updateAll(c)
                }
                Updater.ACTION_TOGGLE_LOC -> {
                    Toggles.toggleLocation(c)
                    Updater.updateAll(c)
                    Thread.sleep(1500)
                    Updater.updateAll(c)
                }
                Updater.ACTION_TOGGLE_SAVER -> {
                    Toggles.toggleSaver(c)
                    Updater.updateAll(c)
                    Thread.sleep(1500)
                    Updater.updateAll(c)
                }
                Updater.ACTION_REFRESH -> {
                    Weather.maybeFetch(c, true)
                    Updater.updateAll(c)
                }
                Updater.ACTION_PREV -> media(c, "prev") { Music.previous(c) }
                Updater.ACTION_PLAY -> media(c, if (Music.isPlaying(c)) "pause" else "play") { Music.togglePlay(c) }
                Updater.ACTION_NEXT -> media(c, "next") { Music.next(c) }
                else -> Updater.updateAll(c)
            }
        }
    }

    /** Muestra el icono del botón pulsado, ejecuta la acción y lo quita al momento. */
    private fun media(c: Context, icon: String, action: () -> Unit) {
        Updater.feedback = icon
        Updater.updateAll(c, musicOnly = true)
        action()
        Thread.sleep(700)
        Updater.feedback = null
        Updater.updateAll(c, musicOnly = true)
    }
}
