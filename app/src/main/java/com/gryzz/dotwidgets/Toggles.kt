package com.gryzz.dotwidgets

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Ubicación y ahorro de batería.
 * Con WRITE_SECURE_SETTINGS (concedido una vez por ADB) se activan/desactivan directamente;
 * sin él, los botones abren el ajuste correspondiente.
 */
object Toggles {
    const val PERM = "android.permission.WRITE_SECURE_SETTINGS"
    const val ADB_CMD = "adb shell pm grant com.gryzz.dotwidgets android.permission.WRITE_SECURE_SETTINGS"

    fun hasSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    fun locationOn(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 28) {
            ctx.getSystemService(LocationManager::class.java).isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    }

    fun saverOn(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isPowerSaveMode

    fun toggleLocation(ctx: Context): Boolean = try {
        Settings.Secure.putInt(ctx.contentResolver, "location_mode", if (locationOn(ctx)) 0 else 3)
    } catch (e: Exception) {
        Log.w("DotWidgets", "No se pudo cambiar la ubicación", e)
        false
    }

    fun toggleSaver(ctx: Context): Boolean = try {
        Settings.Global.putInt(ctx.contentResolver, "low_power", if (saverOn(ctx)) 0 else 1)
    } catch (e: Exception) {
        Log.w("DotWidgets", "No se pudo cambiar el ahorro de batería", e)
        false
    }
}
