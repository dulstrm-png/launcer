package com.dulz.fluu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Jembatan antara index.html dan Android.
 * Di JavaScript tersedia sebagai objek global "Android".
 */
class Bridge(private val act: MainActivity) {

    private val prefs = act.getSharedPreferences("dulz", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun hasPermission(id: String): Boolean = when (id) {
        "overlay" -> Settings.canDrawOverlays(act)
        "notif" -> Build.VERSION.SDK_INT < 33 ||
            act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        "battery" -> (act.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(act.packageName)
        else -> true
    }

    @JavascriptInterface
    fun requestPermission(id: String) {
        act.runOnUiThread {
            val pkg = Uri.parse("package:${act.packageName}")
            try {
                when (id) {
                    "overlay" -> act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
                    "battery" -> act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
                    "notif" -> if (Build.VERSION.SDK_INT >= 33) {
                        act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                    }
                }
            } catch (e: Exception) {
                act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
            }
        }
    }

    @JavascriptInterface
    fun showOverlay(on: Boolean) {
        val svc = Intent(act, OverlayService::class.java)
        if (on) {
            if (!Settings.canDrawOverlays(act)) return
            act.startForegroundService(svc)
        } else {
            act.stopService(svc)
        }
    }

    @JavascriptInterface
    fun openApp(pkg: String): Boolean = Sys.open(act, pkg)

    @JavascriptInterface
    fun clean(): Double = Sys.clean(act)

    @JavascriptInterface
    fun gameMode(on: Boolean) {
        prefs.edit().putBoolean("game", on).apply()
        act.runOnUiThread {
            if (on) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        OverlayService.instance?.refresh()
    }

    /** Daftar aplikasi (JSON array) dikirim dari HTML agar panel native bisa menampilkannya. */
    @JavascriptInterface
    fun setApps(json: String) {
        prefs.edit().putString("apps", json).apply()
        OverlayService.instance?.refresh()
    }

    /** Warna tema (JSON: bg, surface, text, accent) dikirim dari HTML. */
    @JavascriptInterface
    fun setTheme(json: String) {
        prefs.edit().putString("theme", json).apply()
        try {
            val bg = Color.parseColor(JSONObject(json).getString("bg"))
            act.runOnUiThread { act.applyBars(bg) }
        } catch (_: Exception) {
        }
        OverlayService.instance?.refresh()
    }
}
