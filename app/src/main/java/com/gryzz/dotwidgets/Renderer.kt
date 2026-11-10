package com.gryzz.dotwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rectángulo en píxeles del bitmap. */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) / 2f
    val cy: Float get() = (t + b) / 2f
}

/** Dónde ha quedado cada cosa, para colocar encima las zonas táctiles. */
class Plan {
    var clockHit: Box? = null
    var weatherHit: Box? = null
    var progressHit: Box? = null
    var chipsHit: Box? = null
    var gridHit: Box? = null
    var loc: Box? = null
    var saver: Box? = null
    var theme: Box? = null
    var musicHit: Box? = null
    var prev: Box? = null
    var play: Box? = null
    var next: Box? = null
    /** Ecualizador animado (solo cuando suena). */
    var eq: Box? = null
}

class State(
    val now: LocalDateTime,
    val theme: DotTheme,
    val weather: Prefs.Weather?,
    val city: String,
    val locOn: Boolean,
    val saverOn: Boolean,
    val music: MusicInfo?,
    /** Icono a mostrar un instante tras tocar el widget de música ("play", "pause", "next", "prev"). */
    val feedback: String? = null,
)

/** Tipos de widget. */
enum class Kind {
    MAIN, CLOCK, PROGRESS, CALENDAR, CLOCK_PROGRESS, BUTTONS, FULL, MUSIC,
    /** Calendario + temperatura + música abajo */
    CAL_TEMP_MUSIC,
    /** Hora + temperatura + música cuadrada + calendario */
    DASH,
    /** Hora + calendario + música */
    CLOCK_CAL_MUSIC,
    /** Día y hora pequeños + calendario pequeño + música estirada abajo (2 filas) */
    MINI_MUSIC,
}

object Renderer {
    val MONTHS = arrayOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    val DAYS = arrayOf("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO")
    private val WEEK_HEAD = arrayOf("L", "M", "X", "J", "V", "S", "D")
    private const val SUN = 0xFFFFC21A.toInt()

    private val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private enum class Sec { CLOCK, PROGRESS, BAR, GRID }

    private fun bmp(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)

    private fun paint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun textPaint(color: Int, size: Float, tf: Typeface = sans): Paint =
        paint(color).apply { textSize = size; typeface = tf }

    private fun drawTextV(c: Canvas, s: String, x: Float, cy: Float, p: Paint) {
        c.drawText(s, x, cy - (p.descent() + p.ascent()) / 2f, p)
    }

    fun hhmm(minOfDay: Int): String = String.format("%02d:%02d", (minOfDay / 60) % 24, minOfDay % 60)

    // ================================================================ Entrada

    /** Pinta el widget entero. [d] es la densidad efectiva (px por dp del bitmap). */
    fun render(ctx: Context, w: Int, h: Int, d: Float, st: State, kind: Kind): Pair<Bitmap, Plan> {
        val b = bmp(w, h)
        val c = Canvas(b)
        val plan = Plan()
        if (kind == Kind.MUSIC) {
            drawMusicTile(c, Box(0f, 0f, w.toFloat(), h.toFloat()), d, st, plan, 24 * d)
            return Pair(b, plan)
        }
        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 24 * d, 24 * d, paint(st.theme.bg))
        val pad = 14 * d
        val box = Box(pad, pad, w - pad, h - pad)
        val gap = 12 * d

        when (kind) {
            Kind.MAIN -> layoutMain(ctx, c, box, d, st, plan)
            Kind.CLOCK -> drawClockRow(ctx, c, box, d, st, plan, allowWeather = true)
            Kind.PROGRESS -> {
                drawProgress(c, box, d, st)
                plan.progressHit = box
            }
            Kind.CALENDAR -> {
                val barH = min(36 * d, box.h * 0.2f)
                drawBar(ctx, c, Box(box.l, box.t, box.r, box.t + barH), d, st, plan, withToggles = false)
                if (box.h / d > 110) {
                    val g = Box(box.l, box.t + barH + 8 * d, box.r, box.b)
                    drawGrid(c, g, d, st)
                    plan.gridHit = g
                }
            }
            Kind.CLOCK_PROGRESS -> {
                if (box.w / box.h >= 1.9f) {
                    val split = box.l + box.w * 0.5f
                    drawClockRow(ctx, c, Box(box.l, box.t, split - gap / 2, box.b), d, st, plan, allowWeather = true)
                    val pb = Box(split + gap / 2, box.t, box.r, box.b)
                    drawProgress(c, pb, d, st)
                    plan.progressHit = pb
                } else {
                    val split = box.t + box.h * 0.55f
                    drawClockRow(ctx, c, Box(box.l, box.t, box.r, split - gap / 2), d, st, plan, allowWeather = true)
                    val pb = Box(box.l, split + gap / 2, box.r, box.b)
                    drawProgress(c, pb, d, st)
                    plan.progressHit = pb
                }
            }
            Kind.BUTTONS -> drawButtons(ctx, c, box, d, st, plan)
            Kind.MUSIC -> Unit // se pinta arriba (drawMusicTile)
            Kind.CAL_TEMP_MUSIC -> {
                val mh = (box.h * 0.24f).coerceIn(60 * d, 96 * d)
                val wh = (box.h * 0.17f).coerceIn(40 * d, 64 * d)
                val wb = Box(box.l, box.t, box.r, box.t + wh)
                drawWeatherCompact(c, wb, d, st)
                plan.weatherHit = wb
                val barH = min(32 * d, box.h * 0.11f)
                val barTop = wb.b + 8 * d
                drawBar(ctx, c, Box(box.l, barTop, box.r, barTop + barH), d, st, plan, withToggles = false)
                val g = Box(box.l, barTop + barH + 6 * d, box.r, box.b - mh - gap)
                drawGrid(c, g, d, st)
                plan.gridHit = g
                drawMusic(ctx, c, Box(box.l, box.b - mh, box.r, box.b), d, st, plan)
            }
            Kind.CLOCK_CAL_MUSIC -> {
                val mh = (box.h * 0.2f).coerceIn(56 * d, 88 * d)
                val ch = box.h * 0.24f
                drawClockRow(ctx, c, Box(box.l, box.t, box.r, box.t + ch), d, st, plan, allowWeather = false)
                val barH = min(32 * d, box.h * 0.1f)
                val barTop = box.t + ch + gap
                drawBar(ctx, c, Box(box.l, barTop, box.r, barTop + barH), d, st, plan, withToggles = false)
                val g = Box(box.l, barTop + barH + 6 * d, box.r, box.b - mh - gap)
                drawGrid(c, g, d, st)
                plan.gridHit = g
                drawMusic(ctx, c, Box(box.l, box.b - mh, box.r, box.b), d, st, plan)
            }
            Kind.DASH -> layoutDash(ctx, c, box, d, st, plan)
            Kind.MINI_MUSIC -> {
                val mh = (box.h * 0.46f).coerceIn(52 * d, 90 * d)
                val top = Box(box.l, box.t, box.r, box.b - mh - 10 * d)
                val split = top.l + top.w * 0.5f
                val left = Box(top.l, top.t, split - gap / 2, top.b)
                drawDayTime(c, left, d, st)
                plan.clockHit = left
                val right = Box(split + gap / 2, top.t, top.r, top.b)
                if (right.h / d >= 150) drawGrid(c, right, d, st) else drawWeek(c, right, d, st)
                plan.gridHit = right
                drawMusic(ctx, c, Box(box.l, box.b - mh, box.r, box.b), d, st, plan)
            }
            Kind.FULL -> {
                val mh = (box.h * 0.22f).coerceIn(68 * d, 112 * d)
                if (box.h - mh - gap > 120 * d) {
                    layoutMain(ctx, c, Box(box.l, box.t, box.r, box.b - mh - gap), d, st, plan)
                    drawMusic(ctx, c, Box(box.l, box.b - mh, box.r, box.b), d, st, plan)
                } else {
                    drawMusic(ctx, c, box, d, st, plan)
                }
            }
        }
        return Pair(b, plan)
    }

    /**
     * Hora + temperatura + música cuadrada + calendario.
     * Ancho: izquierda = hora arriba, portada cuadrada y tiempo abajo; derecha = calendario.
     */
    private fun layoutDash(ctx: Context, c: Canvas, box: Box, d: Float, st: State, plan: Plan) {
        val gap = 12 * d
        val wide = box.w / d >= 300
        val left: Box
        val right: Box?
        if (wide) {
            val split = box.l + box.w * 0.56f
            left = Box(box.l, box.t, split - gap / 2, box.b)
            right = Box(split + gap / 2, box.t, box.r, box.b)
            c.drawLine(split, box.t + 6 * d, split, box.b - 6 * d, paint(st.theme.surface).apply { strokeWidth = 1.2f * d })
        } else {
            left = Box(box.l, box.t, box.r, box.t + box.h * 0.6f)
            right = Box(box.l, left.b + gap, box.r, box.b)
        }
        val ch = left.h * 0.48f
        drawClockRow(ctx, c, Box(left.l, left.t, left.r, left.t + ch), d, st, plan, allowWeather = false)
        val bottom = Box(left.l, left.t + ch + gap, left.r, left.b)
        val tile = min(bottom.h, bottom.w * 0.55f)
        drawMusicTile(c, Box(bottom.l, bottom.t, bottom.l + tile, bottom.t + tile), d, st, plan, 16 * d)
        val wb = Box(bottom.l + tile + 12 * d, bottom.t, bottom.r, bottom.t + tile)
        if (wb.w > 50 * d) {
            drawWeather(c, wb, d, st.theme, st.city, st.weather)
            plan.weatherHit = wb
        }
        if (right != null && right.h > 80 * d) {
            val barH = min(32 * d, right.h * 0.13f)
            drawBar(ctx, c, Box(right.l, right.t, right.r, right.t + barH), d, st, plan, withToggles = false)
            val g = Box(right.l, right.t + barH + 6 * d, right.r, right.b)
            drawGrid(c, g, d, st)
            plan.gridHit = g
        }
    }

    /**
     * Widget principal:
     *  - ancho (≥ 300 dp) y alto suficiente: dos columnas con todo
     *    (izq.: reloj + progreso; dcha.: tiempo + botones + calendario)
     *  - ancho y bajo: reloj | progreso
     *  - estrecho: por filas según el alto
     */
    private fun layoutMain(ctx: Context, c: Canvas, box: Box, d: Float, st: State, plan: Plan) {
        val wdp = box.w / d
        val hdp = box.h / d
        val gap = 12 * d
        if (wdp >= 300 && hdp >= 200) {
            val split = box.l + box.w * 0.56f
            val left = Box(box.l, box.t, split - gap / 2, box.b)
            val right = Box(split + gap / 2, box.t, box.r, box.b)
            val divX = split
            c.drawLine(divX, box.t + 6 * d, divX, box.b - 6 * d, paint(st.theme.surface).apply { strokeWidth = 1.2f * d })

            val ch = left.h * 0.55f
            drawClockRow(ctx, c, Box(left.l, left.t, left.r, left.t + ch), d, st, plan, allowWeather = false)
            val pb = Box(left.l, left.t + ch + gap, left.r, left.b)
            drawProgress(c, pb, d, st)
            plan.progressHit = pb

            val wh = (right.h * 0.2f).coerceIn(40 * d, 70 * d)
            val wb = Box(right.l, right.t, right.r, right.t + wh)
            drawWeatherCompact(c, wb, d, st)
            plan.weatherHit = wb
            val barH = min(34 * d, right.h * 0.13f)
            val barTop = wb.b + 8 * d
            drawBar(ctx, c, Box(right.l, barTop, right.r, barTop + barH), d, st, plan, withToggles = true)
            val g = Box(right.l, barTop + barH + 6 * d, right.r, right.b)
            drawGrid(c, g, d, st)
            plan.gridHit = g
            return
        }
        if (wdp >= 300 && hdp >= 95) {
            val split = box.l + box.w * 0.52f
            drawClockRow(ctx, c, Box(box.l, box.t, split - gap / 2, box.b), d, st, plan, allowWeather = true)
            val pb = Box(split + gap / 2, box.t, box.r, box.b)
            drawProgress(c, pb, d, st)
            plan.progressHit = pb
            return
        }

        val secs: List<Pair<Sec, Float>> = when {
            hdp < 160 -> listOf(Sec.CLOCK to 1f)
            hdp < 300 -> listOf(Sec.CLOCK to 0.55f, Sec.BAR to 0f, Sec.PROGRESS to 0.45f)
            hdp < 440 -> listOf(Sec.CLOCK to 0.36f, Sec.BAR to 0f, Sec.GRID to 0.64f)
            else -> listOf(Sec.CLOCK to 0.28f, Sec.PROGRESS to 0.22f, Sec.BAR to 0f, Sec.GRID to 0.50f)
        }
        val barH = 36 * d
        val hasBar = secs.any { it.first == Sec.BAR }
        val flexible = box.h - gap * (secs.size - 1) - (if (hasBar) barH else 0f)
        val sum = secs.sumOf { it.second.toDouble() }.toFloat()
        var y = box.t
        for ((sec, frac) in secs) {
            val sh = if (sec == Sec.BAR) barH else flexible * frac / sum
            val sb = Box(box.l, y, box.r, y + sh)
            when (sec) {
                Sec.CLOCK -> drawClockRow(ctx, c, sb, d, st, plan, allowWeather = true)
                Sec.PROGRESS -> {
                    drawProgress(c, sb, d, st)
                    plan.progressHit = sb
                }
                Sec.BAR -> drawBar(ctx, c, sb, d, st, plan, withToggles = true)
                Sec.GRID -> {
                    drawGrid(c, sb, d, st)
                    plan.gridHit = sb
                }
            }
            y += sh + gap
        }
    }

    // ---------------------------------------------------------------- Reloj + tiempo

    private fun drawClockRow(
        ctx: Context, c: Canvas, box: Box, d: Float, st: State, plan: Plan, allowWeather: Boolean,
    ) {
        val t = st.theme
        val wide = allowWeather && box.w / d >= 230
        val leftW = if (wide) box.w * 0.56f else box.w
        val dayH = box.h * 0.2f
        val clockH = box.h * 0.46f
        val left = Box(box.l, box.t, box.l + leftW, box.b)

        // Día de la semana en puntos
        val inlineTemp = allowWeather && !wide && st.weather != null
        val labelRight = if (inlineTemp) left.r - 44 * d else left.r - 8 * d
        drawSpacedDots(c, DAYS[st.now.dayOfWeek.value - 1], Box(left.l, left.t, labelRight, left.t + dayH), t.accent)

        // Hora con cifras de bloques (se repinta cada minuto)
        val clockBox = Box(left.l, left.t + dayH, left.r, left.t + dayH + clockH)
        val cell = BlockDigits.cellFor(clockBox.w - 8 * d, clockBox.h * 0.86f)
        BlockDigits.draw(
            c, String.format("%02d%02d", st.now.hour, st.now.minute),
            clockBox.l, clockBox.cy - BlockDigits.height(cell) / 2f, cell, t.text,
        )
        plan.clockHit = left

        // Arco del sol
        val wx = st.weather
        drawArc(c, Box(left.l, clockBox.b, left.r - 8 * d, box.b), d, t,
            (st.now.hour * 60 + st.now.minute) / 1440f,
            wx?.sunriseMin ?: (7 * 60 + 45), wx?.sunsetMin ?: (18 * 60))

        if (wide) {
            val divX = box.l + leftW + 4 * d
            c.drawLine(divX, box.t + 4 * d, divX, box.b - 4 * d, paint(t.surface).apply { strokeWidth = 1.2f * d })
            val wb = Box(divX + 14 * d, box.t, box.r, box.b)
            drawWeather(c, wb, d, t, st.city, wx)
            plan.weatherHit = Box(divX, box.t, box.r, box.b)
        } else if (inlineTemp && wx != null) {
            // Sin columna de tiempo: la temperatura va junto al día
            run {
                val p = textPaint(t.dim, min(dayH * 0.7f, 13 * d)).apply { textAlign = Paint.Align.RIGHT }
                drawTextV(c, "${wx.temp.roundToInt()}°", box.r, box.t + dayH / 2f, p)
            }
        }
    }

    private fun drawSpacedDots(c: Canvas, text: String, box: Box, color: Int) {
        val cell = min(box.h * 0.9f / 7f, box.w / (text.length * 7.5f))
        val p = paint(color)
        var x = box.l
        val y = box.t + (box.h - DotFont.height(cell)) / 2f
        for (ch in text) {
            DotFont.draw(c, ch.toString(), x, y, cell, p)
            x += 7.5f * cell
        }
    }

    /** Eje X = día completo; la curva sube hasta el mediodía solar. Sobre el horizonte, de día. */
    private fun drawArc(c: Canvas, box: Box, d: Float, t: DotTheme, nowFrac: Float, sunriseMin: Int, sunsetMin: Int) {
        val rise = sunriseMin / 1440f
        val set = sunsetMin / 1440f
        val noon = (rise + set) / 2f
        val labelH = box.h * 0.32f
        val r = min(box.h * 0.08f, 5.5f * d)
        val top = box.t + r * 2f
        val bottom = box.b - labelH - r
        val left = box.l + r
        val right = box.r - r

        fun yv(f: Float): Float = cos(2.0 * PI * (f - noon)).toFloat()
        fun px(f: Float): Float = left + f * (right - left)
        fun py(v: Float): Float = bottom - (v + 1f) / 2f * (bottom - top)

        val path = Path()
        for (i in 0..160) {
            val f = i / 160f
            if (i == 0) path.moveTo(px(f), py(yv(f))) else path.lineTo(px(f), py(yv(f)))
        }
        val horizon = py(yv(rise))
        c.drawLine(box.l, horizon, box.r, horizon, paint(t.dim).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f * d
            pathEffect = DashPathEffect(floatArrayOf(3 * d, 3 * d), 0f)
        })
        c.drawPath(path, paint(t.dim).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * d })
        c.save()
        c.clipRect(box.l, box.t, box.r, horizon)
        c.drawPath(path, paint(t.text).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * d })
        c.restore()

        val isDay = nowFrac in rise..set
        val dotColor = if (isDay) SUN else t.text
        val dx = px(nowFrac)
        val dy = py(yv(nowFrac))
        c.drawCircle(dx, dy, r * 2f, paint(dotColor).apply { alpha = 50 })
        c.drawCircle(dx, dy, r, paint(dotColor))

        val lp = textPaint(t.dim, min(labelH * 0.62f, 11 * d)).apply { textAlign = Paint.Align.CENTER }
        val ly = box.b - labelH / 2f
        val riseTxt = "↑" + hhmm(sunriseMin)
        val setTxt = "↓" + hhmm(sunsetMin)
        val h1 = lp.measureText(riseTxt) / 2f
        val h2 = lp.measureText(setTxt) / 2f
        drawTextV(c, riseTxt, px(rise).coerceIn(box.l + h1, box.r - h1), ly, lp)
        drawTextV(c, setTxt, px(set).coerceIn(box.l + h2, box.r - h2), ly, lp)
    }

    private fun drawWeather(c: Canvas, box: Box, d: Float, t: DotTheme, city: String, wx: Prefs.Weather?) {
        val H = box.h
        val cityP = textPaint(t.dim, min(H * 0.11f, 12 * d))
        drawTextV(c, ellipsize(city.uppercase(), cityP, box.w), box.l, box.t + H * 0.08f, cityP)

        val temp = if (wx == null) "--°" else "${wx.temp.roundToInt()}°"
        val tempArea = H * 0.42f
        val cell = min(tempArea * 0.85f / 7f, box.w / (temp.length * 6f))
        DotFont.draw(c, temp, box.l, box.t + H * 0.18f + (tempArea - DotFont.height(cell)) / 2f, cell, paint(t.text))

        val condP = textPaint(t.text, min(H * 0.12f, 14 * d))
        val cond = if (wx == null) "Toca para actualizar" else condition(wx.code)
        drawTextV(c, ellipsize(cond, condP, box.w), box.l, box.t + H * 0.72f, condP)
        if (wx != null) {
            val mm = textPaint(t.dim, min(H * 0.11f, 12 * d))
            drawTextV(c, "↑${wx.max.roundToInt()}°   ↓${wx.min.roundToInt()}°", box.l, box.t + H * 0.9f, mm)
        }
    }

    // ---------------------------------------------------------------- Progreso

    private fun drawProgress(c: Canvas, box: Box, d: Float, st: State) {
        val t = st.theme
        val now = st.now
        val fracDay = (now.hour * 60 + now.minute) / 1440f
        val ym = YearMonth.of(now.year, now.monthValue)
        val daysInYear = if (ym.isLeapYear) 366 else 365

        data class Row(val label: String, val n: Int, val cur: Int, val pct: Float)
        val rows = listOf(
            Row(now.year.toString(), 12, now.monthValue - 1, (now.dayOfYear - 1 + fracDay) / daysInYear),
            Row(MONTHS[now.monthValue - 1].uppercase(), ym.lengthOfMonth(), now.dayOfMonth - 1,
                (now.dayOfMonth - 1 + fracDay) / ym.lengthOfMonth()),
            Row("DIA ${now.dayOfMonth}", 24, now.hour, fracDay),
            Row(String.format("%02d:%02d", now.hour, now.minute), 12, now.minute / 5, now.minute / 60f),
        )

        val rowH = box.h / rows.size
        val labelW = box.w * 0.27f
        val pctW = max(box.w * 0.15f, 34 * d)
        val dotsL = box.l + labelW + 4 * d
        val dotsR = box.r - pctW
        val cell = min(rowH * 0.5f / 7f, labelW * 0.92f / 35f)
        val labelP = paint(t.text)
        val filled = paint(t.text)
        val acc = paint(t.accent)
        val pctP = textPaint(t.dim, min(rowH * 0.42f, 13 * d)).apply { textAlign = Paint.Align.RIGHT }

        rows.forEachIndexed { i, r ->
            val cy = box.t + rowH * (i + 0.5f)
            DotFont.draw(c, r.label, box.l, cy - DotFont.height(cell) / 2f, cell, labelP)
            val sp = (dotsR - dotsL) / r.n
            val rad = min(sp * 0.36f, rowH * 0.28f)
            val sw = max(1f, rad * 0.3f)
            val off = paint(t.dim).apply { style = Paint.Style.STROKE; strokeWidth = sw }
            for (k in 0 until r.n) {
                val x = dotsL + sp * (k + 0.5f)
                when {
                    k < r.cur -> c.drawCircle(x, cy, rad, filled)
                    k == r.cur -> c.drawCircle(x, cy, rad, acc)
                    else -> c.drawCircle(x, cy, rad - sw / 2f, off)
                }
            }
            drawTextV(c, "${(r.pct * 100).toInt()}%", box.r, cy, pctP)
        }
    }

    // ---------------------------------------------------------------- Barra: mes, año, botones, tema

    private fun drawBar(ctx: Context, c: Canvas, box: Box, d: Float, st: State, plan: Plan, withToggles: Boolean) {
        val t = st.theme
        val chipH = box.h
        val cy = box.cy
        val gap = 8 * d

        // Tema (derecha): chip con nombre si cabe, si no solo un círculo
        val tp = textPaint(t.text, chipH * 0.42f)
        val dotR = chipH * 0.17f
        val full = box.w / d >= 280
        val themeW = if (full) chipH * 0.4f + dotR * 2 + chipH * 0.22f + tp.measureText(t.name) + chipH * 0.45f else chipH
        val themeBox = Box(box.r - themeW, box.t, box.r, box.b)
        val stroke = paint(t.surface).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
        c.drawRoundRect(themeBox.l, themeBox.t, themeBox.r, themeBox.b, chipH / 2, chipH / 2, stroke)
        if (full) {
            c.drawCircle(themeBox.l + chipH * 0.4f + dotR, cy, dotR, paint(t.accent))
            drawTextV(c, t.name, themeBox.l + chipH * 0.4f + dotR * 2 + chipH * 0.22f, cy, tp)
        } else {
            c.drawCircle(themeBox.cx, cy, chipH * 0.22f, paint(t.accent))
        }
        plan.theme = themeBox

        // Botones de ahorro y ubicación
        var limit = themeBox.l - gap
        if (withToggles) {
            val saverBox = Box(themeBox.l - gap - chipH, box.t, themeBox.l - gap, box.b)
            val locBox = Box(saverBox.l - gap - chipH, box.t, saverBox.l - gap, box.b)
            drawToggle(ctx, c, locBox, d, t, R.drawable.ic_location, st.locOn)
            drawToggle(ctx, c, saverBox, d, t, R.drawable.ic_battery_saver, st.saverOn)
            plan.loc = locBox
            plan.saver = saverBox
            limit = locBox.l - gap
        }

        // Chips de mes y año (izquierda), solo los que quepan
        val cp = textPaint(t.text, chipH * 0.46f, sansMedium)
        var x = box.l
        for (label in listOf(MONTHS[st.now.monthValue - 1], st.now.year.toString())) {
            val tw = cp.measureText(label)
            val cw = tw + chipH * 0.9f
            if (x + cw > limit) break
            c.drawRoundRect(x, box.t, x + cw, box.b, chipH / 2, chipH / 2, paint(t.surface))
            drawTextV(c, label, x + (cw - tw) / 2f, cy, cp)
            x += cw + 6 * d
        }
        plan.chipsHit = Box(box.l, box.t, max(box.l + 1, limit), box.b)
    }

    private fun drawToggle(ctx: Context, c: Canvas, box: Box, d: Float, t: DotTheme, iconRes: Int, active: Boolean) {
        val r = min(box.w, box.h) / 2f
        c.drawCircle(box.cx, box.cy, r, paint(if (active) t.accent else t.surface))
        val icon = ctx.getDrawable(iconRes)?.mutate() ?: return
        icon.setTint(if (active) t.onAccent else t.text)
        val half = (r * 0.55f).toInt()
        val cx = box.cx.toInt()
        val cy = box.cy.toInt()
        icon.setBounds(cx - half, cy - half, cx + half, cy + half)
        icon.draw(c)
    }

    // ---------------------------------------------------------------- Tiempo compacto

    private fun drawWeatherCompact(c: Canvas, box: Box, d: Float, st: State) {
        val t = st.theme
        val wx = st.weather
        val temp = if (wx == null) "--°" else "${wx.temp.roundToInt()}°"
        val cell = min(box.h * 0.7f / 7f, box.w * 0.48f / (temp.length * 6f))
        DotFont.draw(c, temp, box.l, box.cy - DotFont.height(cell) / 2f, cell, paint(t.text))
        val tx = box.l + DotFont.width(temp, cell) + 10 * d
        val tw = box.r - tx
        if (tw < 30 * d) return
        val small = min(box.h * 0.22f, 12 * d)
        val cityP = textPaint(t.dim, small)
        val condP = textPaint(t.text, min(box.h * 0.26f, 14 * d))
        val mmP = textPaint(t.dim, small)
        drawTextV(c, ellipsize(st.city.uppercase(), cityP, tw), tx, box.t + box.h * 0.2f, cityP)
        val cond = if (wx == null) "Toca para actualizar" else condition(wx.code)
        drawTextV(c, ellipsize(cond, condP, tw), tx, box.cy, condP)
        if (wx != null) {
            drawTextV(c, ellipsize("↑${wx.max.roundToInt()}°  ↓${wx.min.roundToInt()}°", mmP, tw), tx, box.t + box.h * 0.8f, mmP)
        }
    }

    // ---------------------------------------------------------------- Widget de botones

    /** Ubicación, ahorro y tema en fila (si es ancho) o en columna. */
    private fun drawButtons(ctx: Context, c: Canvas, box: Box, d: Float, st: State, plan: Plan) {
        val t = st.theme
        val horizontal = box.w >= box.h
        val along = if (horizontal) box.w else box.h
        val cross = if (horizontal) box.h else box.w
        val gap = 10 * d
        val size = min(cross, (along - 2 * gap) / 3f)
        val total = size * 3 + gap * 2
        val start = (if (horizontal) box.l else box.t) + (along - total) / 2f
        val boxes = (0 until 3).map { i ->
            val a = start + i * (size + gap)
            if (horizontal) Box(a, box.cy - size / 2, a + size, box.cy + size / 2)
            else Box(box.cx - size / 2, a, box.cx + size / 2, a + size)
        }
        drawToggle(ctx, c, boxes[0], d, t, R.drawable.ic_location, st.locOn)
        drawToggle(ctx, c, boxes[1], d, t, R.drawable.ic_battery_saver, st.saverOn)
        val th = boxes[2]
        val r = th.w / 2f
        c.drawCircle(th.cx, th.cy, r - 0.75f * d, paint(t.surface).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d })
        c.drawCircle(th.cx, th.cy, r * 0.32f, paint(t.accent))
        plan.loc = boxes[0]
        plan.saver = boxes[1]
        plan.theme = th
    }

    // ---------------------------------------------------------------- Música

    private fun drawMusic(ctx: Context, c: Canvas, box: Box, d: Float, st: State, plan: Plan) {
        val t = st.theme
        val m = st.music
        val radius = 16 * d
        c.drawRoundRect(box.l, box.t, box.r, box.b, radius, radius, paint(t.surface))
        val inner = 8 * d
        val cs = box.h - 2 * inner
        val cover = Box(box.l + inner, box.t + inner, box.l + inner + cs, box.t + inner + cs)

        // Portada
        val coverPath = Path().apply {
            addRoundRect(RectF(cover.l, cover.t, cover.r, cover.b), 10 * d, 10 * d, Path.Direction.CW)
        }
        c.save()
        c.clipPath(coverPath)
        val art = m?.art
        if (art != null) {
            val side = min(art.width, art.height)
            val sx = (art.width - side) / 2
            val sy = (art.height - side) / 2
            c.drawBitmap(
                art, Rect(sx, sy, sx + side, sy + side),
                RectF(cover.l, cover.t, cover.r, cover.b),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            c.drawRect(cover.l, cover.t, cover.r, cover.b, paint(t.bg))
            // nota musical en puntos
            val cell = cs / 11f
            val note = arrayOf("...##", "...#.#", "...#..", "...#..", ".###..", "####..", ".##...")
            val p = paint(t.accent)
            for (row in note.indices) for (col in note[row].indices) {
                if (note[row][col] == '#') {
                    c.drawCircle(cover.l + (col + 2.5f) * cell, cover.t + (row + 2f) * cell, cell * 0.42f, p)
                }
            }
        }
        c.restore()

        // Controles a la derecha: anterior · play/pausa · siguiente
        val bs = min(box.h * 0.42f, 38 * d)
        val cy = box.cy
        val nextB = Box(box.r - inner - bs, cy - bs / 2, box.r - inner, cy + bs / 2)
        val playB = Box(nextB.l - 4 * d - bs, cy - bs / 2, nextB.l - 4 * d, cy + bs / 2)
        val prevB = Box(playB.l - 4 * d - bs, cy - bs / 2, playB.l - 4 * d, cy + bs / 2)
        c.drawCircle(playB.cx, playB.cy, bs / 2f, paint(t.accent))
        drawMediaIcon(c, playB, if (m?.playing == true) "pause" else "play", t.onAccent)
        drawMediaIcon(c, prevB, "prev", t.text)
        drawMediaIcon(c, nextB, "next", t.text)
        plan.prev = prevB
        plan.play = playB
        plan.next = nextB

        // Título y artista
        val tx = cover.r + 12 * d
        val tw = prevB.l - 8 * d - tx
        if (tw > 30 * d) {
            val titleP = textPaint(t.text, min(box.h * 0.2f, 15 * d), sansMedium)
            val artistP = textPaint(t.dim, min(box.h * 0.17f, 13 * d))
            val title = when {
                m == null || !m.hasAccess -> "Música"
                m.title.isNullOrBlank() -> "Nada sonando"
                else -> m.title
            }
            val artist = when {
                m == null || !m.hasAccess -> "Activa el acceso en la app"
                m.title.isNullOrBlank() -> "Abre Spotify o YouTube Music"
                else -> m.artist ?: ""
            }
            drawTextV(c, ellipsize(title, titleP, tw), tx, box.cy - box.h * 0.13f, titleP)
            drawTextV(c, ellipsize(artist, artistP, tw), tx, box.cy + box.h * 0.15f, artistP)
        }
        plan.musicHit = Box(box.l, box.t, prevB.l - 2 * d, box.b)
    }

    /**
     * Música cuadrada: solo la portada. Zonas táctiles invisibles:
     * izquierda = anterior, centro = pausa/play (o abrir app), derecha = siguiente.
     * Abajo a la izquierda, 4 columnas de puntos grises (ecualizador): animadas cuando suena.
     */
    private fun drawMusicTile(c: Canvas, box: Box, d: Float, st: State, plan: Plan, radius: Float) {
        val t = st.theme
        val m = st.music
        val clip = Path().apply { addRoundRect(RectF(box.l, box.t, box.r, box.b), radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)

        val art = m?.art
        if (art != null) {
            val ratio = box.w / box.h
            var sw = art.width
            var sh = (art.width / ratio).toInt()
            if (sh > art.height) {
                sh = art.height
                sw = (art.height * ratio).toInt()
            }
            val sx = (art.width - sw) / 2
            val sy = (art.height - sh) / 2
            c.drawBitmap(art, Rect(sx, sy, sx + sw, sy + sh), RectF(box.l, box.t, box.r, box.b),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            // Sin portada: nota musical en puntos, centrada
            c.drawRect(box.l, box.t, box.r, box.b, paint(if (t.bg ushr 24 == 0) t.surface else t.bg))
            val cell = min(box.w, box.h) / 12f
            val note = arrayOf("....##", "....#.#", "....#..", "....#..", "..###..", ".####..", "..##...")
            val p = paint(if (m?.playing == true) t.accent else t.dim)
            val ox = box.cx - 3.5f * cell
            val oy = box.cy - 3.5f * cell
            for (row in note.indices) for (col in note[row].indices) {
                if (note[row][col] == '#') c.drawCircle(ox + col * cell + cell / 2, oy + row * cell + cell / 2, cell * 0.42f, p)
            }
        }

        // Ecualizador: 4 columnas × 5 puntos, abajo a la izquierda
        val eqW = min(box.w, box.h) * 0.16f
        val eqH = eqW * 30f / 24f
        val pad = min(box.w, box.h) * 0.07f
        val eqBox = Box(box.l + pad, box.b - pad - eqH, box.l + pad + eqW, box.b - pad)
        if (m?.playing == true) {
            plan.eq = eqBox // lo anima un ProgressBar encima
        } else {
            drawEqStatic(c, eqBox)
        }

        // Respuesta al tocar: icono grande un instante
        val fb = st.feedback
        if (fb != null) {
            val r = min(box.w, box.h) * 0.17f
            c.drawCircle(box.cx, box.cy, r, paint(0x000000).apply { alpha = 120 })
            drawMediaIcon(c, Box(box.cx - r, box.cy - r, box.cx + r, box.cy + r), fb, 0xFFFFFFFF.toInt())
        }
        c.restore()

        val third = box.w * 0.3f
        plan.prev = Box(box.l, box.t, box.l + third, box.b)
        plan.next = Box(box.r - third, box.t, box.r, box.b)
        plan.play = Box(box.l + third, box.t, box.r - third, box.b)
    }

    /** Ecualizador quieto (en pausa): solo la fila de abajo. Misma geometría que eq_*.xml. */
    private fun drawEqStatic(c: Canvas, b: Box) {
        val sx = b.w / 24f
        val sy = b.h / 30f
        val on = paint(0xFFD9D9D9.toInt()).apply { alpha = 220 }
        for (col in 0 until 4) {
            c.drawCircle(b.l + (3f + col * 6f) * sx, b.t + 27f * sy, 2.2f * sx, on)
        }
    }

    private fun drawMediaIcon(c: Canvas, b: Box, type: String, color: Int) {
        val p = paint(color)
        val s = b.w * 0.36f
        val x = b.cx
        val y = b.cy
        val path = Path()
        when (type) {
            "play" -> {
                path.moveTo(x - s * 0.38f, y - s * 0.5f)
                path.lineTo(x + s * 0.55f, y)
                path.lineTo(x - s * 0.38f, y + s * 0.5f)
                path.close()
                c.drawPath(path, p)
            }
            "pause" -> {
                val bw = s * 0.28f
                c.drawRoundRect(x - s * 0.42f, y - s * 0.48f, x - s * 0.42f + bw, y + s * 0.48f, bw / 3, bw / 3, p)
                c.drawRoundRect(x + s * 0.42f - bw, y - s * 0.48f, x + s * 0.42f, y + s * 0.48f, bw / 3, bw / 3, p)
            }
            "next" -> {
                path.moveTo(x - s * 0.5f, y - s * 0.45f)
                path.lineTo(x + s * 0.3f, y)
                path.lineTo(x - s * 0.5f, y + s * 0.45f)
                path.close()
                c.drawPath(path, p)
                c.drawRect(x + s * 0.32f, y - s * 0.45f, x + s * 0.5f, y + s * 0.45f, p)
            }
            "prev" -> {
                path.moveTo(x + s * 0.5f, y - s * 0.45f)
                path.lineTo(x - s * 0.3f, y)
                path.lineTo(x + s * 0.5f, y + s * 0.45f)
                path.close()
                c.drawPath(path, p)
                c.drawRect(x - s * 0.5f, y - s * 0.45f, x - s * 0.32f, y + s * 0.45f, p)
            }
        }
    }

    // ---------------------------------------------------------------- Día + hora pequeños

    private fun drawDayTime(c: Canvas, box: Box, d: Float, st: State) {
        val t = st.theme
        val dayH = box.h * 0.3f
        drawSpacedDots(c, DAYS[st.now.dayOfWeek.value - 1], Box(box.l, box.t, box.r - 4 * d, box.t + dayH), t.accent)
        val cb = Box(box.l, box.t + dayH + 4 * d, box.r, box.b)
        val cell = BlockDigits.cellFor(cb.w - 4 * d, cb.h * 0.92f)
        BlockDigits.draw(
            c, String.format("%02d%02d", st.now.hour, st.now.minute),
            cb.l, cb.cy - BlockDigits.height(cell) / 2f, cell, t.text,
        )
    }

    // ---------------------------------------------------------------- Semana (calendario pequeño)

    /**
     * Calendario pequeño por semanas. Intenta semana pasada + actual + siguiente;
     * si no caben legibles, actual + siguiente.
     */
    private fun drawWeek(c: Canvas, box: Box, d: Float, st: State) {
        val t = st.theme
        val now = st.now
        val today = now.toLocalDate()
        val monday = today.minusDays((now.dayOfWeek.value - 1).toLong())
        val colW = box.w / 7f

        val headH = min(box.h * 0.2f, 16 * d)
        val weeks: List<Long>
        val showLabel: Boolean
        if ((box.h - headH) / 3f >= 17 * d) {
            weeks = listOf(-1L, 0L, 1L)
            showLabel = false
        } else {
            weeks = listOf(0L, 1L)
            showLabel = (box.h - headH - box.h * 0.26f) / 2f >= 16 * d
        }
        val labelH = if (showLabel) box.h * 0.26f else 0f
        val rowH = (box.h - labelH - headH) / weeks.size

        if (showLabel) {
            val lp = textPaint(t.dim, min(labelH * 0.6f, 12 * d))
            drawTextV(c, "${MONTHS[now.monthValue - 1]} ${now.year}", box.l + colW * 0.15f, box.t + labelH / 2f, lp)
        }
        val head = textPaint(t.dim, min(headH * 0.62f, colW * 0.38f)).apply { textAlign = Paint.Align.CENTER }
        val size = min(rowH * 0.52f, colW * 0.42f)
        val dayP = textPaint(t.text, size).apply { textAlign = Paint.Align.CENTER }
        val pastP = textPaint(t.dim, size).apply { textAlign = Paint.Align.CENTER }
        val todayP = textPaint(t.onAccent, size, sansMedium).apply { textAlign = Paint.Align.CENTER }
        val rr = min(rowH, colW) * 0.44f
        for (i in 0 until 7) drawTextV(c, WEEK_HEAD[i], box.l + colW * (i + 0.5f), box.t + labelH + headH / 2f, head)
        weeks.forEachIndexed { r, offset ->
            val cy = box.t + labelH + headH + rowH * (r + 0.5f)
            for (i in 0 until 7) {
                val day = monday.plusDays(offset * 7 + i)
                val cx = box.l + colW * (i + 0.5f)
                when {
                    day == today -> {
                        c.drawCircle(cx, cy, rr, paint(t.accent))
                        drawTextV(c, day.dayOfMonth.toString(), cx, cy, todayP)
                    }
                    day.isBefore(today) -> drawTextV(c, day.dayOfMonth.toString(), cx, cy, pastP)
                    else -> drawTextV(c, day.dayOfMonth.toString(), cx, cy, dayP)
                }
            }
        }
    }

    // ---------------------------------------------------------------- Calendario

    private fun drawGrid(c: Canvas, box: Box, d: Float, st: State) {
        val t = st.theme
        val now = st.now
        val ym = YearMonth.of(now.year, now.monthValue)
        val days = ym.lengthOfMonth()
        val offset = ym.atDay(1).dayOfWeek.value - 1
        val weeks = ceil((offset + days) / 7.0).toInt()
        val rowH = box.h / (weeks + 1)
        val colW = box.w / 7f

        val head = textPaint(t.dim, min(rowH * 0.36f, colW * 0.38f)).apply { textAlign = Paint.Align.CENTER }
        for (i in 0 until 7) drawTextV(c, WEEK_HEAD[i], box.l + colW * (i + 0.5f), box.t + rowH / 2f, head)

        val dayP = textPaint(t.text, min(rowH * 0.46f, colW * 0.42f)).apply { textAlign = Paint.Align.CENTER }
        val todayP = textPaint(t.onAccent, dayP.textSize, sansMedium).apply { textAlign = Paint.Align.CENTER }
        val circle = paint(t.accent)
        val rr = min(rowH, colW) * 0.44f
        for (day in 1..days) {
            val idx = offset + day - 1
            val dcx = box.l + colW * (idx % 7 + 0.5f)
            val dcy = box.t + rowH * (idx / 7 + 1.5f)
            if (day == now.dayOfMonth) {
                c.drawCircle(dcx, dcy, rr, circle)
                drawTextV(c, day.toString(), dcx, dcy, todayP)
            } else {
                drawTextV(c, day.toString(), dcx, dcy, dayP)
            }
        }
    }

    // ---------------------------------------------------------------- Utilidades

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var e = s
        while (e.length > 1 && p.measureText("$e…") > maxW) e = e.dropLast(1)
        return "$e…"
    }

    fun condition(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Casi despejado"
        2 -> "Parcialmente nuboso"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        in 51..57 -> "Llovizna"
        in 61..67 -> "Lluvia"
        in 71..77 -> "Nieve"
        in 80..82 -> "Chubascos"
        85, 86 -> "Chubascos de nieve"
        in 95..99 -> "Tormenta"
        else -> "—"
    }

    /** Título en puntos para la app. */
    fun dotLabel(w: Int, h: Int, text: String, color: Int): Bitmap {
        val b = bmp(w, h)
        drawSpacedDots(Canvas(b), text, Box(0f, 0f, w.toFloat(), h.toFloat()), color)
        return b
    }

    /** Muestra de color para la lista de temas de la app. */
    fun swatch(ctx: Context, t: DotTheme): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val s = (40 * d).toInt()
        val b = bmp(s, s)
        val c = Canvas(b)
        val rect = RectF(0f, 0f, s.toFloat(), s.toFloat())
        c.drawRoundRect(rect, 10 * d, 10 * d, paint(t.bg))
        c.drawRoundRect(rect, 10 * d, 10 * d, paint(0x33888888).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d })
        c.drawCircle(s * 0.35f, s * 0.5f, s * 0.13f, paint(t.text))
        c.drawCircle(s * 0.65f, s * 0.5f, s * 0.13f, paint(t.accent))
        return b
    }
}
