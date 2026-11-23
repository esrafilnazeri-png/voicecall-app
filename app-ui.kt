package com.esrafil.voicecall

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private class WaveView(context: Context) : View(context) {
    var activity = 0.12f
    var c1 = Color.CYAN
    var c2 = Color.BLUE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = System.currentTimeMillis() / 1000.0
        for (layer in 0..2) {
            val base = h * (0.42f + 0.17f * layer)
            val amp = h * (0.13f + 0.20f * activity) * (1f - 0.26f * layer)
            val freq = 1.3f + 0.8f * layer
            val speed = 1.0 + 0.4 * layer
            path.reset()
            path.moveTo(0f, h)
            path.lineTo(0f, base)
            var x = 0f
            while (x <= w) {
                val y = (base + amp * sin(freq * (x / w) * 2.0 * Math.PI + t * speed)).toFloat()
                path.lineTo(x, y)
                x += 8f
            }
            path.lineTo(w, h)
            path.close()
            paint.color = if (layer == 0) c1 else if (layer == 1) c2 else c1
            paint.alpha = (120 + 50 * activity).toInt() - layer * 30
            canvas.drawPath(path, paint)
            crest.color = if (layer == 2) c2 else c1
            crest.alpha = 170 - layer * 45
            val top = Path()
            top.moveTo(0f, base)
            x = 0f
            while (x <= w) {
                val y = (base + amp * sin(freq * (x / w) * 2.0 * Math.PI + t * speed)).toFloat()
                top.lineTo(x, y)
                x += 8f
            }
            canvas.drawPath(top, crest)
        }
    }
}

private class MicCircleView(context: Context) : View(context) {
    var level = 0.15f
    var muted = false
    var accent = Color.CYAN
    var accent2 = Color.BLUE
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val baseR = minOf(w, h) * 0.27f
        val t = System.currentTimeMillis() / 1000.0

        for (i in 0..2) {
            val frac = ((t * 0.65 + i * 0.33) % 1.0).toFloat()
            val r = baseR * (1.08f + 0.85f * frac * (0.55f + level))
            ring.color = if (muted) Color.parseColor("#F43F5E") else accent
            ring.alpha = ((1f - frac) * (55 + 175 * level)).toInt()
            ring.strokeWidth = 3f + 5f * (1 - frac)
            canvas.drawCircle(cx, cy, r, ring)
        }

        fill.shader = RadialGradient(
            cx, cy - baseR * 0.35f, baseR * 1.5f,
            if (muted) intArrayOf(Color.parseColor("#F87171"), Color.parseColor("#9F1239"))
            else intArrayOf(accent, accent2),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseR, fill)
        fill.shader = null
        fill.color = Color.argb(55, 0, 0, 0)
        canvas.drawCircle(cx, cy, baseR * 0.86f, fill)

        val gw = baseR * 0.34f
        val gh = baseR * 0.66f
        glyph.color = Color.WHITE
        val rect = RectF(cx - gw / 2, cy - gh * 0.78f, cx + gw / 2, cy - gh * 0.78f + gh)
        canvas.drawRoundRect(rect, gw / 2, gw / 2, glyph)

        glyphStroke.color = Color.WHITE
        glyphStroke.strokeWidth = baseR * 0.09f
        val arcR = gw * 1.0f
        canvas.drawArc(
            RectF(cx - arcR, cy - gh * 0.30f, cx + arcR, cy - gh * 0.30f + arcR * 2),
            0f, 180f, false, glyphStroke
        )
        glyphStroke.strokeWidth = baseR * 0.09f
        canvas.drawLine(cx, cy + gh * 0.34f + arcR * 0.55f, cx, cy + gh * 0.62f, glyphStroke)

        if (muted) {
            glyphStroke.color = Color.WHITE
            glyphStroke.strokeWidth = baseR * 0.11f
            canvas.drawLine(cx - baseR * 0.44f, cy + baseR * 0.44f, cx + baseR * 0.44f, cy - baseR * 0.44f, glyphStroke)
        }
    }
}

class MainActivity : Activity() {

    private companion object {
        const val SERVER = "https://shrill-wave-0b90.esrafilnazeri.workers.dev"
        const val PREFS = "voicecall"
    }

    private val themes = arrayOf(
        Triple("اقیانوس", "#22D3EE", "#3B82F6"),
        Triple("زمردی", "#34D399", "#059669"),
        Triple("امضایی", "#10B981", "#0EA5E9"),
        Triple("طلایی", "#FBBF24", "#F59E0B"),
        Triple("بنفش", "#8B5CF6", "#4F46E5"),
        Triple("خونی", "#EF4444", "#B91C1C")
    )
    private var themeIndex = 0
    private val a1: Int get() = Color.parseColor(themes[themeIndex].second)
    private val a2: Int get() = Color.parseColor(themes[themeIndex].third)

    private val cText = Color.parseColor("#F3F0FF")
    private val cMuted = Color.parseColor("#9A92C9")
    private val cGreen = Color.parseColor("#34D399")
    private val cAmber = Color.parseColor("#FBBF24")
    private val cRed = Color.parseColor("#F43F5E")
    private val cGray = Color.parseColor("#6B7280")

    private val emojiChoices = listOf(
        "😀", "😎", "🤩", "😇", "🥳", "🤠", "👻", "🤖",
        "🦊", "🐼", "🐺", "🦁", "🐸", "🐵", "🐯", "🦅",
        "⚡", "🔥", "🌙", "⭐", "💎", "🎧", "🚀", "🎯"
    )

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var rootRef: FrameLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var errorCard: TextView
    private lateinit var peopleBox: LinearLayout
    private lateinit var statsRow: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var countText: TextView
    private lateinit var pingText: TextView
    private lateinit var micView: MicCircleView
    private lateinit var callBtn: TextView
    private lateinit var muteBtn: TextView
    private lateinit var speakerBtn: TextView
    private lateinit var chatBtn: TextView
    private lateinit var endBtn: TextView
    private lateinit var reactBtn: TextView
    private lateinit var hintText: TextView
    private lateinit var waveBottom: WaveView
    private lateinit var waveTop: WaveView

    private lateinit var chatOverlay: FrameLayout
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatInput: EditText
    private var chatOpen = false
    private var unread = 0

    private lateinit var profileOverlay: FrameLayout
    private lateinit var profileAvatar: TextView
    private lateinit var fUser: EditText
    private lateinit var fName: EditText
    private lateinit var fBio: EditText
    private var selectedEmoji = "😎"
    private var profileOpen = false

    private val orbs = mutableListOf<View>()
    private val particles = mutableListOf<Pair<View, Float>>()

    private var ticker: ValueAnimator? = null
    private var eventsJob: Job? = null
    private var pendingConnect = false
    private var callStart = 0L
    private var lastStatus = "idle"
    private var speakerOn = true
    private var alive = true
    private var waveActive = false
    private var pingMs = -1
    private var pingThread: Thread? = null
    private val wasSpeaking = mutableMapOf<String, Boolean>()
    private val knownNames = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        CallManager.onChange = { runOnUiThread { if (alive) render() } }
        render()
        startTicker()
        showSplash()
    }

    override fun onDestroy() {
        alive = false
        ticker?.cancel()
        pingThread?.interrupt()
        eventsJob?.cancel()
        CallManager.onChange = null
        super.onDestroy()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun fa(s: String): String {
        val en = "0123456789"
        val faDigits = "۰۱۲۳۴۵۶۷۸۹"
        var out = s
        for (i in en.indices) out = out.replace(en[i], faDigits[i])
        return out
    }

    private fun fmtDur(sec: Long): String {
        val mm = sec / 60
        val ss = sec % 60
        val s = (if (mm < 10) "0" else "") + mm + ":" + (if (ss < 10) "0" else "") + ss
        return fa(s)
    }

    private fun parseEmoji(bio: String): Pair<String, String> {
        val i = bio.indexOf('|')
        return if (i in 1..4) Pair(bio.substring(0, i), bio.substring(i + 1)) else Pair("", bio)
    }

    private fun rounded(bg: Int, radius: Float = 18f, stroke: Int = 0, strokeW: Float = 1.5f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(bg)
            if (stroke != 0) setStroke(dp(strokeW), stroke)
        }

    private fun orb(color: Int, size: Int, x: Int, y: Int): View {
        val v = View(this)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        v.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(110, r, g, b), Color.argb(0, r, g, b))
        ).apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientRadius(size / 2f)
            setGradientCenter(0.5f, 0.5f)
        }
        val lp = FrameLayout.LayoutParams(size, size)
        lp.setMargins(x, y, 0, 0)
        v.layoutParams = lp
        return v
    }

    private fun gradientText(tv: TextView, colors: IntArray) {
        tv.paint.shader = LinearGradient(
            0f, 0f, tv.paint.textSize * (tv.text.length + 2), 0f,
            colors, null, Shader.TileMode.CLAMP
        )
        tv.setShadowLayer(dp(18f).toFloat(), 0f, 0f, a2)
        tv.invalidate()
    }

    private fun field(hint: String, single: Boolean = true): EditText =
        EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.parseColor("#7E76AD"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = rounded(Color.parseColor("#1B1440"), 16f, Color.parseColor("#3A2E75"), 1.5f)
            inputType = if (single) InputType.TYPE_CLASS_TEXT else (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            setSingleLine(single)
            if (!single) maxLines = 3
        }

    private fun button(text: String, c1: Int, c2: Int, size: Float = 14.5f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18f), dp(15f), dp(18f), dp(15f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
                cornerRadius = dp(26f).toFloat()
            }
            elevation = dp(7f).toFloat()
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                }
                false
            }
        }

    private fun circleButton(symbol: String, c1: Int, c2: Int): TextView =
        TextView(this).apply {
            text = symbol
            textSize = 19f
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
                shape = GradientDrawable.OVAL
            }
            elevation = dp(6f).toFloat()
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                }
                false
            }
        }

    private fun smallPill(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(cText)
            gravity = Gravity.CENTER
            setPadding(dp(11f), dp(7f), dp(11f), dp(7f))
            background = rounded(Color.parseColor("#1A1636"), 16f, Color.parseColor("#332B63"), 1f)
        }

    private fun buildUi() {
        orbs.clear()
        particles.clear()

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0A0A1F"))
        rootRef = root

        val o1 = orb(a1, dp(340f), dp(-100f), dp(-70f))
        val o2 = orb(a2, dp(320f), dp(180f), dp(420f))
        val o3 = orb(Color.parseColor("#4F46E5"), dp(380f), dp(-130f), dp(640f))
        orbs.add(o1); orbs.add(o2); orbs.add(o3)
        root.addView(o1); root.addView(o2); root.addView(o3)

        for (i in 0..9) {
            val p = View(this)
            val pc = if (i % 2 == 0) a1 else a2
            val r = Color.red(pc); val g = Color.green(pc); val b = Color.blue(pc)
            p.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(130, r, g, b))
            }
            val sz = dp(if (i % 3 == 0) 7f else 4f)
            val lp = FrameLayout.LayoutParams(sz, sz)
            lp.setMargins(dp((8 + (i * 37) % 310).toFloat()), dp((50 + (i * 149) % 720).toFloat()), 0, 0)
            p.layoutParams = lp
            p.alpha = 0.4f
            particles.add(Pair(p, (i * 0.11f) % 1f))
            root.addView(p)
        }

        waveBottom = WaveView(this)
        waveBottom.c1 = a1
        waveBottom.c2 = a2
        root.addView(waveBottom, FrameLayout.LayoutParams(-1, dp(300f), Gravity.BOTTOM))

        waveTop = WaveView(this)
        waveTop.c1 = a2
        waveTop.c2 = a1
        waveTop.rotation = 180f
        waveTop.alpha = 0.45f
        root.addView(waveTop, FrameLayout.LayoutParams(-1, dp(150f), Gravity.TOP))

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        root.addView(main, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "esy call"
        title.textSize = 30f
        title.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        title.setTextColor(Color.WHITE)
        gradientText(title, intArrayOf(a1, Color.WHITE, a2))
        header.addView(title)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val themeBtn = TextView(this)
        themeBtn.text = "🎨 تم"
        themeBtn.textSize = 11f
        themeBtn.setTextColor(a1)
        themeBtn.gravity = Gravity.CENTER
        themeBtn.setPadding(dp(11f), dp(8f), dp(11f), dp(8f))
        themeBtn.background = rounded(Color.parseColor("#141033"), 20f, Color.parseColor("#3A2E75"), 1f)
        themeBtn.setOnClickListener {
            themeIndex = (themeIndex + 1) % themes.size
            buildUi()
            render()
        }
        header.addView(themeBtn)
        header.addView(View(this), LinearLayout.LayoutParams(dp(8f), 1))

        val profileBtn = TextView(this)
        profileBtn.text = "👤 حساب"
        profileBtn.textSize = 11f
        profileBtn.setTextColor(a1)
        profileBtn.gravity = Gravity.CENTER
        profileBtn.setPadding(dp(11f), dp(8f), dp(11f), dp(8f))
        profileBtn.background = rounded(Color.parseColor("#141033"), 20f, Color.parseColor("#3A2E75"), 1f)
        profileBtn.setOnClickListener { openProfile() }
        header.addView(profileBtn)
        main.addView(header)

        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
        pill.background = rounded(Color.parseColor("#12102E"), 22f, Color.parseColor("#2B2560"), 1f)
        statusDot = View(this)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(cGray)
        }
        pill.addView(statusDot, LinearLayout.LayoutParams(dp(10f), dp(10f)))
        statusText = TextView(this)
        statusText.textSize = 12.5f
        statusText.setTextColor(cMuted)
        pill.addView(statusText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
        main.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14f) })

        statsRow = LinearLayout(this)
        statsRow.orientation = LinearLayout.HORIZONTAL
        statsRow.gravity = Gravity.CENTER_VERTICAL
        statsRow.setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        statsRow.background = rounded(Color.parseColor("#100E28EE"), 20f, Color.parseColor("#2E2762"), 1f)
        statsRow.visibility = View.GONE
        timerText = smallPill("⏱ ۰۰:۰۰")
        countText = smallPill("👥 ۰ نفر")
        pingText = smallPill("📶 ...")
        statsRow.addView(timerText, LinearLayout.LayoutParams(0, -2, 1f))
        statsRow.addView(countText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6f) })
        statsRow.addView(pingText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6f) })
        main.addView(statsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        errorCard = TextView(this)
        errorCard.textSize = 12.5f
        errorCard.setTextColor(Color.parseColor("#FDA4AF"))
        errorCard.setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
        errorCard.background = rounded(Color.parseColor("#3B1220"), 16f, Color.parseColor("#7F1D33"), 1f)
        errorCard.visibility = View.GONE
        main.addView(errorCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        peopleBox = LinearLayout(this)
        peopleBox.orientation = LinearLayout.VERTICAL
        scroll.addView(peopleBox, LinearLayout.LayoutParams(-1, -2))
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10f) })

        callBtn = button("تماس عمومی", a1, a2, 16f)
        callBtn.setOnClickListener { onCallClick() }
        main.addView(callBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })

        micView = MicCircleView(this)
        micView.accent = a1
        micView.accent2 = a2
        micView.setOnClickListener {
            doToggleMute()
            render()
            vibrate(25)
        }
        main.addView(micView, LinearLayout.LayoutParams(-1, dp(150f)))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        muteBtn = circleButton("🔊", Color.parseColor("#164E63"), Color.parseColor("#0E7490"))
        muteBtn.setOnClickListener { toggleSpeaker() }
        speakerBtn = muteBtn
        row.addView(muteBtn, LinearLayout.LayoutParams(dp(58f), dp(58f)))

        chatBtn = circleButton("💬", Color.parseColor("#1E1B4B"), Color.parseColor("#312E81"))
        chatBtn.setOnClickListener { openChat() }
        row.addView(chatBtn, LinearLayout.LayoutParams(dp(58f), dp(58f)).apply { marginStart = dp(14f) })

        reactBtn = circleButton("😊", Color.parseColor("#1E1B4B"), Color.parseColor("#312E81"))
        reactBtn.setOnClickListener { reactionBurst() }
        row.addView(reactBtn, LinearLayout.LayoutParams(dp(58f), dp(58f)).apply { marginStart = dp(14f) })

        endBtn = circleButton("✕", Color.parseColor("#9F1239"), Color.parseColor("#F43F5E"))
        endBtn.setOnClickListener { doHangup(); render() }
        row.addView(endBtn, LinearLayout.LayoutParams(dp(58f), dp(58f)).apply { marginStart = dp(14f) })
        main.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })

        hintText = TextView(this)
        hintText.text = "همه می‌توانند وارد این تماس شوند ✓"
        hintText.textSize = 11f
        hintText.setTextColor(cMuted)
        hintText.gravity = Gravity.CENTER
        main.addView(hintText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })

        val ver = TextView(this)
        ver.text = "نسخه ۵"
        ver.textSize = 9f
        ver.setTextColor(Color.parseColor("#4A4470"))
        ver.gravity = Gravity.CENTER
        main.addView(ver, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })

        buildChatOverlay()
        buildProfileOverlay()
        setContentView(root)
    }

    private fun buildChatOverlay() {
        chatOverlay = FrameLayout(this)
        chatOverlay.setBackgroundColor(Color.parseColor("#F50A0A1F"))
        chatOverlay.visibility = View.GONE

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16f), dp(18f), dp(16f), dp(16f))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val hTitle = TextView(this)
        hTitle.text = "💬 چت زنده"
        hTitle.textSize = 18f
        hTitle.setTextColor(cText)
        hTitle.typeface = Typeface.DEFAULT_BOLD
        head.addView(hTitle)
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val close = TextView(this)
        close.text = "✕"
        close.textSize = 17f
        close.setTextColor(cMuted)
        close.setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        close.setOnClickListener { closeChat() }
        head.addView(close)
        col.addView(head)

        chatScroll = ScrollView(this)
        chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatScroll.addView(chatList, LinearLayout.LayoutParams(-1, -2))
        col.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10f) })

        val sendRow = LinearLayout(this)
        sendRow.orientation = LinearLayout.HORIZONTAL
        sendRow.gravity = Gravity.CENTER_VERTICAL
        chatInput = EditText(this)
        chatInput.hint = "پیام بنویس..."
        chatInput.setHintTextColor(Color.parseColor("#7E76AD"))
        chatInput.setTextColor(Color.WHITE)
        chatInput.textSize = 13.5f
        chatInput.setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
        chatInput.background = rounded(Color.parseColor("#1B1440"), 18f, Color.parseColor("#3A2E75"), 1f)
        chatInput.setSingleLine(true)
        sendRow.addView(chatInput, LinearLayout.LayoutParams(0, -2, 1f))

        val sendBtn = button("ارسال", a1, a2, 13f)
        sendBtn.setOnClickListener { sendChat() }
        sendRow.addView(sendBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
        col.addView(sendRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })

        chatOverlay.addView(col, FrameLayout.LayoutParams(-1, -1))
        rootRef.addView(chatOverlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildProfileOverlay() {
        profileOverlay = FrameLayout(this)
        profileOverlay.setBackgroundColor(Color.parseColor("#F50A0A1F"))
        profileOverlay.visibility = View.GONE

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18f), dp(20f), dp(18f), dp(18f))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val hTitle = TextView(this)
        hTitle.text = "👤 حساب من"
        hTitle.textSize = 18f
        hTitle.setTextColor(cText)
        hTitle.typeface = Typeface.DEFAULT_BOLD
        head.addView(hTitle)
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val close = TextView(this)
        close.text = "✕"
        close.textSize = 17f
        close.setTextColor(cMuted)
        close.setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        close.setOnClickListener { closeProfile() }
        head.addView(close)
        col.addView(head)

        profileAvatar = TextView(this)
        profileAvatar.textSize = 40f
        profileAvatar.gravity = Gravity.CENTER
        profileAvatar.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a1, a2)).apply {
            shape = GradientDrawable.OVAL
        }
        selectedEmoji = prefs.getString("emoji", "") ?: ""
        if (selectedEmoji.isBlank()) selectedEmoji = "😎"
        profileAvatar.text = selectedEmoji
        val lpAv = LinearLayout.LayoutParams(dp(96f), dp(96f))
        lpAv.gravity = Gravity.CENTER_HORIZONTAL
        lpAv.topMargin = dp(16f)
        col.addView(profileAvatar, lpAv)

        val pickHint = TextView(this)
        pickHint.text = "آواتارت را انتخاب کن:"
        pickHint.textSize = 12f
        pickHint.setTextColor(cMuted)
        pickHint.gravity = Gravity.CENTER
        col.addView(pickHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        val pickerScroll = HorizontalScrollView(this)
        val pickerRow = LinearLayout(this)
        pickerRow.orientation = LinearLayout.HORIZONTAL
        for (e in emojiChoices) {
            val ev = TextView(this)
            ev.text = e
            ev.textSize = 22f
            ev.gravity = Gravity.CENTER
            ev.setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            ev.background = rounded(Color.parseColor("#1B1440"), 14f, if (e == selectedEmoji) a1 else Color.parseColor("#2A2258"), 1.5f)
            ev.setOnClickListener {
                selectedEmoji = e
                profileAvatar.text = e
                buildProfilePicker(pickerRow)
            }
            pickerRow.addView(ev, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6f) })
        }
        pickerScroll.addView(pickerRow)
        col.addView(pickerScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })

        fUser = field("نام کاربری")
        fName = field("اسم")
        fBio = field("بیو", single = false)
        fUser.setText(prefs.getString("username", "") ?: "")
        fName.setText(prefs.getString("name", "") ?: "")
        fBio.setText(prefs.getString("bio", "") ?: "")
        col.addView(fUser, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
        col.addView(fName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        col.addView(fBio, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        val saveBtn = button("ذخیره حساب", a1, a2, 14f)
        saveBtn.setOnClickListener {
            prefs.edit()
                .putString("emoji", selectedEmoji)
                .putString("username", fUser.text.toString().trim())
                .putString("name", fName.text.toString().trim())
                .putString("bio", fBio.text.toString().trim())
                .apply()
            closeProfile()
            render()
        }
        col.addView(saveBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })

        profileOverlay.addView(col, FrameLayout.LayoutParams(-1, -1))
        rootRef.addView(profileOverlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildProfilePicker(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val ev = row.getChildAt(i) as? TextView ?: continue
            val isSel = ev.text.toString() == selectedEmoji
            ev.background = rounded(Color.parseColor("#1B1440"), 14f, if (isSel) a1 else Color.parseColor("#2A2258"), 1.5f)
        }
    }

    private fun openChat() {
        chatOpen = true
        unread = 0
        chatOverlay.visibility = View.VISIBLE
        render()
    }

    private fun closeChat() {
        chatOpen = false
        chatOverlay.visibility = View.GONE
        render()
    }

    private fun openProfile() {
        profileOpen = true
        profileOverlay.visibility = View.VISIBLE
    }

    private fun closeProfile() {
        profileOpen = false
        profileOverlay.visibility = View.GONE
    }

    private fun addChatBubble(name: String, msg: String, mine: Boolean) {
        runCatching {
            val wrap = LinearLayout(this)
            wrap.orientation = LinearLayout.HORIZONTAL
            wrap.gravity = if (mine) Gravity.END else Gravity.START

            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL

            val headRow = LinearLayout(this)
            headRow.orientation = LinearLayout.HORIZONTAL
            headRow.gravity = Gravity.CENTER_VERTICAL

            var emo = ""
            for (p in CallManager.people) {
                if (p.name == name) {
                    emo = parseEmoji(p.bio).first
                }
            }
            if (mine && emo.isBlank()) emo = prefs.getString("emoji", "") ?: ""

            val av = TextView(this)
            av.text = emo.ifBlank { "👤" }
            av.textSize = 13f
            av.gravity = Gravity.CENTER
            av.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a1, a2)).apply {
                shape = GradientDrawable.OVAL
            }
            headRow.addView(av, LinearLayout.LayoutParams(dp(24f), dp(24f)))

            val nameTv = TextView(this)
            nameTv.text = name + if (mine) " (شما)" else ""
            nameTv.textSize = 11f
            nameTv.setTextColor(a1)
            nameTv.typeface = Typeface.DEFAULT_BOLD
            headRow.addView(nameTv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6f) })
            inner.addView(headRow)

            val bubble = TextView(this)
            bubble.text = msg
            bubble.textSize = 13.5f
            bubble.setTextColor(Color.WHITE)
            bubble.setPadding(dp(13f), dp(9f), dp(13f), dp(9f))
            bubble.background = if (mine) {
                rounded(Color.parseColor("#155E75"), 16f, a1, 1f)
            } else {
                rounded(Color.parseColor("#241C4D"), 16f, Color.parseColor("#3A2E75"), 1f)
            }
            inner.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4f) })

            wrap.addView(inner)
            chatList.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun sendChat() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return
        chatInput.setText("")
        val u = (prefs.getString("name", "") ?: "").ifBlank { prefs.getString("username", "") ?: "?" }
        scope.launch {
            runCatching {
                val o = JSONObject().put("t", "chat").put("n", u).put("m", text)
                publishBytes(o.toString().toByteArray(Charsets.UTF_8))
            }
        }
        addChatBubble(u, text, true)
    }

    private fun handlePacket(raw: String) {
        runCatching {
            val o = JSONObject(raw)
            when (o.optString("t")) {
                "chat" -> runOnUiThread {
                    if (alive) {
                        addChatBubble(o.optString("n"), o.optString("m"), false)
                        if (!chatOpen) {
                            unread++
                            render()
                        }
                    }
                }
                "react" -> runOnUiThread {
                    if (alive) spawnFloat(o.optString("e"), dp((60 + Math.random() * 220).toFloat()).toFloat(), resources.displayMetrics.heightPixels - dp(240f).toFloat(), 28f)
                }
            }
        }
    }

    private fun getRoom(): Room? = runCatching {
        val f = CallManager::class.java.getDeclaredField("room")
        f.isAccessible = true
        f.get(CallManager) as? Room
    }.getOrNull()

    private suspend fun publishBytes(data: ByteArray) {
        try {
            val room = getRoom() ?: return
            val lp = room.localParticipant
            val methods = lp.javaClass.methods.filter { it.name.startsWith("publishData") }
            for (m in methods) {
                try {
                    val args = ArrayList<Any?>()
                    var isSuspend = false
                    for (pt in m.parameterTypes) {
                        when {
                            pt == ByteArray::class.java -> args.add(data)
                            pt == java.lang.Boolean.TYPE || pt == java.lang.Boolean::class.java -> args.add(true)
                            pt == String::class.java -> args.add("chat")
                            pt.isEnum -> args.add(
                                pt.enumConstants.firstOrNull { (it as Enum<*>).name.contains("RELIABLE") }
                                    ?: pt.enumConstants[0]
                            )
                            List::class.java.isAssignableFrom(pt) -> args.add(emptyList<Any>())
                            pt.name == "kotlin.coroutines.Continuation" -> {
                                isSuspend = true
                                args.add(null)
                            }
                            else -> args.add(null)
                        }
                    }
                    if (isSuspend) {
                        suspendCoroutine<Any?> { cont ->
                            args[args.size - 1] = cont
                            try {
                                val r = m.invoke(lp, *args.toTypedArray())
                                if (r != kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                                    cont.resume(r)
                                }
                            } catch (e: Throwable) {
                                cont.resumeWithException(e)
                            }
                        }
                    } else {
                        m.invoke(lp, *args.toTypedArray())
                    }
                    return
                } catch (e: Throwable) {
                    // try next overload
                }
            }
        } catch (e: Throwable) {
            // give up silently
        }
    }

    private fun startEventListener() {
        if (eventsJob != null) return
        val room = getRoom() ?: return
        eventsJob = scope.launch {
            runCatching {
                room.events.collect { ev ->
                    if (ev is RoomEvent.DataReceived) {
                        val raw = runCatching { ev.data.toString(Charsets.UTF_8) }.getOrNull()
                        if (raw != null) handlePacket(raw)
                    }
                }
            }
        }
    }

    private fun showSplash() {
        runCatching {
            val splash = FrameLayout(this)
            splash.setBackgroundColor(Color.parseColor("#0A0A1F"))
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            val t = TextView(this)
            t.text = "esy call"
            t.textSize = 44f
            t.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            t.setTextColor(Color.WHITE)
            t.gravity = Gravity.CENTER
            gradientText(t, intArrayOf(a1, Color.WHITE, a2))
            box.addView(t)
            val sub = TextView(this)
            sub.text = "تماس صوتی با همه ✓"
            sub.textSize = 13f
            sub.setTextColor(cMuted)
            sub.gravity = Gravity.CENTER
            box.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10f) })
            splash.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            rootRef.addView(splash, FrameLayout.LayoutParams(-1, -1))
            splash.alpha = 0f
            splash.animate().alpha(1f).setDuration(350).withEndAction {
                splash.animate().alpha(0f).setDuration(500).setStartDelay(800).withEndAction {
                    runCatching { rootRef.removeView(splash) }
                }.start()
            }.start()
        }
    }

    private fun personRow(p: Person): View {
        val (emo, bio) = parseEmoji(p.bio)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
        row.background = rounded(
            Color.parseColor("#1C1545"), 20f,
            if (p.speaking) a1 else Color.parseColor("#2A2258"),
            if (p.speaking) 2f else 1f
        )

        val avatar = TextView(this)
        avatar.text = emo.ifBlank { (p.name.trim().firstOrNull() ?: '?').uppercaseChar().toString() }
        avatar.textSize = if (emo.isBlank()) 16f else 21f
        avatar.setTextColor(Color.WHITE)
        avatar.gravity = Gravity.CENTER
        avatar.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a1, a2)).apply {
            shape = GradientDrawable.OVAL
            if (p.speaking) setStroke(dp(2f), cGreen)
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(44f), dp(44f)))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(11f) })

        val nameTv = TextView(this)
        nameTv.text = p.name + if (p.me) "  (شما)" else ""
        nameTv.textSize = 14f
        nameTv.setTextColor(cText)
        nameTv.typeface = Typeface.DEFAULT_BOLD
        info.addView(nameTv)

        if (bio.isNotBlank()) {
            val bioTv = TextView(this)
            bioTv.text = bio
            bioTv.textSize = 11.5f
            bioTv.setTextColor(cMuted)
            bioTv.maxLines = 1
            info.addView(bioTv)
        }

        if (p.speaking) {
            val sp = TextView(this)
            sp.text = "در حال صحبت"
            sp.textSize = 10.5f
            sp.setTextColor(cGreen)
            sp.tag = "speakingLabel"
            row.addView(sp)
        }

        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9f) }

        val speakingNow = p.speaking
        val before = wasSpeaking[p.name] ?: false
        if (speakingNow && !before) {
            val loc = IntArray(2)
            avatar.getLocationOnScreen(loc)
            spawnFloat(avatar.text.toString(), loc[0].toFloat(), loc[1].toFloat(), 13f, true)
        }
        wasSpeaking[p.name] = speakingNow
        return row
    }

    private fun spawnFloat(txt: String, x: Float, y: Float, size: Float = 22f, circle: Boolean = false) {
        runCatching {
            val v = TextView(this)
            v.text = txt
            v.textSize = size
            v.gravity = Gravity.CENTER
            if (circle) {
                v.setTextColor(Color.WHITE)
                v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a1, a2)).apply {
                    shape = GradientDrawable.OVAL
                }
                v.setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            }
            v.x = x - dp(14f)
            v.y = y - dp(14f)
            rootRef.addView(v)
            v.animate()
                .translationYBy(-dp((110 + Math.random() * 70).toFloat()).toFloat())
                .alpha(0f)
                .rotation((Math.random() * 40 - 20).toFloat())
                .setDuration(1300)
                .withEndAction { runCatching { rootRef.removeView(v) } }
                .start()
        }
    }

    private fun reactionBurst() {
        runCatching {
            vibrate(25)
            val emojis = listOf("🔥", "👏", "😂", "😮", "🎉", "💯")
            val pick = emojis[(Math.random() * emojis.size).toInt()]
            for (i in 0..4) {
                val x = dp((40 + Math.random() * 260).toFloat())
                val y = resources.displayMetrics.heightPixels - dp(180f)
                spawnFloat(pick, x.toFloat(), y.toFloat(), 28f)
            }
            scope.launch {
                runCatching {
                    val o = JSONObject().put("t", "react").put("e", pick)
                    publishBytes(o.toString().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private fun joinBurst(name: String) {
        runCatching {
            vibrate(35)
            for (i in 0..3) {
                spawnFloat("✨", dp((60 + Math.random() * 220).toFloat()).toFloat(), dp(120f).toFloat(), 22f)
            }
        }
    }

    private fun toggleSpeaker() {
        runCatching {
            speakerOn = !speakerOn
            audioManager.isSpeakerphoneOn = speakerOn
            vibrate(20)
        }
        render()
    }

    private fun vibrate(ms: Long) {
        runCatching {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(ms)
            }
        }
    }

    private fun beep() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
            android.os.Handler(mainLooper).postDelayed({
                runCatching {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
                    android.os.Handler(mainLooper).postDelayed({ runCatching { tg.release() } }, 500)
                }
            }, 220)
        }
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (!alive) return@addUpdateListener
                val t = anim.animatedValue as Float

                for ((i, v) in orbs.withIndex()) {
                    val ph = t * 2f * Math.PI.toFloat() + i * 1.1f
                    v.translationX = sin(ph) * dp(if (i == 1) 10f else 15f)
                    v.translationY = cos(ph) * dp(11f)
                }

                for ((v, phase) in particles) {
                    val frac = (t * 0.22f + phase) % 1f
                    v.translationY = -frac * dp(70f)
                    v.alpha = 0.4f * sin(Math.PI * frac).toFloat()
                }

                waveActive = lastStatus == "connected" && CallManager.people.any { it.speaking }
                val waveLevel = if (waveActive) 0.55f + 0.45f * abs(sin(t * 2f * Math.PI.toFloat() * 2f)) else 0.12f
                waveBottom.activity = waveLevel
                waveBottom.invalidate()
                waveTop.activity = waveLevel
                waveTop.invalidate()

                if (::micView.isInitialized) {
                    micView.muted = CallManager.muted
                    micView.level = if (waveActive) 0.55f + 0.45f * abs(sin(t * 2f * Math.PI.toFloat() * 2f)) else 0.15f
                    micView.invalidate()
                }

                if (callStart > 0) {
                    timerText.text = "⏱ " + fmtDur((System.currentTimeMillis() - callStart) / 1000)
                }

                if (lastStatus == "connecting" || lastStatus == "reconnecting") {
                    val dots = ".".repeat(((System.currentTimeMillis() / 400) % 4).toInt())
                    statusText.text = if (lastStatus == "connecting") "در حال اتصال$dots" else "اتصال مجدد$dots"
                }

                if (::callBtn.isInitialized && callBtn.visibility == View.VISIBLE) {
                    val pulse = 0.5f + 0.5f * sin(t * 2f * Math.PI.toFloat())
                    callBtn.alpha = 0.90f + 0.10f * pulse
                    callBtn.scaleX = 1f + 0.015f * pulse
                    callBtn.scaleY = 1f + 0.015f * pulse
                }

                if (::peopleBox.isInitialized) {
                    for (i in 0 until peopleBox.childCount) {
                        val r = peopleBox.getChildAt(i) as? LinearLayout ?: continue
                        val lbl = r.findViewWithTag<TextView>("speakingLabel") ?: continue
                        lbl.alpha = 0.55f + 0.45f * abs(sin(t * 2f * Math.PI.toFloat() + i))
                    }
                }
            }
            start()
        }
    }

    private fun render() {
        val st = CallManager.status
        lastStatus = st

        statusText.text = when (st) {
            "connecting" -> "در حال اتصال..."
            "connected" -> "متصل"
            "reconnecting" -> "در حال اتصال مجدد..."
            else -> "اماده تماس"
        }
        val dotColor = when (st) {
            "connected" -> cGreen
            "connecting", "reconnecting" -> cAmber
            else -> cGray
        }
        (statusDot.background as GradientDrawable).setColor(dotColor)

        val inCall = st == "connected" || st == "connecting" || st == "reconnecting"
        callBtn.visibility = if (inCall) View.GONE else View.VISIBLE
        micView.visibility = if (st == "connected") View.VISIBLE else View.GONE
        hintText.visibility = if (inCall) View.GONE else View.VISIBLE
        muteBtn.visibility = if (st == "connected") View.VISIBLE else View.GONE
        chatBtn.visibility = if (st == "connected") View.VISIBLE else View.GONE
        reactBtn.visibility = if (st == "connected") View.VISIBLE else View.GONE
        endBtn.visibility = if (inCall) View.VISIBLE else View.GONE
        statsRow.visibility = if (inCall) View.VISIBLE else View.GONE

        chatBtn.text = if (unread > 0) "💬" else "💬"

        muteBtn.text = if (speakerOn) "🔊" else "🔈"

        if (st == "connected" && callStart == 0L) {
            callStart = System.currentTimeMillis()
            vibrate(55)
            beep()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            runCatching {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = speakerOn
            }
            startPing()
            startEventListener()
        }
        if (st == "idle") {
            callStart = 0L
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            wasSpeaking.clear()
            knownNames.clear()
        }

        countText.text = "👥 " + fa(CallManager.people.size.toString()) + " نفر"
        pingText.text = if (pingMs >= 0) "📶 " + fa(pingMs.toString()) + "ms" else "📶 ..."

        val err = CallManager.error
        if (err.isNullOrBlank()) {
            errorCard.visibility = View.GONE
        } else {
            errorCard.visibility = View.VISIBLE
            errorCard.text = err
        }

        peopleBox.removeAllViews()
        for (p in CallManager.people) {
            peopleBox.addView(personRow(p))
            if (p.name !in knownNames) {
                knownNames.add(p.name)
                if (knownNames.size > 1 || p.me) {
                    if (st == "connected") joinBurst(p.name)
                }
            }
        }
        if (st == "connected" && CallManager.people.size <= 1) {
            val waiting = TextView(this)
            waiting.text = "هنوز کسی نیست — دوستانت را دعوت کن! ✨"
            waiting.textSize = 12f
            waiting.setTextColor(cMuted)
            waiting.gravity = Gravity.CENTER
            peopleBox.addView(waiting, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })
        }
    }

    private fun onCallClick() {
        val st = CallManager.status
        if (st == "connected" || st == "connecting" || st == "reconnecting") return
        val u = (prefs.getString("username", "") ?: "").trim()
        val n = (prefs.getString("name", "") ?: "").trim()
        if (u.isEmpty() || n.isEmpty()) {
            openProfile()
            errorCard.visibility = View.VISIBLE
            errorCard.text = "لطفا اول حساب را کامل کن (نام کاربری و اسم)"
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingConnect = true
            val perms = arrayListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 100)
            return
        }
        startCall()
    }

    private fun startCall() {
        val u = (prefs.getString("username", "") ?: "").trim()
        val n = (prefs.getString("name", "") ?: "").trim()
        val emo = prefs.getString("emoji", "") ?: ""
        val b = prefs.getString("bio", "") ?: ""
        CallManager.connect(this, SERVER, u, n, "$emo|$b", deviceId())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && pendingConnect) {
            pendingConnect = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCall()
            } else {
                errorCard.visibility = View.VISIBLE
                errorCard.text = "بدون اجازه میکروفون نمی توان تماس گرفت"
            }
        }
    }

    private fun deviceId(): String {
        var d = prefs.getString("deviceId", null)
        if (d.isNullOrBlank()) {
            d = try {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            } catch (e: Exception) {
                ""
            }
            if (d.isNullOrBlank()) d = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", d).apply()
        }
        return d
    }

    private fun startPing() {
        if (pingThread != null) return
        pingThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val ms = runCatching {
                    val t0 = System.currentTimeMillis()
                    val c = URL(SERVER).openConnection() as HttpURLConnection
                    c.connectTimeout = 3000
                    c.readTimeout = 3000
                    c.requestMethod = "HEAD"
                    c.responseCode
                    System.currentTimeMillis() - t0
                }.getOrNull()
                runOnUiThread { pingMs = ms?.toInt() ?: -1 }
                try {
                    Thread.sleep(4000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun doToggleMute() {
        runCatching {
            val m = CallManager::class.java.methods.firstOrNull {
                it.name == "toggleMute" || it.name == "toggleMic" || it.name == "toggleMicrophone"
            }
            if (m != null) {
                m.invoke(CallManager)
                return
            }
            val s = CallManager::class.java.methods.firstOrNull {
                it.name == "setMuted" && it.parameterCount == 1
            }
            if (s != null) {
                s.invoke(CallManager, !CallManager.muted)
                return
            }
            CallManager.muted = !CallManager.muted
        }
    }

    private fun doHangup() {
        runCatching {
            for (nm in listOf("hangup", "hangUp", "disconnect", "leave", "endCall", "stopCall", "cleanup", "stop")) {
                val m = CallManager::class.java.methods.firstOrNull { it.name == nm && it.parameterCount == 0 }
                if (m != null) {
                    m.invoke(CallManager)
                    return
                }
            }
        }
    }

    override fun onBackPressed() {
        if (chatOpen) {
            closeChat()
            return
        }
        if (profileOpen) {
            closeProfile()
            return
        }
        val st = CallManager.status
        if (st == "connected" || st == "connecting" || st == "reconnecting") {
            doHangup()
            render()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
