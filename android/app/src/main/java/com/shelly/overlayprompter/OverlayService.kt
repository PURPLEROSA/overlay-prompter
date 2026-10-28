package com.shelly.overlayprompter

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Foreground service that owns the floating teleprompter window (TYPE_APPLICATION_OVERLAY).
 * Windows: [panel] (main prompter) and [bubble] (shown when collapsed or locked/click-through).
 */
class OverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_HIDE = "hide"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "prompter"
        private const val NOTIF_ID = 7
    }

    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private lateinit var heebo: Typeface

    private var panel: View? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private var bubble: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams

    private lateinit var scroll: ScrollView
    private lateinit var text: TextView
    private lateinit var band: View
    private lateinit var playBtn: TextView
    private lateinit var lockBtn: TextView

    private var playing = false
    private var locked = false
    private var collapsed = false
    private var scrollAcc = 0f
    private var lastFrameNs = 0L

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (!playing) return
            if (lastFrameNs != 0L) {
                scrollAcc += store.speed * (now - lastFrameNs) / 1e9f
                val dy = scrollAcc.toInt()
                if (dy > 0) { scroll.scrollBy(0, dy); scrollAcc -= dy }
                val atEnd = scroll.scrollY >= text.height - scroll.height
                if (atEnd) { setPlaying(false); return }
            }
            lastFrameNs = now
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
        heebo = resources.getFont(R.font.heebo)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "פרומפטר צף", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startFg() } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_TOGGLE -> setPlaying(!playing)
            ACTION_HIDE -> if (collapsed) expand() else collapse()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> showPanel()
        }
        return START_STICKY
    }

    private fun pi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, OverlayService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("פרומפטר צף פעיל")
            .setContentText(if (playing) "גלילה פעילה" else "מושהה")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, if (playing) "השהיה" else "הפעלה", pi(ACTION_TOGGLE, 1)).build())
            .addAction(Notification.Action.Builder(null, if (collapsed) "הצגה" else "הסתרה", pi(ACTION_HIDE, 2)).build())
            .addAction(Notification.Action.Builder(null, "עצירה", pi(ACTION_STOP, 3)).build())
            .build()
    }

    private fun startFg() {
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIF_ID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, buildNotif())
    }

    private fun refreshNotif() = getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif())

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun baseFlags(): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        // FLAG_SECURE: ask the system to exclude this window from screenshots / screen recording.
        // Honored by most devices for overlay windows, but behavior varies by OEM / Android version.
        if (store.secure) f = f or WindowManager.LayoutParams.FLAG_SECURE
        return f
    }

    // ---------------- panel ----------------

    @SuppressLint("ClickableViewAccessibility")
    private fun showPanel() {
        if (panel != null) { text.text = store.currentText; if (collapsed) expand(); return }
        val dm = resources.displayMetrics
        panelLp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.92).toInt(), (dm.heightPixels * 0.35).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, baseFlags(), PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (dm.widthPixels * 0.04).toInt(); y = dp(60) }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // toolbar (LTR so icons keep stable order)
        val bar = LinearLayout(this).apply {
            setBackgroundColor(Color.argb(200, 30, 30, 40)); gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val drag = icon("✥") {}
        drag.setOnTouchListener(dragger({ panelLp }, { panel }))
        bar.addView(drag)
        playBtn = icon("▶") { setPlaying(!playing) }
        bar.addView(playBtn)
        bar.addView(icon("−") { store.speed = max(5f, store.speed - 10f); toast("מהירות ${store.speed.toInt()}") })
        bar.addView(icon("+") { store.speed = store.speed + 10f; toast("מהירות ${store.speed.toInt()}") })
        bar.addView(icon("A−") { store.fontSp = max(12f, store.fontSp - 2f); applyStyle() })
        bar.addView(icon("A+") { store.fontSp = store.fontSp + 2f; applyStyle() })
        bar.addView(icon("◐") { store.opacity = if (store.opacity <= 0.3f) 1f else store.opacity - 0.15f; applyStyle() })
        bar.addView(icon("⇋") { store.mirror = !store.mirror; applyStyle() })
        bar.addView(icon("▤") { store.highlight = !store.highlight; applyStyle() })
        lockBtn = icon("🔒") { setLocked(true) }
        bar.addView(lockBtn)
        bar.addView(icon("▁") { collapse() })
        bar.addView(icon("✕") { stopSelf() })
        root.addView(HorizontalScrollView(this).apply { addView(bar); isHorizontalScrollBarEnabled = false },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // body
        val body = FrameLayout(this)
        text = TextView(this).apply {
            setTextColor(Color.WHITE); typeface = heebo
            textDirection = View.TEXT_DIRECTION_RTL; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setLineSpacing(0f, 1.3f); setPadding(dp(16), 0, dp(16), 0)
            text = store.currentText
        }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(text) }
        // tap anywhere on the text = play/pause; drag = manual scroll
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean { setPlaying(!playing); return true }
        })
        scroll.setOnTouchListener { _, e -> gd.onTouchEvent(e); false }
        body.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        band = View(this).apply { setBackgroundColor(Color.argb(60, 255, 214, 0)); isClickable = false }
        body.addView(band, FrameLayout.LayoutParams(MATCH_PARENT, 10))
        val resize = TextView(this).apply {
            text = "◢"; setTextColor(Color.argb(200, 255, 255, 255)); textSize = 22f; setPadding(dp(6), dp(6), dp(6), dp(2))
        }
        resize.setOnTouchListener(resizer())
        body.addView(resize, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        body.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionBand() }
        root.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        panel = root
        applyStyle()
        wm.addView(root, panelLp)
    }

    private fun positionBand() {
        val h = (text.lineHeight * 1.2f).toInt()
        val lp = band.layoutParams as FrameLayout.LayoutParams
        lp.height = h; lp.topMargin = (scroll.height / 3) - h / 2
        band.layoutParams = lp
        // pad so first/last lines can reach the reading line
        text.setPadding(text.paddingLeft, scroll.height / 3, text.paddingRight, scroll.height * 2 / 3)
    }

    private fun applyStyle() {
        val p = panel ?: return
        val a = (store.opacity * 255).toInt()
        p.background = GradientDrawable().apply { setColor(Color.argb(a, 0, 0, 0)); cornerRadius = dp(12).toFloat() }
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, store.fontSp)
        text.scaleX = if (store.mirror) -1f else 1f
        band.visibility = if (store.highlight) View.VISIBLE else View.GONE
        text.post { positionBand() }
    }

    private fun setPlaying(on: Boolean) {
        if (panel == null) return
        playing = on
        playBtn.text = if (on) "⏸" else "▶"
        lastFrameNs = 0L; scrollAcc = 0f
        Choreographer.getInstance().removeFrameCallback(frame)
        if (on) Choreographer.getInstance().postFrameCallback(frame)
        refreshNotif()
    }

    // ------------- lock / click-through -------------

    private fun setLocked(on: Boolean) {
        val p = panel ?: return
        locked = on
        panelLp.flags = if (on) baseFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else baseFlags()
        wm.updateViewLayout(p, panelLp)
        if (on) showBubble("🔓") { setLocked(false) } else removeBubble()
    }

    // ---------------- collapse ----------------

    private fun collapse() {
        val p = panel ?: return
        setPlaying(false)
        p.visibility = View.GONE
        collapsed = true
        showBubble("📜") { expand() }
        refreshNotif()
    }

    private fun expand() {
        val p = panel ?: return
        p.visibility = View.VISIBLE
        collapsed = false
        if (locked) showBubble("🔓") { setLocked(false) } else removeBubble()
        refreshNotif()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble(label: String, onTap: () -> Unit) {
        removeBubble()
        bubbleLp = WindowManager.LayoutParams(dp(52), dp(52), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(68); y = dp(140)
        }
        val b = TextView(this).apply {
            text = label; textSize = 22f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(220, 126, 87, 194)) }
        }
        b.setOnTouchListener(dragger({ bubbleLp }, { bubble }, onTap))
        bubble = b
        wm.addView(b, bubbleLp)
    }

    private fun removeBubble() { bubble?.let { runCatching { wm.removeView(it) } }; bubble = null }

    // ---------------- helpers ----------------

    private fun icon(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 18f; typeface = heebo
        setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { onClick() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    /** Drag a window by touching [this] view; tap (no movement) triggers [onTap]. */
    private fun dragger(lp: () -> WindowManager.LayoutParams, view: () -> View?, onTap: (() -> Unit)? = null) =
        object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val l = lp()
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = l.x; oy = l.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx; val dy = e.rawY - sy
                        if (abs(dx) + abs(dy) > dp(6)) moved = true
                        l.x = ox + dx.toInt(); l.y = oy + dy.toInt()
                        view()?.let { wm.updateViewLayout(it, l) }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) onTap?.invoke()
                }
                return true
            }
        }

    private fun resizer() = object : View.OnTouchListener {
        var sx = 0f; var sy = 0f; var ow = 0; var oh = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ow = panelLp.width; oh = panelLp.height }
                MotionEvent.ACTION_MOVE -> {
                    panelLp.width = max(dp(160), ow + (e.rawX - sx).toInt())
                    panelLp.height = max(dp(120), oh + (e.rawY - sy).toInt())
                    panel?.let { wm.updateViewLayout(it, panelLp) }
                }
            }
            return true
        }
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(frame)
        removeBubble()
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        super.onDestroy()
    }
}
