package com.dulz.fluu

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import java.io.File

internal fun lum(c: Int): Int =
    (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c)) / 1000

/** Palet warna yang dipakai panel native. */
internal class Pal(val bg: Int, val surface: Int, val text: Int, val accent: Int) {
    val onAccent: Int get() = if (lum(accent) > 150) 0xFF0B0C0F.toInt() else Color.WHITE
}

object Sys {

    /** Membuka aplikasi lain berdasarkan nama paket. */
    fun open(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Pembersih: hapus cache aplikasi ini dan hentikan proses latar belakang
     * aplikasi lain. Mengembalikan perkiraan MB yang dibebaskan.
     */
    fun clean(ctx: Context): Double {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem

        var bytes = 0L
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (!f.name.startsWith("org.chromium")) bytes += wipe(f)
            }
        }

        ctx.packageManager.getInstalledApplications(0).forEach { app ->
            val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!system && app.packageName != ctx.packageName) {
                am.killBackgroundProcesses(app.packageName)
            }
        }

        Thread.sleep(400)
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        return (bytes + maxOf(0L, after - before)) / 1048576.0
    }

    private fun wipe(f: File): Long {
        var n = 0L
        if (f.isDirectory) f.listFiles()?.forEach { n += wipe(it) }
        n += f.length()
        f.delete()
        return n
    }
}
