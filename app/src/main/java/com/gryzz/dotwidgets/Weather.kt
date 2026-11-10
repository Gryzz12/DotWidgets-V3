package com.gryzz.dotwidgets

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Tiempo de Open-Meteo (gratis, sin clave). */
object Weather {
    private const val MAX_AGE = 30 * 60 * 1000L

    fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "DotWidgets/1.0")
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun maybeFetch(ctx: Context, force: Boolean): Boolean {
        val age = System.currentTimeMillis() - Prefs.weatherTime(ctx)
        if (!force && age < MAX_AGE) return false
        return fetch(ctx)
    }

    fun fetch(ctx: Context): Boolean = try {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code" +
                "&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1",
            Prefs.lat(ctx), Prefs.lon(ctx),
        )
        val j = JSONObject(httpGet(url))
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        fun minutes(iso: String): Int {
            // "2026-11-08T07:52"
            val hm = iso.substringAfter('T')
            return hm.substring(0, 2).toInt() * 60 + hm.substring(3, 5).toInt()
        }
        Prefs.saveWeather(
            ctx,
            Prefs.Weather(
                temp = cur.getDouble("temperature_2m").toFloat(),
                code = cur.getInt("weather_code"),
                max = daily.getJSONArray("temperature_2m_max").getDouble(0).toFloat(),
                min = daily.getJSONArray("temperature_2m_min").getDouble(0).toFloat(),
                sunriseMin = minutes(daily.getJSONArray("sunrise").getString(0)),
                sunsetMin = minutes(daily.getJSONArray("sunset").getString(0)),
                time = System.currentTimeMillis(),
            ),
        )
        true
    } catch (e: Exception) {
        Log.w("DotWidgets", "Tiempo no disponible", e)
        false
    }

    /** Busca una ciudad y devuelve (lat, lon, nombre) o null. */
    fun searchCity(name: String): Triple<Double, Double, String>? = try {
        val q = URLEncoder.encode(name, "UTF-8")
        val j = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=es&format=json"))
        val arr = j.optJSONArray("results")
        if (arr == null || arr.length() == 0) {
            null
        } else {
            val r = arr.getJSONObject(0)
            Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"))
        }
    } catch (e: Exception) {
        null
    }
}
