package com.gryzz.dotwidgets

import android.content.Context
import android.content.SharedPreferences

data class DotTheme(
    val name: String,
    val bg: Int,
    val surface: Int,
    val text: Int,
    val dim: Int,
    val accent: Int,
    val onAccent: Int,
)

object Themes {
    private fun c(hex: Long): Int = hex.toInt()

    val all: List<DotTheme> = listOf(
        DotTheme("Claro", c(0xFFFFFFFF), c(0xFFEFEFEF), c(0xFF111111), c(0xFF9A9A9A), c(0xFFD71921), c(0xFFFFFFFF)),
        DotTheme("Oscuro", c(0xFF1C1C1E), c(0xFF2C2C2E), c(0xFFF2F2F2), c(0xFF6E6E73), c(0xFFD71921), c(0xFFFFFFFF)),
        DotTheme("Negro", c(0xFF000000), c(0xFF1A1A1A), c(0xFFFFFFFF), c(0xFF5A5A5A), c(0xFFD71921), c(0xFFFFFFFF)),
        // Sin fondo: solo texto y puntos sobre el fondo de pantalla, con distintos colores de refuerzo
        DotTheme("Transparente", c(0x00000000), c(0x40FFFFFF), c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFFD71921), c(0xFFFFFFFF)),
        DotTheme("Transp. azul", c(0x00000000), c(0x40FFFFFF), c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFF8AB4F8), c(0xFFFFFFFF)),
        DotTheme("Transp. verde", c(0x00000000), c(0x40FFFFFF), c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFF7BD389), c(0xFFFFFFFF)),
        DotTheme("Transp. rosa", c(0x00000000), c(0x40FFFFFF), c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFFF28FB0), c(0xFFFFFFFF)),
        DotTheme("Transp. ámbar", c(0x00000000), c(0x40FFFFFF), c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFFFFC21A), c(0xFF1A1A1A)),
        DotTheme("Transp. blanco", c(0x00000000), c(0x40FFFFFF), c(0xFFFFFFFF), c(0x99FFFFFF), c(0xFFFFFFFF), c(0xFF111111)),
        // Translúcidos: se ve bastante el fondo detrás
        DotTheme("Cristal gris", c(0x597A7A7A), c(0x33FFFFFF), c(0xFFFFFFFF), c(0xB3FFFFFF), c(0xFFFFFFFF), c(0xFF222222)),
        DotTheme("Cristal negro", c(0x59000000), c(0x33FFFFFF), c(0xFFFFFFFF), c(0xB3FFFFFF), c(0xFFD71921), c(0xFFFFFFFF)),
        DotTheme("Rojo", c(0xFFD71921), c(0xFFB5121A), c(0xFFFFFFFF), c(0xFFF5A3A6), c(0xFF111111), c(0xFFFFFFFF)),
        DotTheme("Rosa", c(0xFFF2A0B8), c(0xFFF7C3D2), c(0xFF3A1020), c(0xFF8E4A5E), c(0xFFFFFFFF), c(0xFFD23F6C)),
        DotTheme("Crema", c(0xFFF1ECE2), c(0xFFE3DCCD), c(0xFF2B2620), c(0xFF9C9386), c(0xFFE2602B), c(0xFFFFFFFF)),
        DotTheme("Noche", c(0xFF14213D), c(0xFF1F3158), c(0xFFF4F4F4), c(0xFF7A86A3), c(0xFFFCA311), c(0xFF14213D)),
        DotTheme("Bosque", c(0xFF0F1F17), c(0xFF1C3328), c(0xFFE8F2EC), c(0xFF6B8577), c(0xFF7BD389), c(0xFF0F1F17)),
    )
}

/** Preferencias y caché del tiempo. Ubicación por defecto: Lorca. */
object Prefs {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("dot", Context.MODE_PRIVATE)

    fun themeIndex(ctx: Context): Int = sp(ctx).getInt("theme", 1).mod(Themes.all.size)
    fun theme(ctx: Context): DotTheme = Themes.all[themeIndex(ctx)]
    fun setTheme(ctx: Context, i: Int) = sp(ctx).edit().putInt("theme", i.mod(Themes.all.size)).apply()
    fun cycleTheme(ctx: Context) = setTheme(ctx, themeIndex(ctx) + 1)

    fun lat(ctx: Context): Double = sp(ctx).getFloat("lat", 37.677f).toDouble()
    fun lon(ctx: Context): Double = sp(ctx).getFloat("lon", -1.700f).toDouble()
    fun city(ctx: Context): String = sp(ctx).getString("city", "Lorca") ?: "Lorca"
    fun setLocation(ctx: Context, lat: Double, lon: Double, city: String) {
        sp(ctx).edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .putString("city", city)
            .putLong("wTime", 0L)
            .apply()
    }

    fun lastMusicPkg(ctx: Context): String? = sp(ctx).getString("musicPkg", null)
    fun setLastMusicPkg(ctx: Context, pkg: String) {
        if (pkg != lastMusicPkg(ctx)) sp(ctx).edit().putString("musicPkg", pkg).apply()
    }

    class Weather(
        val temp: Float,
        val code: Int,
        val max: Float,
        val min: Float,
        val sunriseMin: Int,
        val sunsetMin: Int,
        val time: Long,
    )

    fun weather(ctx: Context): Weather? {
        val p = sp(ctx)
        val t = p.getLong("wTime", 0L)
        if (t == 0L) return null
        return Weather(
            p.getFloat("wTemp", 0f), p.getInt("wCode", 0),
            p.getFloat("wMax", 0f), p.getFloat("wMin", 0f),
            p.getInt("sunrise", 7 * 60 + 30), p.getInt("sunset", 18 * 60),
            t,
        )
    }

    fun weatherTime(ctx: Context): Long = sp(ctx).getLong("wTime", 0L)

    fun saveWeather(ctx: Context, w: Weather) {
        sp(ctx).edit()
            .putFloat("wTemp", w.temp).putInt("wCode", w.code)
            .putFloat("wMax", w.max).putFloat("wMin", w.min)
            .putInt("sunrise", w.sunriseMin).putInt("sunset", w.sunsetMin)
            .putLong("wTime", w.time)
            .apply()
    }
}
