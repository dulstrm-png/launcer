package com.dulz.fluu

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Panel melayang native: gelembung yang bisa digeser, terbuka menjadi panel
 * berisi info perangkat, pembersih, mode game, dan pintasan aplikasi.
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var instance: OverlayService? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())

    private var pal = Pal(0xFF0A0B0E.toInt(), 0xFF14161B.toInt(), 0xFFF1F2F4.toInt(), 0xFF5B8CFF.toInt())
    private var open = false
    private var tvHz: TextView? = null
    private var tvRam: TextView? = null
    private var tvBat: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            updateStats()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("dulz", MODE_PRIVATE)
        pal = loadPal()
        startAsForeground()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = FrameLayout(this)

        val m = resources.displayMetrics
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("fx", m.widthPixels - dp(60))
            y = prefs.getInt("fy", (m.heightPixels * 0.34f).toInt())
        }
        applyFlags(false)
        wm.addView(root, lp)
        setOpen(false)
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
        instance = null
        super.onDestroy()
    }

    /** Dipanggil saat tema, daftar aplikasi, atau mode game berubah. */
    fun refresh() {
        ui.post {
            pal = loadPal()
            applyFlags(true)
            setOpen(open, false)
        }
    }

    // ---------------------------------------------------------------- Foreground

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("panel", "Panel melayang", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "panel")
            .setContentTitle("Dulz Fluu")
            .setContentText("Panel melayang aktif")
            .setSmallIcon(R.drawable.ic_gauge)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------------------------------------------------------------- Data

    private fun loadPal(): Pal = try {
        val j = JSONObject(prefs.getString("theme", "") ?: "")
        Pal(
            Color.parseColor(j.getString("bg")),
            Color.parseColor(j.getString("surface")),
            Color.parseColor(j.getString("text")),
            Color.parseColor(j.getString("accent"))
        )
    } catch (e: Exception) {
        Pal(0xFF0A0B0E.toInt(), 0xFF14161B.toInt(), 0xFFF1F2F4.toInt(), 0xFF5B8CFF.toInt())
    }

    private fun loadApps(): List<Pair<String, String>> = try {
        val a = JSONArray(prefs.getString("apps", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }
            .map { it.getString("name") to it.getString("pkg") }
    } catch (e: Exception) {
        emptyList()
    }

    private fun applyFlags(update: Boolean) {
        val keep = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        lp.flags = if (prefs.getBoolean("game", false)) lp.flags or keep else lp.flags and keep.inv()
        if (update) try {
            wm.updateViewLayout(root, lp)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- Tampilan

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun withA(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    private fun round(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = r.toFloat()
    }

    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun label(s: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(s: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = s
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (filled) pal.onAccent else pal.text)
        background = round(if (filled) pal.accent else withA(pal.text, 0x14), dp(12))
        setOnClickListener { onClick() }
    }

    private fun buildBubble(): View {
        val v = FrameLayout(this).apply {
            background = oval(pal.accent)
            layoutParams = FrameLayout.LayoutParams(dp(54), dp(54))
        }
        v.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_gauge)
            setColorFilter(pal.onAccent)
        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        drag(v, true) { setOpen(true) }
        return v
    }

    private fun buildPanel(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
            background = round(withA(pal.surface, 0xF2), dp(20)).apply {
                setStroke(dp(1), withA(pal.text, 0x22))
            }
            layoutParams = FrameLayout.LayoutParams(dp(264), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Kepala (bisa digeser)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        head.addView(
            label("Dulz Fluu", 15f, pal.text, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        head.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(withA(pal.text, 0x99))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { setOpen(false) }
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        drag(head, false) {}
        card.addView(head)

        // Statistik
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = round(withA(pal.text, 0x12), dp(14))
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        fun stat(name: String): TextView {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val value = label("--", 18f, pal.text, true)
            col.addView(value)
            col.addView(label(name, 11f, withA(pal.text, 0x99), false))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return value
        }
        tvHz = stat("Hz layar")
        tvRam = stat("RAM bebas")
        tvBat = stat("Baterai")
        card.addView(row)

        // Aksi
        val game = prefs.getBoolean("game", false)
        val acts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(2))
        }
        acts.addView(button("Bersihkan", true) { cleanNow() },
            LinearLayout.LayoutParams(0, dp(40), 1f))
        acts.addView(button(if (game) "Game aktif" else "Mode Game", game) { toggleGame() },
            LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(8) })
        card.addView(acts)

        // Aplikasi
        val apps = loadApps()
        if (apps.isEmpty()) {
            card.addView(label("Tambahkan aplikasi dari dalam Dulz Fluu agar muncul di sini.", 12f,
                withA(pal.text, 0x99), false).apply { setPadding(0, dp(10), 0, 0) })
        } else {
            apps.take(8).chunked(4).forEach { group ->
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(10), 0, 0)
                }
                group.forEach { (name, pkg) ->
                    r.addView(appTile(name, pkg),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                repeat(4 - group.size) { r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
                card.addView(r)
            }
        }
        return card
    }

    private fun appTile(name: String, pkg: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener {
                if (!Sys.open(this@OverlayService, pkg)) toast("Aplikasi tidak ditemukan")
            }
        }
        val letter = label(name.take(1).uppercase(), 16f, pal.accent, true).apply {
            gravity = Gravity.CENTER
            background = oval(withA(pal.accent, 0x2A))
        }
        col.addView(letter, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(label(name, 11f, withA(pal.text, 0x99), false).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(60)
            setPadding(0, dp(4), 0, 0)
        })
        return col
    }

    // ---------------------------------------------------------------- Perilaku

    private fun setOpen(o: Boolean, animate: Boolean = true) {
        open = o
        ui.removeCallbacks(tick)
        root.removeAllViews()
        if (o) {
            val p = buildPanel()
            root.addView(p)
            if (animate) {
                p.alpha = 0f; p.scaleX = .6f; p.scaleY = .6f; p.pivotX = 0f; p.pivotY = 0f
                p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            ui.post(tick)
            root.post { clampUpdate() }
        } else {
            val b = buildBubble()
            root.addView(b)
            if (animate) {
                b.scaleX = 0f; b.scaleY = 0f
                b.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(OvershootInterpolator()).start()
            }
            root.post { snapEdge() }
        }
    }

    private fun drag(v: View, snap: Boolean, onTap: () -> Unit) {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (!moved && abs(dx) + abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = (ox + dx).toInt()
                        lp.y = (oy + dy).toInt()
                        clampUpdate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (snap) snapEdge() else savePos()
                    } else onTap()
                }
            }
            true
        }
    }

    private fun clampUpdate() {
        val m = resources.displayMetrics
        val w = if (root.width > 0) root.width else dp(54)
        val h = if (root.height > 0) root.height else dp(54)
        lp.x = lp.x.coerceIn(0, maxOf(0, m.widthPixels - w))
        lp.y = lp.y.coerceIn(0, maxOf(0, m.heightPixels - h))
        try {
            wm.updateViewLayout(root, lp)
        } catch (_: Exception) {
        }
    }

    private fun snapEdge() {
        val m = resources.displayMetrics
        val w = if (root.width > 0) root.width else dp(54)
        val target = if (lp.x + w / 2 < m.widthPixels / 2) dp(6) else m.widthPixels - w - dp(6)
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lp.x = it.animatedValue as Int
                try {
                    wm.updateViewLayout(root, lp)
                } catch (_: Exception) {
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = savePos()
            })
            start()
        }
    }

    private fun savePos() {
        prefs.edit().putInt("fx", lp.x).putInt("fy", lp.y).apply()
    }

    private fun toggleGame() {
        val v = !prefs.getBoolean("game", false)
        prefs.edit().putBoolean("game", v).apply()
        toast(if (v) "Mode Game aktif. Layar tetap menyala" else "Mode Game dimatikan")
        refresh()
    }

    private fun cleanNow() {
        toast("Membersihkan")
        Thread {
            val mb = Sys.clean(this)
            ui.post {
                toast(if (mb > 0.05) "Dibersihkan %.1f MB".format(mb) else "Tidak ada yang perlu dibersihkan")
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun updateStats() {
        try {
            val dsp = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            tvHz?.text = dsp.refreshRate.toInt().toString()

            val mi = ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            tvRam?.text = "%.1f GB".format(mi.availMem / 1073741824.0)

            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            tvBat?.text = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString() + "%"
        } catch (_: Exception) {
        }
    }
}
