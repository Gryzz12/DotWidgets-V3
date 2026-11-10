package com.gryzz.dotwidgets

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.net.Uri
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private val red = 0xFFD71921.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(list, MATCH_PARENT, WRAP_CONTENT)
        })
    }

    override fun onResume() {
        super.onResume()
        build()
        Thread { Updater.updateAll(applicationContext) }.start()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        list.removeAllViews()

        list.addView(ImageView(this).apply {
            setImageBitmap(Renderer.dotLabel(dp(260), dp(30), "DOT WIDGETS", Color.WHITE))
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(30)))

        para("Mantén pulsado el escritorio → Widgets → Dot Widgets. Hay 7: Todo (ideal 5×3), Reloj y tiempo, Progreso, Calendario, Reloj y progreso, Botones y Todo + música. Todos se pueden estirar y se reorganizan solos.")

        // ---- Temas
        header("TEMA")
        para("También puedes cambiarlo tocando el botón del tema en el widget.")
        val current = Prefs.themeIndex(this)
        Themes.all.forEachIndexed { i, t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (i == current) 0xFF222222.toInt() else Color.TRANSPARENT)
                    if (i == current) setStroke(dp(1), red)
                }
                setOnClickListener {
                    Prefs.setTheme(this@MainActivity, i)
                    Thread { Updater.updateAll(applicationContext) }.start()
                    build()
                }
            }
            row.addView(ImageView(this).apply { setImageBitmap(Renderer.swatch(this@MainActivity, t)) })
            row.addView(TextView(this).apply {
                text = if (i == current) "${t.name}  ✓" else t.name
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(dp(14), 0, 0, 0)
            })
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        }

        // ---- Ubicación del tiempo
        header("TIEMPO")
        val wx = Prefs.weather(this)
        para(
            "Ubicación: ${Prefs.city(this)}" +
                if (wx != null) "  ·  ${Math.round(wx.temp)}° ${Renderer.condition(wx.code)}" else "  ·  sin datos aún",
        )
        val cityInput = EditText(this).apply {
            hint = "Buscar ciudad (p. ej. Lorca)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF777777.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        list.addView(cityInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        button("Buscar ciudad") { searchCity(cityInput.text.toString()) }
        cityInput.setOnEditorActionListener { _, _, _ -> searchCity(cityInput.text.toString()); true }
        button("Usar mi ubicación actual") { useMyLocation() }
        button("Actualizar tiempo ahora") {
            Thread {
                val ok = Weather.fetch(applicationContext)
                Updater.updateAll(applicationContext)
                runOnUiThread { toast(if (ok) "Tiempo actualizado" else "No hay conexión"); build() }
            }.start()
        }

        // ---- Música
        header("MÚSICA")
        if (Music.hasAccess(this)) {
            para("✓ Acceso concedido. El widget Todo + música muestra la portada y controla la canción que suene (Spotify, YouTube Music…).")
        } else {
            para(
                "Para ver la portada y controlar la música, activa Dot Widgets en \"Acceso a notificaciones\". " +
                    "Si Android dice que es un ajuste restringido: abre Info de la app → menú ⋮ (arriba a la derecha) → " +
                    "\"Permitir ajustes restringidos\", y vuelve a intentarlo.",
            )
            button("Abrir acceso a notificaciones") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            button("Abrir info de la app") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }

        // ---- Botones
        header("BOTONES DE UBICACIÓN Y AHORRO")
        if (Toggles.hasSecure(this)) {
            para("✓ Permiso concedido: los botones activan y desactivan directamente. El que está encendido se pinta con el color de acento.")
        } else {
            para(
                "Ahora mismo los botones abren el ajuste de Android. Para que activen/desactiven con un toque, " +
                    "concede este permiso una sola vez desde un ordenador con ADB (depuración USB activada):",
            )
            list.addView(TextView(this).apply {
                text = Toggles.ADB_CMD
                setTextColor(0xFFFFC21A.toInt())
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFF1A1A1A.toInt()) }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            button("Copiar comando") {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("adb", Toggles.ADB_CMD))
                toast("Copiado")
            }
        }
    }

    private fun header(s: String) {
        list.addView(TextView(this).apply {
            text = s
            setTextColor(red)
            textSize = 13f
            letterSpacing = 0.15f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(28) })
    }

    private fun para(s: String) {
        list.addView(TextView(this).apply {
            text = s
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 14f
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun button(label: String, onClick: () -> Unit) {
        list.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0xFF2A2A2A.toInt()) }
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)).apply { topMargin = dp(8) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun searchCity(name: String) {
        if (name.isBlank()) return
        Thread {
            val r = Weather.searchCity(name.trim())
            if (r == null) {
                runOnUiThread { toast("No encontré esa ciudad") }
            } else {
                Prefs.setLocation(applicationContext, r.first, r.second, r.third)
                Weather.fetch(applicationContext)
                Updater.updateAll(applicationContext)
                runOnUiThread { toast("Ubicación: ${r.third}"); build() }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun useMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 7)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
            if (l != null && (best == null || l.time > best.time)) best = l
        }
        val found: Location? = best
        if (found != null && System.currentTimeMillis() - found.time < 60 * 60 * 1000L) {
            saveLocation(found)
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                LocationManager.GPS_PROVIDER
            }
            toast("Buscando ubicación…")
            lm.getCurrentLocation(prov, null, mainExecutor) { loc ->
                val l = loc ?: found
                if (l != null) saveLocation(l) else toast("No se pudo obtener la ubicación. ¿Está activada?")
            }
        } else if (found != null) {
            saveLocation(found)
        } else {
            toast("No se pudo obtener la ubicación")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) useMyLocation()
    }

    private fun saveLocation(l: Location) {
        Thread {
            val name = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale("es", "ES")).getFromLocation(l.latitude, l.longitude, 1)
                    ?.firstOrNull()?.locality ?: "Mi ubicación"
            } catch (e: Exception) {
                "Mi ubicación"
            }
            Prefs.setLocation(applicationContext, l.latitude, l.longitude, name)
            Weather.fetch(applicationContext)
            Updater.updateAll(applicationContext)
            runOnUiThread { toast("Ubicación: $name"); build() }
        }.start()
    }
}
