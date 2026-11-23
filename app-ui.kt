package com.esrafil.voicecall

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class Msg(
    val from: String,
    val text: String,
    val mine: Boolean,
    var status: Int,
    val time: String
)

/* ============================ ICONS (vector paths) ============================ */
private object Icons {
    fun draw(c: Canvas, kind: String, color: Int, size: Float, cx: Float, cy: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 7f
            this.color = color
        }
        val pf = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        c.scale(size / 100f, size / 100f)
        val path = Path()
        when (kind) {
            "mic" -> {
                c.drawRoundRect(RectF(37f, 10f, 63f, 58f), 13f, 13f, p)
                path.reset()
                path.arcTo(RectF(25f, 38f, 75f, 80f), 0f, 180f, true)
                c.drawPath(path, p)
                c.drawLine(50f, 80f, 50f, 92f, p)
                c.drawLine(34f, 92f, 66f, 92f, p)
            }
            "micOff" -> {
                c.drawRoundRect(RectF(37f, 10f, 63f, 58f), 13f, 13f, p)
                path.reset()
                path.arcTo(RectF(25f, 38f, 75f, 80f), 0f, 180f, true)
                c.drawPath(path, p)
                c.drawLine(50f, 80f, 50f, 92f, p)
                c.drawLine(24f, 12f, 76f, 94f, p)
            }
            "speaker" -> {
                path.reset()
                path.moveTo(20f, 42f)
                path.lineTo(36f, 42f)
                path.lineTo(57f, 23f)
                path.lineTo(57f, 77f)
                path.lineTo(36f, 58f)
                path.lineTo(20f, 58f)
                path.close()
                c.drawPath(path, p)
                path.reset()
                path.arcTo(RectF(62f, 30f, 90f, 70f), -52f, 104f, true)
                c.drawPath(path, p)
            }
            "speakerOff" -> {
                path.reset()
                path.moveTo(20f, 42f)
                path.lineTo(36f, 42f)
                path.lineTo(57f, 23f)
                path.lineTo(57f, 77f)
                path.lineTo(36f, 58f)
                path.lineTo(20f, 58f)
                path.close()
                c.drawPath(path, p)
                c.drawLine(68f, 36f, 88f, 64f, p)
                c.drawLine(88f, 36f, 68f, 64f, p)
            }
            "chat" -> {
                c.drawRoundRect(RectF(14f, 18f, 86f, 64f), 16f, 16f, p)
                path.reset()
                path.moveTo(32f, 64f)
                path.lineTo(32f, 86f)
                path.lineTo(52f, 64f)
                path.close()
                c.drawPath(path, p)
                c.drawLine(32f, 41f, 68f, 41f, p)
            }
            "smile" -> {
                c.drawCircle(50f, 50f, 37f, p)
                c.drawCircle(36f, 40f, 5f, pf)
                c.drawCircle(64f, 40f, 5f, pf)
                path.reset()
                path.arcTo(RectF(28f, 32f, 72f, 72f), 20f, 140f, true)
                c.drawPath(path, p)
            }
            "hangup" -> {
                c.save()
                c.rotate(133f, 50f, 50f)
                c.drawRoundRect(RectF(32f, 14f, 68f, 86f), 20f, 20f, p)
                c.drawLine(32f, 36f, 68f, 36f, p)
                c.drawLine(32f, 64f, 68f, 64f, p)
                c.restore()
            }
            "phone" -> {
                path.reset()
                path.moveTo(22f, 26f)
                path.lineTo(38f, 26f)
                path.lineTo(44f, 44f)
                path.lineTo(35f, 52f)
                path.cubicTo(40f, 66f, 52f, 74f, 60f, 70f)
                path.lineTo(68f, 60f)
                path.lineTo(82f, 68f)
                path.lineTo(82f, 82f)
                path.cubicTo(58f, 88f, 18f, 58f, 18f, 34f)
                path.close()
                c.drawPath(path, p)
            }
            "send" -> {
                path.reset()
                path.moveTo(14f, 50f)
                path.lineTo(88f, 18f)
                path.lineTo(64f, 88f)
                path.lineTo(50f, 58f)
                path.close()
                c.drawPath(path, p)
                c.drawLine(50f, 58f, 88f, 18f, p)
            }
            "back" -> {
                path.reset()
                path.moveTo(62f, 20f)
                path.lineTo(34f, 50f)
                path.lineTo(62f, 80f)
                c.drawPath(path, p)
            }
            "camera" -> {
                c.drawRoundRect(RectF(12f, 30f, 88f, 84f), 12f, 12f, p)
                path.reset()
                path.moveTo(34f, 30f)
                path.lineTo(41f, 16f)
                path.lineTo(59f, 16f)
                path.lineTo(66f, 30f)
                c.drawPath(path, p)
                c.drawCircle(50f, 58f, 15f, p)
            }
            "check" -> {
                path.reset()
                path.moveTo(22f, 52f)
                path.lineTo(43f, 73f)
                path.lineTo(80f, 30f)
                c.drawPath(path, p)
            }
            "clock" -> {
                c.drawCircle(50f, 50f, 38f, p)
                c.drawLine(50f, 50f, 50f, 26f, p)
                c.drawLine(50f, 50f, 66f, 58f, p)
            }
            "retry" -> {
                path.reset()
                path.arcTo(RectF(20f, 20f, 80f, 80f), 40f, 280f, true)
                c.drawPath(path, p)
                path.reset()
                path.moveTo(14f, 30f)
                path.lineTo(30f, 42f)
                path.lineTo(28f, 20f)
                path.close()
                c.drawPath(path, pf)
            }
            "moon" -> {
                path.reset()
                path.arcTo(RectF(18f, 12f, 82f, 76f), 125f, 230f, true)
                path.arcTo(RectF(36f, 2f, 100f, 66f), 280f, -195f, true)
                c.drawPath(path, p)
            }
            "logout" -> {
                c.drawRoundRect(RectF(12f, 16f, 50f, 84f), 8f, 8f, p)
                c.drawLine(62f, 50f, 92f, 50f, p)
                path.reset()
                path.moveTo(76f, 34f)
                path.lineTo(94f, 50f)
                path.lineTo(76f, 66f)
                c.drawPath(path, p)
            }
        }
        c.restore()
    }
}

private class IconView(context: Context, var kind: String) : View(context) {
    var color = Color.WHITE
    override fun onDraw(c: Canvas) {
        Icons.draw(c, kind, color, min(width, height) * 0.92f, width / 2f, height / 2f)
    }
}

private class CircleIconButton(context: Context, var kind: String, val onTap: () -> Unit) : View(context) {
    var accent = Color.parseColor("#22D3EE")
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f }
    private val ic = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val pr = if (isPressed) 0.92f else 1f
        c.save()
        c.scale(pr, pr, cx, cy)
        glow.color = (accent and 0x00FFFFFF) or (0x38 shl 24)
        c.drawCircle(cx, cy, r * 1.10f, glow)
        bg.color = Color.parseColor("#121A30")
        c.drawCircle(cx, cy, r * 0.94f, bg)
        st.color = (accent and 0x00FFFFFF) or (0x66 shl 24)
        c.drawCircle(cx, cy, r * 0.94f, st)
        ic.color = if (isPressed) accent else Color.parseColor("#DCE7FF")
        Icons.draw(c, kind, ic.color, r * 1.02f, cx, cy)
        c.restore()
    }
}

/* ============================ WAVES v2 ============================ */
private class WaveView(context: Context) : View(context) {
    var active = false
    var accent = Color.parseColor("#22D3EE")
    var accent2 = Color.parseColor("#3B82F6")
    var bgColor = Color.parseColor("#05070F")
    var amp = 1f
    private var t = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crest = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.4f }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val stars = Array(26) { floatArrayOf((Math.random() * 1000).toFloat(), (Math.random() * 1000).toFloat(), (Math.random() * 6.28f).toFloat()) }

    fun tick(dt: Float) {
        t += dt * (if (active) 1.7f else 0.55f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val base = h * (if (active) 0.42f else 0.58f)
        val a = h * (if (active) 0.13f else 0.055f) * amp

        for (i in stars.indices) {
            val s = stars[i]
            val x = (s[0] + t * 12f) % w
            val y = (s[1] * 0.62f) % h
            val tw = 0.5f + 0.5f * sin(t * 1.9f + s[2])
            star.color = (Color.WHITE and 0x00FFFFFF) or (((if (active) 70 else 34) * tw).toInt() shl 24)
            c.drawCircle(x, y, 1.6f + tw * 1.8f, star)
        }

        for (layer in 0 until 3) {
            path.reset()
            path.moveTo(0f, h)
            path.lineTo(0f, base)
            val ph = t * (1f + layer * 0.42f) + layer * 1.7f
            var x = 0f
            while (x <= w) {
                val y = base +
                        sin(x / (150f + layer * 72f) + ph) * a * (1f - layer * 0.18f) +
                        sin(x / (66f + layer * 40f) + ph * 1.6f) * a * 0.32f
                path.lineTo(x, y)
                x += 10f
            }
            path.lineTo(w, h)
            path.close()
            val c1 = if (layer == 0) accent else if (layer == 1) accent2 else Color.parseColor("#7C4DFF")
            paint.shader = LinearGradient(0f, base - a * 2f, 0f, h, (c1 and 0x00FFFFFF) or (((if (active) 120 else 52) - layer * 14) shl 24), (bgColor and 0x00FFFFFF) or (0 shl 24), Shader.TileMode.CLAMP)
            c.drawPath(path, paint)
            if (layer == 0) {
                crest.color = (Color.WHITE and 0x00FFFFFF) or ((if (active) 120 else 46) shl 24)
                var px = 0f
                var started = false
                path.reset()
                while (px <= w) {
                    val y = base + sin(px / 150f + ph) * a + sin(px / 66f + ph * 1.6f) * a * 0.32f
                    if (!started) {
                        path.moveTo(px, y)
                        started = true
                    } else path.lineTo(px, y)
                    px += 10f
                }
                c.drawPath(path, crest)
            }
        }
    }
}

/* ============================ MIC CIRCLE v2 ============================ */
private class MicCircleView(context: Context) : View(context) {
    var level = 0f
    var muted = false
    var accent = Color.parseColor("#22D3EE")
    var accent2 = Color.parseColor("#3B82F6")
    private var angle = 0f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6.5f }
    private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.6f }

    fun tick(dt: Float) {
        angle += dt * (if (level > 0.12f) 86f else 26f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val rO = r * 0.86f
        val rCore = r * 0.62f
        val cA = if (muted) Color.parseColor("#FF6B4A") else accent
        val cB = if (muted) Color.parseColor("#FF5252") else accent2

        fill.color = (cA and 0x00FFFFFF) or (0x26 shl 24)
        c.drawCircle(cx, cy, rO * 1.12f, fill)

        val sweep = SweepGradient(cx, cy, intArrayOf(cA, cB, cA), null)
        val m = Matrix()
        m.postRotate(angle, cx, cy)
        sweep.setLocalMatrix(m)
        ring.shader = sweep
        c.drawCircle(cx, cy, rO, ring)
        ring.shader = null

        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30f + angle * 0.32f).toDouble())
            val x1 = cx + kotlin.math.cos(a).toFloat() * rO * 1.10f
            val y1 = cy + kotlin.math.sin(a).toFloat() * rO * 1.10f
            val x2 = cx + kotlin.math.cos(a).toFloat() * rO * 1.18f
            val y2 = cy + kotlin.math.sin(a).toFloat() * rO * 1.18f
            tickP.color = (cA and 0x00FFFFFF) or (0x55 shl 24)
            c.drawLine(x1, y1, x2, y2, tickP)
        }

        val core = RadialGradient(cx, cy, rCore, intArrayOf(Color.parseColor("#1B2547"), Color.parseColor("#0B1226")), null, Shader.TileMode.CLAMP)
        fill.shader = core
        c.drawCircle(cx, cy, rCore, fill)
        fill.shader = null

        for (k in 0 until 3) {
            val scale = 1.10f + 0.30f * k + level * (0.34f + 0.30f * k)
            ripple.color = (cA and 0x00FFFFFF) or (((120 - k * 34) * (0.35f + level)).toInt().coerceIn(8, 140) shl 24)
            c.drawCircle(cx, cy, rCore * scale, ripple)
        }

        Icons.draw(c, if (muted) "micOff" else "mic", if (muted) Color.parseColor("#FF8A65") else Color.parseColor("#EAF2FF"), rCore * 1.06f, cx, cy)
    }
}

/* ============================ AVATAR ============================ */
private class AvatarDrawable(val bmp: Bitmap?, val emoji: String, val c1: Int, val c2: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = min(b.width(), b.height()) / 2f
        if (r <= 1f) return
        p.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        if (bmp != null) {
            val sc = (r * 2f * 0.88f) / min(bmp.width, bmp.height).toFloat()
            val m = Matrix()
            m.setScale(sc, sc)
            m.postTranslate(cx - bmp.width * sc / 2f, cy - bmp.height * sc / 2f)
            val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sh.setLocalMatrix(m)
            p.shader = sh
            c.drawCircle(cx, cy, r * 0.88f, p)
            p.shader = null
        } else {
            p.shader = null
            p.color = Color.WHITE
            p.textAlign = Paint.Align.CENTER
            p.textSize = r * 1.02f
            p.typeface = Typeface.DEFAULT_BOLD
            val fm = p.fontMetrics
            c.drawText(emoji.ifBlank { "👤" }, cx, cy - (fm.ascent + fm.descent) / 2f, p)
        }
        ringP.shader = SweepGradient(cx, cy, intArrayOf(c1, c2, c1), null)
        ringP.strokeWidth = r * 0.10f
        c.drawCircle(cx, cy, r * 0.96f, ringP)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/* ============================ MAIN ============================ */
class MainActivity : Activity() {

    private companion object {
        const val SERVER = "https://shrill-wave-0b90.esrafilnazeri.workers.dev"
        const val PREFS = "voicecall"
        const val REQ_PHOTO = 71
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

    private val cText = Color.parseColor("#EAF2FF")
    private val cMuted = Color.parseColor("#8FA3C8")
    private val cGreen = Color.parseColor("#2EE6A8")
    private val cAmber = Color.parseColor("#FBBF24")
    private val cRed = Color.parseColor("#FF6B4A")

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
    private lateinit var timerText: TextView
    private lateinit var countText: TextView
    private lateinit var micView: MicCircleView
    private lateinit var callBtn: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private lateinit var speakerBtn: CircleIconButton
    private lateinit var chatBtn: CircleIconButton
    private lateinit var endBtn: CircleIconButton
    private lateinit var reactBtn: CircleIconButton
    private lateinit var hintText: TextView
    private lateinit var waveBottom: WaveView
    private lateinit var waveTop: WaveView
    private lateinit var topAvatar: ImageView
    private lateinit var topName: TextView
    private lateinit var topUser: TextView
    private lateinit var lastChatChip: TextView

    private lateinit var chatPage: FrameLayout
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatInput: EditText
    private lateinit var chatMicBtn: CircleIconButton
    private lateinit var chatHeaderAvatar: ImageView
    private lateinit var chatSubTv: TextView

    private lateinit var profileOverlay: FrameLayout
    private lateinit var profileAvatar: ImageView
    private lateinit var nameField: EditText
    private lateinit var userField: EditText
    private lateinit var bioField: EditText

    private val chatMessages = ArrayList<Msg>()
    private val peerAvatars = HashMap<String, Bitmap>()
    private var chatOpen = false
    private var profileOpen = false
    private var unread = 0
    private var selectedEmoji = "😎"
    private var ticker: ValueAnimator? = null
    private var eventsJob: Job? = null
    private var callStart = 0L
    private var lastStatus = "idle"
    private var speakerOn = true
    private var alive = true
    private var waveActive = false
    private var cachedRoom: Any? = null
    private var lastSendErr = ""
    private var lastAvSent = 0L
    private var lastPeopleCount = 0
    private var quoteIdx = 0
    private var quoteT = 0f

    private val quotes = arrayOf(
        "🎤 صدات داره می‌رسه!",
        "🔥 گفتگوی زنده — خوش بگذره",
        "🌐 همه با هم در یک اتاق عمومی",
        "⚡ امواج صدا زنده‌اند",
        "🚀 نسخه ۶ — سریع‌تر و خفن‌تر"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        themeIndex = prefs.getInt("theme", 0)
        selectedEmoji = prefs.getString("emoji", "😎") ?: "😎"

        rootRef = FrameLayout(this)
        buildUi(rootRef)
        setContentView(rootRef)
        showSplash()
        render()
        startTicker()

        CallManager.onChange = { runOnUiThread { if (alive) render() } }
    }

    override fun onDestroy() {
        alive = false
        ticker?.cancel()
        eventsJob?.cancel()
        CallManager.onChange = null
        super.onDestroy()
    }

    /* ------------------------- helpers ------------------------- */
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun fa(s: String): String {
        val f = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch in '0'..'9') f[ch - '0'] else ch)
        return sb.toString()
    }

    private fun fmtDur(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun parseEmoji(bio: String): Pair<String, String> {
        val i = bio.indexOf('|')
        return if (i >= 0) bio.substring(0, i) to bio.substring(i + 1) else "" to bio
    }

    private fun myName(): String =
        (prefs.getString("name", "") ?: "").ifBlank { prefs.getString("username", "") ?: "؟" }

    private fun rounded(bg: Int, radius: Float = 18f, stroke: Int = 0, strokeW: Float = 1.5f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(bg)
            if (stroke != 0) setStroke(dp(strokeW), stroke)
        }

    private fun field(hint: String, single: Boolean = true): EditText =
        EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.parseColor("#5C6C92"))
            setTextColor(cText)
            textSize = 14f
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            if (single) inputType = InputType.TYPE_CLASS_TEXT else inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = if (single) 1 else 4
            background = rounded(Color.parseColor("#0F1730"), 16f, Color.parseColor("#27355C"), 1.4f)
        }

    private fun button(text: String, c1: Int, c2: Int, size: Float = 15f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(22f), dp(15f), dp(22f), dp(15f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
                cornerRadius = dp(18f).toFloat()
            }
        }

    private fun smallPill(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 10.5f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(dp(11f), dp(5f), dp(11f), dp(5f))
            background = rounded(Color.parseColor("#111A33"), 12f, Color.parseColor("#25335C"), 1f)
        }

    private fun gradientText(tv: TextView, colors: IntArray) {
        tv.paint.shader = LinearGradient(0f, 0f, tv.paint.textSize * 6f, 0f, colors[0], colors[1], Shader.TileMode.CLAMP)
        tv.invalidate()
    }

    /* ------------------------- UI build ------------------------- */
    private fun buildUi(root: FrameLayout) {
        val outer = FrameLayout(this)

        waveBottom = WaveView(this)
        waveBottom.accent = a1
        waveBottom.accent2 = a2
        outer.addView(waveBottom, FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(dp(18f), dp(30f), dp(18f), dp(210f))
        scroll.addView(main, FrameLayout.LayoutParams(-1, -2))
        outer.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        // ===== TOP ACCOUNT BAR =====
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF0C1226.toInt(), 0xFF12204A.toInt())).apply {
                cornerRadius = dp(20f).toFloat()
                setStroke(dp(1.2f), 0xFF2B3B66.toInt())
            }
        }
        topAvatar = ImageView(this)
        topAvatar.setOnClickListener { openProfile() }
        top.addView(topAvatar, LinearLayout.LayoutParams(dp(46f), dp(46f)))

        val nameCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        topName = TextView(this).apply {
            text = myName()
            textSize = 15f
            setTextColor(cText)
            typeface = Typeface.DEFAULT_BOLD
        }
        topUser = TextView(this).apply {
            text = "@" + (prefs.getString("username", "") ?: "")
            textSize = 11f
            setTextColor(a1)
        }
        nameCol.addView(topName, LinearLayout.LayoutParams(-2, -2))
        nameCol.addView(topUser, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
        top.addView(nameCol, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10f) })

        val verChip = smallPill("نسخه ۶")
        verChip.setTextColor(a1)
        top.addView(verChip)
        top.setOnClickListener { openProfile() }
        main.addView(top, LinearLayout.LayoutParams(-1, -2))

        // ===== STATUS =====
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(16f), dp(10f), dp(4f))
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cRed) }
        }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(11f), dp(11f)))
        statusText = TextView(this).apply {
            text = "● آماده برای تماس"
            textSize = 13.5f
            setTextColor(cMuted)
        }
        statusRow.addView(statusText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
        main.addView(statusRow, LinearLayout.LayoutParams(-1, -2))

        errorCard = TextView(this).apply {
            visibility = View.GONE
            textSize = 11.5f
            setTextColor(cRed)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = rounded(0x1AFF6B4A, 14f, 0x55FF6B4A, 1f)
        }
        main.addView(errorCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })

        // ===== CALL BUTTON =====
        callBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(26f), dp(17f), dp(26f), dp(17f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a1, a2)).apply {
                cornerRadius = dp(20f).toFloat()
            }
            setOnClickListener { onCallClick() }
        }
        val phIc = IconView(this, "phone").apply { color = Color.WHITE }
        callBtn.addView(phIc, LinearLayout.LayoutParams(dp(24f), dp(24f)))
        val callTv = TextView(this).apply {
            text = "شروع تماس"
            textSize = 16.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        callBtn.addView(callTv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10f) })
        main.addView(callBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })

        // ===== MIC CIRCLE =====
        val micWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22f), 0, 0)
        }
        micView = MicCircleView(this)
        micView.accent = a1
        micView.accent2 = a2
        micView.setOnClickListener { doToggleMute() }
        micWrap.addView(micView, LinearLayout.LayoutParams(dp(168f), dp(168f)))
        val micLabel = TextView(this).apply {
            text = "میکروفون — لمس برای بی‌صدا"
            textSize = 11.5f
            setTextColor(cMuted)
        }
        micWrap.addView(micLabel, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10f) })
        main.addView(micWrap, LinearLayout.LayoutParams(-1, -2))

        // ===== ACTION BUTTONS =====
        actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22f), 0, 0)
        }
        speakerBtn = CircleIconButton(this, "speaker") { toggleSpeaker() }
        chatBtn = CircleIconButton(this, "chat") { openChat() }
        reactBtn = CircleIconButton(this, "smile") { quickReact() }
        endBtn = CircleIconButton(this, "hangup") { doHangup(); render() }
        for (b in listOf(speakerBtn, chatBtn, reactBtn, endBtn)) {
            b.accent = a1
            actionsRow.addView(b, LinearLayout.LayoutParams(dp(62f), dp(62f)).apply { marginStart = dp(12f); marginEnd = dp(12f) })
        }
        main.addView(actionsRow, LinearLayout.LayoutParams(-1, -2))

        // ===== STATS =====
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            background = rounded(0xCC0C1226.toInt(), 18f, 0xFF22305C.toInt(), 1.2f)
        }
        timerText = TextView(this).apply { text = "⏱ 00:00"; textSize = 13f; setTextColor(cText) }
        countText = TextView(this).apply { text = "👥 ۰ نفر"; textSize = 13f; setTextColor(cText) }
        statsCard.addView(timerText, LinearLayout.LayoutParams(0, -2, 1f))
        statsCard.addView(countText, LinearLayout.LayoutParams(0, -2, 1f).apply { gravity = Gravity.END })
        main.addView(statsCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })

        // ===== LAST CHAT CHIP =====
        lastChatChip = TextView(this).apply {
            text = "💬 آخرین پیام — برای باز کردن چت ضربه بزن"
            textSize = 11.5f
            setTextColor(cMuted)
            setPadding(dp(15f), dp(12f), dp(15f), dp(12f))
            background = rounded(0xCC0C1226.toInt(), 16f, 0xFF22305C.toInt(), 1.2f)
            setOnClickListener { openChat() }
        }
        main.addView(lastChatChip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        // ===== PEOPLE =====
        val peopleTitle = TextView(this).apply {
            text = "حاضرین در تماس"
            textSize = 13f
            setTextColor(cText)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4f), dp(22f), dp(4f), dp(8f))
        }
        main.addView(peopleTitle, LinearLayout.LayoutParams(-1, -2))

        val peopleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            background = rounded(0xCC0C1226.toInt(), 18f, 0xFF22305C.toInt(), 1.2f)
        }
        peopleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        peopleCard.addView(peopleBox, LinearLayout.LayoutParams(-1, -2))
        main.addView(peopleCard, LinearLayout.LayoutParams(-1, -2))

        hintText = TextView(this).apply {
            text = "هنوز کسی نیست — دوستانت را دعوت کن!\nکافیه برنامه رو نصب کنن و دکمه «شروع تماس» رو بزنن."
            textSize = 12f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(18f), dp(10f), dp(10f))
        }
        main.addView(hintText, LinearLayout.LayoutParams(-1, -2))

        // ===== SETTINGS =====
        buildSettings(main)

        // ===== WAVES TOP (overlay small) =====
        waveTop = WaveView(this)
        waveTop.accent = a2
        waveTop.accent2 = a1
        outer.addView(waveTop, FrameLayout.LayoutParams(-1, dp(120f), Gravity.TOP))

        root.addView(outer, FrameLayout.LayoutParams(-1, -1))

        buildChatPage(root)
        buildProfileOverlay(root)
    }

    private fun buildSettings(main: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            background = rounded(0xCC0C1226.toInt(), 18f, 0xFF22305C.toInt(), 1.2f)
        }
        val t = TextView(this).apply {
            text = "⚙️ تنظیمات"
            textSize = 13f
            setTextColor(cText)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(t, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10f) })

        val themeLabel = TextView(this).apply { text = "🎨 رنگ تماس"; textSize = 11.5f; setTextColor(cMuted) }
        card.addView(themeLabel, LinearLayout.LayoutParams(-2, -2))
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in themes.indices) {
            val chip = TextView(this).apply {
                text = themes[i].first
                textSize = 11f
                setTextColor(if (i == themeIndex) Color.WHITE else cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(11f), dp(7f), dp(11f), dp(7f))
                background = if (i == themeIndex) {
                    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor(themes[i].second), Color.parseColor(themes[i].third))).apply { cornerRadius = dp(12f).toFloat() }
                } else rounded(Color.parseColor("#111A33"), 12f, Color.parseColor("#25335C"), 1f)
                setOnClickListener {
                    themeIndex = i
                    prefs.edit().putInt("theme", i).apply()
                    recreate()
                }
            }
            themeRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6f) })
        }
        card.addView(themeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f); bottomMargin = dp(10f) })

        val togRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun tog(label: String, key: String, def: Boolean): LinearLayout {
            val on = prefs.getBoolean(key, def)
            val b = TextView(this).apply {
                text = "$label: " + if (on) "روشن ✅" else "خاموش ⭕"
                textSize = 11f
                setTextColor(if (on) cGreen else cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                background = rounded(Color.parseColor("#111A33"), 12f, Color.parseColor("#25335C"), 1f)
                setOnClickListener {
                    val v = !(prefs.getBoolean(key, def))
                    prefs.edit().putBoolean(key, v).apply()
                    recreate()
                }
            }
            val wrap = LinearLayout(this)
            wrap.addView(b, LinearLayout.LayoutParams(-2, -2))
            return wrap
        }
        togRow.addView(tog("🔔 صدا", "sound", true), LinearLayout.LayoutParams(0, -2, 1f))
        togRow.addView(tog("📳 لرزش", "vibrate", true), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(togRow, LinearLayout.LayoutParams(-1, -2))

        val dev = TextView(this).apply {
            text = "🛡 امنیت: سرور داخل برنامه پنهان است"
            textSize = 10.5f
            setTextColor(cMuted)
            setPadding(dp(4f), dp(12f), dp(4f), 0)
        }
        card.addView(dev, LinearLayout.LayoutParams(-1, -2))

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12f), 0, 0) }
        val invite = button("📣 دعوت از دوستان", a1, a2, 12.5f)
        invite.setOnClickListener {
            runCatching {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, "بیا تو تماس عمومی esy call! برنامه رو نصب کن و «شروع تماس» رو بزن 🚀")
                startActivity(Intent.createChooser(i, "اشتراک‌گذاری"))
            }
        }
        btnRow.addView(invite, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8f) })
        val logout = button("🚪 خروج از حساب", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12.5f)
        logout.setOnClickListener {
            prefs.edit().remove("name").remove("username").remove("bio").remove("emoji").remove("my_photo_b64").apply()
            render()
            toast("از حساب محلی خارج شدی")
        }
        btnRow.addView(logout, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(btnRow, LinearLayout.LayoutParams(-1, -2))

        main.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22f) })
    }

    /* ------------------------- CHAT PAGE (Telegram style) ------------------------- */
    private fun buildChatPage(root: FrameLayout) {
        chatPage = FrameLayout(this)
        chatPage.setBackgroundColor(Color.parseColor("#F205070F"))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF0C1226.toInt(), 0xFF12204A.toInt()))
        }
        val back = CircleIconButton(this, "back") { closeChat() }
        back.accent = a1
        header.addView(back, LinearLayout.LayoutParams(dp(52f), dp(52f)))

        chatHeaderAvatar = ImageView(this)
        header.addView(chatHeaderAvatar, LinearLayout.LayoutParams(dp(42f), dp(42f)).apply { marginStart = dp(8f) })

        val tt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val t1 = TextView(this).apply {
            text = "تماس عمومی"
            textSize = 15f
            setTextColor(cText)
            typeface = Typeface.DEFAULT_BOLD
        }
        chatSubTv = TextView(this).apply {
            text = "۰ نفر آنلاین"
            textSize = 11f
            setTextColor(cGreen)
        }
        tt.addView(t1, LinearLayout.LayoutParams(-2, -2))
        tt.addView(chatSubTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
        header.addView(tt, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10f) })

        chatMicBtn = CircleIconButton(this, "mic") {
            doToggleMute()
            refreshChatMic()
        }
        chatMicBtn.accent = a1
        header.addView(chatMicBtn, LinearLayout.LayoutParams(dp(52f), dp(52f)))
        col.addView(header, LinearLayout.LayoutParams(-1, -2))

        val hair = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a1, 0x0022D3EE))
        }
        col.addView(hair, LinearLayout.LayoutParams(-1, dp(2f)))

        val dateWrap = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(10f), 0, 0) }
        dateWrap.addView(smallPill("امروز"))
        col.addView(dateWrap, LinearLayout.LayoutParams(-1, -2))

        chatScroll = ScrollView(this)
        chatList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }
        chatScroll.addView(chatList, LinearLayout.LayoutParams(-1, -2))
        col.addView(chatScroll, LinearLayout.LayoutParams(0, 0, 1f))

        val er = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(8f), dp(6f), dp(8f), dp(2f))
        }
        val erow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (e in listOf("😀", "🔥", "👍", "❤️", "😂", "🎉", "⚡", "💪", "🤝", "😎")) {
            val ev = TextView(this).apply {
                text = e
                textSize = 22f
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                setOnClickListener { sendReaction(e) }
            }
            erow.addView(ev)
        }
        er.addView(erow)
        col.addView(er, LinearLayout.LayoutParams(-1, -2))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(16f))
        }
        chatInput = field("پیام بنویس...", true)
        chatInput.setOnEditorActionListener { _, _, _ -> sendChat(); true }
        inputRow.addView(chatInput, LinearLayout.LayoutParams(0, -2, 1f))
        val sendBtn = CircleIconButton(this, "send") { sendChat() }
        sendBtn.accent = a1
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(54f), dp(54f)).apply { marginStart = dp(10f) })
        col.addView(inputRow, LinearLayout.LayoutParams(-1, -2))

        chatPage.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.addView(chatPage, FrameLayout.LayoutParams(-1, -1))
        chatPage.visibility = View.GONE
    }

    private fun openChat() {
        chatOpen = true
        unread = 0
        chatPage.visibility = View.VISIBLE
        chatPage.alpha = 0f
        chatPage.translationY = dp(46f).toFloat()
        chatPage.animate().alpha(1f).translationY(0f).setDuration(240).start()
        refreshChatMic()
        rebuildChat()
        render()
    }

    private fun closeChat() {
        chatOpen = false
        chatPage.animate().alpha(0f).translationY(dp(46f).toFloat()).setDuration(200).withEndAction {
            chatPage.visibility = View.GONE
        }.start()
        render()
    }

    private fun refreshChatMic() {
        if (::chatMicBtn.isInitialized) {
            chatMicBtn.kind = if (CallManager.muted) "micOff" else "mic"
            chatMicBtn.accent = if (CallManager.muted) cRed else a1
            chatMicBtn.invalidate()
        }
    }

    private fun rebuildChat() {
        if (!::chatList.isInitialized) return
        chatList.removeAllViews()
        if (chatMessages.isEmpty()) {
            val empty = TextView(this).apply {
                text = "💬 هنوز پیامی نیست\nاولین پیام رو بنویس!"
                textSize = 12.5f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
                setPadding(0, dp(60f), 0, 0)
            }
            chatList.addView(empty, LinearLayout.LayoutParams(-1, -2))
            return
        }
        for (m in chatMessages) addBubble(m)
    }

    private fun addBubble(m: Msg) {
        runCatching {
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (m.mine) Gravity.END else Gravity.START
            }

            val bmp: Bitmap?
            val emo: String
            if (m.mine) {
                bmp = myPhoto()
                emo = prefs.getString("emoji", "") ?: ""
            } else {
                bmp = peerAvatars[m.from]
                var e = ""
                for (p in CallManager.people) if (p.name == m.from) e = parseEmoji(p.bio).first
                emo = e.ifBlank { "👤" }
            }

            val av = ImageView(this)
            av.background = AvatarDrawable(bmp, emo, a1, a2)
            av.setOnClickListener { openProfile() }

            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            val nameTv = TextView(this).apply {
                text = m.from + if (m.mine) " (شما)" else ""
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (m.mine) Color.parseColor("#BFE3FF") else a1)
                setPadding(dp(2f), 0, dp(2f), dp(3f))
            }

            val bubble = TextView(this).apply {
                text = m.text
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setPadding(dp(14f), dp(10f), dp(14f), dp(8f))
                background = if (m.mine) {
                    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF1B4B8F.toInt(), 0xFF2E7DD1.toInt())).apply {
                        cornerRadii = floatArrayOf(dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(5f).toFloat(), dp(18f).toFloat())
                    }
                } else {
                    rounded(0xFF141C33.toInt(), 18f, 0xFF26324F.toInt(), 1.2f).apply {
                        cornerRadii = floatArrayOf(dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(5f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat())
                    }
                }
            }

            val meta = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4f), dp(2f), dp(4f), 0)
            }
            val timeTv = TextView(this).apply {
                text = m.time
                textSize = 9.5f
                setTextColor(Color.parseColor("#7E93B8"))
            }
            meta.addView(timeTv, LinearLayout.LayoutParams(-2, -2))
            if (m.mine) {
                val st = TextView(this).apply {
                    textSize = 10f
                    text = when (m.status) {
                        1 -> " ✓"
                        2 -> " ✗"
                        else -> " ⋯"
                    }
                    setTextColor(if (m.status == 2) cRed else Color.parseColor("#7E93B8"))
                    setPadding(dp(4f), 0, 0, 0)
                    if (m.status == 2) setOnClickListener {
                        m.status = 0
                        rebuildChat()
                        scope.launch {
                            val ok = publishBytes(JSONObject().put("t", "chat").put("n", m.from).put("m", m.text).toString().toByteArray(Charsets.UTF_8))
                            m.status = if (ok) 1 else 2
                            if (chatOpen) rebuildChat()
                        }
                    }
                }
                meta.addView(st, LinearLayout.LayoutParams(-2, -2))
            }

            inner.addView(nameTv)
            inner.addView(bubble)
            inner.addView(meta)

            if (m.mine) {
                wrap.addView(inner, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
                wrap.addView(av, LinearLayout.LayoutParams(dp(34f), dp(34f)))
            } else {
                wrap.addView(av, LinearLayout.LayoutParams(dp(34f), dp(34f)))
                wrap.addView(inner, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
            }

            chatList.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun sendChat() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return
        chatInput.setText("")
        val u = myName()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val msg = Msg(u, text, true, 0, time)
        chatMessages.add(msg)
        if (chatOpen) {
            addBubble(msg)
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
        scope.launch {
            val ok = publishBytes(JSONObject().put("t", "chat").put("n", u).put("m", text).toString().toByteArray(Charsets.UTF_8))
            msg.status = if (ok) 1 else 2
            if (!ok) toast("ارسال نشد — دوباره تلاش کن (روی ✗ بزن)")
            if (chatOpen) rebuildChat()
        }
    }

    private fun sendReaction(e: String) {
        spawnFloat(e)
        tone()
        vibrate()
        scope.launch {
            runCatching { publishBytes(JSONObject().put("t", "react").put("e", e).toString().toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun quickReact() {
        val e = listOf("🔥", "👍", "😂", "🎉", "⚡", "❤️").random()
        sendReaction(e)
    }

    private fun spawnFloat(e: String) {
        runCatching {
            val tv = TextView(this)
            tv.text = e
            tv.textSize = 30f
            val x = dp((20 + Math.random() * 280).toFloat())
            val lp = FrameLayout.LayoutParams(-2, -2)
            lp.leftMargin = x
            lp.topMargin = resources.displayMetrics.heightPixels - dp(320f)
            rootRef.addView(tv, lp)
            tv.animate()
                .translationY(-dp(240f).toFloat())
                .alpha(0f)
                .setDuration(1500)
                .setInterpolator(LinearInterpolator())
                .withEndAction { runCatching { rootRef.removeView(tv) } }
                .start()
        }
    }

    private fun tone() {
        if (!prefs.getBoolean("sound", true)) return
        runCatching {
            val t = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            t.startTone(ToneGenerator.TONE_PROP_BEEP, 110)
        }
    }

    private fun vibrate() {
        if (!prefs.getBoolean("vibrate", true)) return
        runCatching {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(28, 120))
            else @Suppress("DEPRECATION") v.vibrate(28)
        }
    }

    private fun toast(s: String) {
        runCatching {
            val tv = TextView(this)
            tv.text = s
            tv.setTextColor(Color.WHITE)
            tv.textSize = 12f
            tv.setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            tv.background = rounded(0xE612204A.toInt(), 16f, a1, 1.2f)
            val lp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            lp.bottomMargin = dp(120f)
            rootRef.addView(tv, lp)
            tv.animate().alpha(1f).setDuration(150).start()
            tv.postDelayed({
                tv.animate().alpha(0f).setDuration(400).withEndAction { runCatching { rootRef.removeView(tv) } }.start()
            }, 2200)
        }
    }

    /* ------------------------- PROFILE ------------------------- */
    private fun buildProfileOverlay(root: FrameLayout) {
        profileOverlay = FrameLayout(this)
        profileOverlay.setBackgroundColor(0xE605070F.toInt())
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(22f), dp(22f), dp(22f))
            background = rounded(0xFF0C1226.toInt(), 22f, 0xFF2B3B66.toInt(), 1.4f)
        }

        val title = TextView(this).apply {
            text = "حساب من"
            textSize = 17f
            setTextColor(cText)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        box.addView(title, LinearLayout.LayoutParams(-1, -2))

        val avWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        profileAvatar = ImageView(this)
        avWrap.addView(profileAvatar, LinearLayout.LayoutParams(dp(104f), dp(104f)).apply { topMargin = dp(16f) })

        val photoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val pickBtn = button("📷 انتخاب عکس", a1, a2, 12f)
        pickBtn.setOnClickListener { pickPhoto() }
        photoRow.addView(pickBtn, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
        val rmBtn = button("✂️ حذف عکس", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12f)
        rmBtn.setOnClickListener {
            prefs.edit().remove("my_photo_b64").apply()
            renderProfileAvatar()
            toast("عکس حذف شد")
        }
        photoRow.addView(rmBtn, LinearLayout.LayoutParams(-2, -2))
        avWrap.addView(photoRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        box.addView(avWrap, LinearLayout.LayoutParams(-1, -2))

        val emojiLabel = TextView(this).apply {
            text = "یا یک آواتار انتخاب کن:"
            textSize = 11.5f
            setTextColor(cMuted)
            setPadding(dp(2f), dp(16f), dp(2f), dp(6f))
        }
        box.addView(emojiLabel, LinearLayout.LayoutParams(-1, -2))
        buildProfilePicker(box)

        val l1 = TextView(this).apply { text = "نام"; textSize = 11.5f; setTextColor(cMuted); setPadding(dp(2f), dp(14f), dp(2f), dp(5f)) }
        box.addView(l1, LinearLayout.LayoutParams(-1, -2))
        nameField = field("مثال: علی")
        nameField.setText(prefs.getString("name", "") ?: "")
        box.addView(nameField, LinearLayout.LayoutParams(-1, -2))

        val l2 = TextView(this).apply { text = "نام کاربری (اختیاری)"; textSize = 11.5f; setTextColor(cMuted); setPadding(dp(2f), dp(12f), dp(2f), dp(5f)) }
        box.addView(l2, LinearLayout.LayoutParams(-1, -2))
        userField = field("مثال: ali123")
        userField.setText(prefs.getString("username", "") ?: "")
        box.addView(userField, LinearLayout.LayoutParams(-1, -2))

        val l3 = TextView(this).apply { text = "بیو (اختیاری)"; textSize = 11.5f; setTextColor(cMuted); setPadding(dp(2f), dp(12f), dp(2f), dp(5f)) }
        box.addView(l3, LinearLayout.LayoutParams(-1, -2))
        bioField = field("یه جمله باحال درباره خودت...", false)
        bioField.setText(prefs.getString("bio", "") ?: "")
        box.addView(bioField, LinearLayout.LayoutParams(-1, -2))

        val save = button("ذخیره", a1, a2, 14f)
        save.setOnClickListener {
            prefs.edit()
                .putString("name", nameField.text.toString().trim())
                .putString("username", userField.text.toString().trim())
                .putString("bio", bioField.text.toString().trim())
                .putString("emoji", selectedEmoji)
                .apply()
            closeProfile()
            render()
            toast("پروفایل ذخیره شد ✅")
            sendMyAvatar(force = true)
        }
        box.addView(save, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })

        val close = TextView(this).apply {
            text = "بستن ✕"
            textSize = 12f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(14f), 0, 0)
            setOnClickListener { closeProfile() }
        }
        box.addView(close, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        scroll.addView(box, LinearLayout.LayoutParams(-1, -2))
        val pad = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18f), dp(40f), dp(18f), dp(40f))
        }
        pad.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        profileOverlay.addView(pad, FrameLayout.LayoutParams(-1, -1))
        profileOverlay.visibility = View.GONE
        profileOverlay.setOnClickListener { closeProfile() }
        pad.setOnClickListener { }
        root.addView(profileOverlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildProfilePicker(row: LinearLayout) {
        val hsv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (e in emojiChoices) {
            val tv = TextView(this)
            tv.text = e
            tv.textSize = 24f
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(9f), dp(7f), dp(9f), dp(7f))
            tv.background = if (e == selectedEmoji) rounded(0x3322D3EE, 14f, a1, 1.4f) else rounded(0x11FFFFFF, 14f)
            tv.setOnClickListener {
                selectedEmoji = e
                buildProfilePickerRefresh(row)
                renderProfileAvatar()
            }
            r.addView(tv)
        }
        hsv.addView(r)
        row.addView(hsv, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildProfilePickerRefresh(row: LinearLayout) {
        val idx = row.indexOfChild(row.getChildAt(row.childCount - 1))
        runCatching {
            val hsv = row.getChildAt(row.childCount - 1) as? HorizontalScrollView ?: return
            val r = hsv.getChildAt(0) as? LinearLayout ?: return
            for (i in 0 until r.childCount) {
                val tv = r.getChildAt(i) as? TextView ?: continue
                val e = emojiChoices[i]
                tv.background = if (e == selectedEmoji) rounded(0x3322D3EE, 14f, a1, 1.4f) else rounded(0x11FFFFFF, 14f)
            }
        }
    }

    private fun renderProfileAvatar() {
        if (::profileAvatar.isInitialized) {
            profileAvatar.background = AvatarDrawable(myPhoto(), selectedEmoji, a1, a2)
        }
    }

    private fun openProfile() {
        profileOpen = true
        profileOverlay.visibility = View.VISIBLE
        renderProfileAvatar()
    }

    private fun closeProfile() {
        profileOpen = false
        profileOverlay.visibility = View.GONE
    }

    /* ------------------------- PHOTO ------------------------- */
    private fun myPhoto(): Bitmap? {
        val b = prefs.getString("my_photo_b64", "") ?: ""
        return if (b.isEmpty()) null else b64ToBitmap(b)
    }

    private fun b64ToBitmap(b: String): Bitmap? = runCatching {
        val bytes = Base64.decode(b, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun bitmapToB64(b: Bitmap, q: Int): String = runCatching {
        val os = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, q, os)
        Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")

    private fun pickPhoto() {
        runCatching {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            i.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(Intent.createChooser(i, "انتخاب عکس"), REQ_PHOTO)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            scope.launch(Dispatchers.IO) {
                try {
                    val src = decodeSampled(uri, 512) ?: return@launch
                    val big = circleCrop(src, 256)
                    val b64 = bitmapToB64(big, 82)
                    val small = circleCrop(src, 64)
                    val sb64 = bitmapToB64(small, 72)
                    runOnUiThread {
                        if (!alive) return@runOnUiThread
                        prefs.edit().putString("my_photo_b64", b64).apply()
                        render()
                        renderProfileAvatar()
                        toast("عکس پروفایل تنظیم شد ✅")
                    }
                    if (sb64.isNotEmpty()) {
                        val o = JSONObject().put("t", "av").put("n", myName()).put("a", sb64)
                        publishBytes(o.toString().toByteArray(Charsets.UTF_8))
                    }
                } catch (t: Throwable) {
                    runOnUiThread { if (alive) toast("انتخاب عکس ناموفق بود") }
                }
            }
        }
    }

    private fun decodeSampled(uri: Uri, max: Int): Bitmap? {
        val cr = contentResolver
        val o1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o1) } }
        var sample = 1
        while (max(o1.outWidth, o1.outHeight) / sample > max) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o2) } }.getOrNull()
    }

    private fun circleCrop(src: Bitmap, size: Int): Bitmap {
        val mn = min(src.width, src.height)
        val x = (src.width - mn) / 2
        val y = (src.height - mn) / 2
        val sq = Bitmap.createBitmap(src, x, y, mn, mn)
        val s = Bitmap.createScaledBitmap(sq, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(s, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        return out
    }

    private fun sendMyAvatar(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAvSent < 15000) return
        lastAvSent = now
        val b = prefs.getString("my_photo_b64", "") ?: ""
        if (b.isEmpty()) return
        val smallBmp = b64ToBitmap(b)
        val sb64 = if (smallBmp != null) bitmapToB64(Bitmap.createScaledBitmap(smallBmp, 64, 64, true), 72) else ""
        if (sb64.isEmpty()) return
        scope.launch {
            runCatching {
                val o = JSONObject().put("t", "av").put("n", myName()).put("a", sb64)
                publishBytes(o.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    /* ------------------------- TRANSPORT (root-cause fixed) ------------------------- */
    private fun isRoomLike(o: Any): Boolean = runCatching {
        o.javaClass.methods.any { it.name == "getLocalParticipant" && it.parameterCount == 0 }
    }.getOrDefault(false)

    private fun findRoom(): Any? {
        cachedRoom?.let { return it }
        // 1) field whose TYPE is Room
        runCatching {
            for (f in CallManager::class.java.declaredFields) {
                runCatching {
                    if (Room::class.java.isAssignableFrom(f.type)) {
                        f.isAccessible = true
                        val v = f.get(CallManager)
                        if (v != null) {
                            cachedRoom = v
                            return v
                        }
                    }
                }
            }
        }
        // 2) common field names
        for (nm in listOf("room", "livekitRoom", "currentRoom", "lkRoom", "theRoom", "_room", "connectedRoom")) {
            runCatching {
                val f = CallManager::class.java.getDeclaredField(nm)
                f.isAccessible = true
                val v = f.get(CallManager)
                if (v != null && isRoomLike(v)) {
                    cachedRoom = v
                    return v
                }
            }
        }
        // 3) deep scan of fields + getters
        val seen = java.util.IdentityHashMap<Any, Boolean>()
        fun scan(obj: Any?, depth: Int): Any? {
            if (obj == null || depth > 2) return null
            if (seen.put(obj, true) != null) return null
            if (isRoomLike(obj)) return obj
            runCatching {
                for (f in obj.javaClass.declaredFields) {
                    val t = f.type
                    if (t == String::class.java || t.isPrimitive || t.isEnum) continue
                    runCatching {
                        f.isAccessible = true
                        val v = f.get(obj) ?: return@runCatching
                        if (v is CharSequence || v is Number || v is Boolean) return@runCatching
                        val r = scan(v, depth + 1)
                        if (r != null) return r
                    }
                }
                for (m in obj.javaClass.methods) {
                    if (m.parameterCount != 0 || !m.name.startsWith("get")) continue
                    runCatching {
                        val v = m.invoke(obj) ?: return@runCatching
                        if (v is CharSequence || v is Number || v is Boolean) return@runCatching
                        val r = scan(v, depth + 1)
                        if (r != null) return r
                    }
                }
            }
            return null
        }
        val r = scan(CallManager, 0)
        if (r != null) cachedRoom = r
        return r
    }

    private fun getLp(room: Any): Any? = runCatching {
        room.javaClass.methods.firstOrNull { it.name == "getLocalParticipant" && it.parameterCount == 0 }?.invoke(room)
    }.getOrNull()

    /** Root-cause fixed: recipients List must be NULL (= broadcast), empty list = send to NOBODY. */
    private suspend fun publishBytes(data: ByteArray, topic: String = "chat"): Boolean {
        val room = findRoom() ?: run { lastSendErr = "اتصال پیدا نشد"; return false }
        val lp = getLp(room) ?: run { lastSendErr = "participant پیدا نشد"; return false }

        // Strategy A: exact suspend publishData (5 args: bytes, reliability, topic, destinations, cont)
        val exact = lp.javaClass.methods.firstOrNull {
            it.name.startsWith("publishData") && !it.name.contains("$") && it.parameterCount == 5
        }
        if (exact != null) {
            val ok = suspendCoroutine<Boolean> { cont ->
                try {
                    val ps = exact.parameterTypes
                    val rel = ps[1].enumConstants?.firstOrNull { (it as Enum<*>).name == "RELIABLE" }
                    val r = exact.invoke(lp, data, rel, topic, null, cont)
                    if (r !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) cont.resume(true)
                } catch (t: Throwable) {
                    lastSendErr = "publishData: " + (t.cause?.message ?: t.message ?: "?")
                    runCatching { cont.resume(false) }
                }
            }
            if (ok) return true
        }

        // Strategy B: any other publishData overload
        for (m in lp.javaClass.methods) {
            if (!m.name.startsWith("publishData") || m.name.contains("$")) continue
            if (m === exact) continue
            try {
                val ps = m.parameterTypes
                if (ps.none { it == ByteArray::class.java }) continue
                val args = arrayOfNulls<Any?>(ps.size)
                var isSuspend = false
                for (i in ps.indices) {
                    val pt = ps[i]
                    when {
                        pt == ByteArray::class.java -> args[i] = data
                        pt.isEnum -> args[i] = pt.enumConstants?.firstOrNull { (it as Enum<*>).name == "RELIABLE" } ?: pt.enumConstants?.firstOrNull()
                        pt == String::class.java -> args[i] = topic
                        List::class.java.isAssignableFrom(pt) -> args[i] = null
                        pt.name == "kotlin.coroutines.Continuation" -> { isSuspend = true; args[i] = null }
                        pt == java.lang.Boolean.TYPE -> args[i] = false
                        else -> args[i] = null
                    }
                }
                if (isSuspend) {
                    val ok = suspendCoroutine<Boolean> { cont ->
                        args[args.size - 1] = cont
                        try {
                            val r = m.invoke(lp, *args)
                            if (r !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) cont.resume(true)
                        } catch (t: Throwable) {
                            lastSendErr = "pb: " + (t.cause?.message ?: t.message ?: "?")
                            runCatching { cont.resume(false) }
                        }
                    }
                    if (ok) return true
                } else {
                    m.invoke(lp, *args)
                    return true
                }
            } catch (t: Throwable) {
                lastSendErr = "pb2: " + (t.cause?.message ?: t.message ?: "?")
            }
        }

        // Strategy C: CallManager helper methods (send/publish with ByteArray)
        for (m in CallManager::class.java.methods) {
            val nm = m.name.lowercase()
            if (!(nm.contains("send") || nm.contains("publish") || nm.contains("broadcast") || nm.contains("data"))) continue
            try {
                val ps = m.parameterTypes
                if (ps.none { it == ByteArray::class.java }) continue
                val args = arrayOfNulls<Any?>(ps.size)
                var okMap = true
                for (i in ps.indices) when {
                    ps[i] == ByteArray::class.java -> args[i] = data
                    ps[i] == String::class.java -> args[i] = topic
                    else -> okMap = false
                }
                if (!okMap) continue
                m.invoke(CallManager, *args)
                return true
            } catch (t: Throwable) {
                lastSendErr = "cm: " + (t.cause?.message ?: t.message ?: "?")
            }
        }
        return false
    }

    private fun handlePacket(raw: String) {
        runCatching {
            val o = JSONObject(raw)
            when (o.optString("t")) {
                "chat" -> runOnUiThread {
                    if (!alive) return@runOnUiThread
                    val m = Msg(o.optString("n"), o.optString("m"), false, 1, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                    chatMessages.add(m)
                    if (chatOpen) {
                        addBubble(m)
                        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
                    } else {
                        unread++
                        tone()
                        vibrate()
                    }
                    render()
                }
                "react" -> runOnUiThread {
                    if (alive) spawnFloat(o.optString("e"))
                }
                "av" -> runOnUiThread {
                    if (!alive) return@runOnUiThread
                    val bmp = b64ToBitmap(o.optString("a"))
                    if (bmp != null) {
                        peerAvatars[o.optString("n")] = bmp
                        if (chatOpen) rebuildChat()
                    }
                }
            }
        }
    }

    private fun startEventListener() {
        if (eventsJob != null) return
        val room = findRoom() as? Room ?: return
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

    /* ------------------------- RENDER + TICKER ------------------------- */
    private fun render() {
        val st = CallManager.status
        val inCall = st == "connected" || st == "connecting" || st == "reconnecting"

        callBtn.visibility = if (inCall) View.GONE else View.VISIBLE
        micView.visibility = if (st == "connected") View.VISIBLE else View.GONE
        actionsRow.visibility = if (inCall) View.VISIBLE else View.GONE
        hintText.visibility = if (inCall) View.GONE else View.VISIBLE

        micView.muted = CallManager.muted
        refreshChatMic()

        val (dotC, txt, txtC) = when (st) {
            "connected" -> Triple(cGreen, "● در تماس — گفتگوی زنده", cGreen)
            "connecting" -> Triple(cAmber, "● در حال اتصال...", cAmber)
            "reconnecting" -> Triple(cAmber, "● در حال اتصال مجدد...", cAmber)
            else -> Triple(cRed, "● آماده برای تماس", cMuted)
        }
        (statusDot.background as? GradientDrawable)?.setColor(dotC)
        statusText.text = txt
        statusText.setTextColor(txtC)

        val err = CallManager.error
        if (err.isNullOrBlank()) {
            errorCard.visibility = View.GONE
        } else {
            errorCard.visibility = View.VISIBLE
            errorCard.text = "⚠ $err"
        }

        topName.text = myName()
        topUser.text = "@" + (prefs.getString("username", "") ?: "").ifBlank { "کاربر" }
        topAvatar.background = AvatarDrawable(myPhoto(), selectedEmoji, a1, a2)
        if (::chatHeaderAvatar.isInitialized) {
            chatHeaderAvatar.background = AvatarDrawable(myPhoto(), selectedEmoji, a1, a2)
        }
        renderProfileAvatar()

        renderPeople()

        if (chatMessages.isNotEmpty()) {
            val last = chatMessages[chatMessages.size - 1]
            val prefix = if (unread > 0) "💬 ($unread) " else "💬 "
            lastChatChip.text = prefix + last.from + ": " + last.text.take(42)
        } else {
            lastChatChip.text = "💬 آخرین پیام — برای باز کردن چت ضربه بزن"
        }

        if (::chatSubTv.isInitialized) {
            chatSubTv.text = fa(CallManager.people.size.toString()) + " نفر آنلاین"
        }
    }

    private fun renderPeople() {
        if (!::peopleBox.isInitialized) return
        peopleBox.removeAllViews()
        val list = CallManager.people
        if (list.isEmpty()) {
            val e = TextView(this).apply {
                text = "هنوز کسی نیست — دوستانت را دعوت کن!"
                textSize = 12f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
                setPadding(0, dp(14f), 0, dp(14f))
            }
            peopleBox.addView(e, LinearLayout.LayoutParams(-1, -2))
            return
        }
        for (p in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6f), dp(8f), dp(6f), dp(8f))
            }
            val av = ImageView(this)
            av.background = AvatarDrawable(peerAvatars[p.name], parseEmoji(p.bio).first.ifBlank { "👤" }, a1, a2)
            row.addView(av, LinearLayout.LayoutParams(dp(42f), dp(42f)))

            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val nm = TextView(this).apply {
                text = p.name + if (p.me) " (شما)" else ""
                textSize = 13f
                setTextColor(cText)
                typeface = Typeface.DEFAULT_BOLD
            }
            val bio = TextView(this).apply {
                text = parseEmoji(p.bio).second
                textSize = 10.5f
                setTextColor(cMuted)
                maxLines = 1
            }
            col.addView(nm, LinearLayout.LayoutParams(-2, -2))
            col.addView(bio, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10f) })

            val sp = TextView(this).apply {
                text = if (p.speaking) "🎙 صحبت می‌کند" else if (p.me && CallManager.muted) "🔇 بی‌صدا" else "● آنلاین"
                textSize = 10f
                setTextColor(if (p.speaking) cGreen else if (p.me && CallManager.muted) cRed else cMuted)
            }
            row.addView(sp, LinearLayout.LayoutParams(-2, -2))

            peopleBox.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        var t = 0f
        ticker = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t += 0.1f

                if (::callBtn.isInitialized && callBtn.visibility == View.VISIBLE) {
                    val pulse = 0.5f + 0.5f * sin(t * 2f * Math.PI.toFloat())
                    callBtn.alpha = 0.92f + 0.08f * pulse
                    callBtn.scaleX = 1f + 0.012f * pulse
                    callBtn.scaleY = 1f + 0.012f * pulse
                }

                val speaking = CallManager.people.any { it.speaking }
                val lvl = if (speaking) 0.5f + 0.5f * abs(sin(t * 5f)) else 0f
                if (::micView.isInitialized) {
                    micView.level = lvl
                    micView.tick(0.1f)
                }

                waveActive = lastStatus == "connected" && speaking
                waveBottom.active = waveActive || lastStatus == "connected"
                waveBottom.tick(0.1f)
                waveTop.active = waveActive
                waveTop.tick(0.1f)

                val st = CallManager.status
                if (st != lastStatus) {
                    lastStatus = st
                    if (st == "connected") {
                        if (callStart == 0L) callStart = System.currentTimeMillis()
                        sendMyAvatar()
                    } else if (st == "idle") {
                        callStart = 0L
                    }
                    render()
                }

                if (::timerText.isInitialized && callStart > 0L) {
                    timerText.text = "⏱ " + fmtDur((System.currentTimeMillis() - callStart) / 1000)
                }

                // self-heal: room might appear later
                if (eventsJob == null) startEventListener()

                // new joiners -> re-send avatar
                val pc = CallManager.people.size
                if (pc != lastPeopleCount) {
                    if (pc > lastPeopleCount && lastPeopleCount > 0) sendMyAvatar()
                    lastPeopleCount = pc
                    render()
                }

                quoteT += 0.1f
                if (quoteT > 14f) {
                    quoteT = 0f
                    quoteIdx = (quoteIdx + 1) % quotes.size
                    if (::hintText.isInitialized && hintText.visibility == View.VISIBLE) {
                        hintText.text = "هنوز کسی نیست — دوستانت را دعوت کن!\n" + quotes[quoteIdx]
                    }
                }
            }
            start()
        }
    }

    /* ------------------------- CALL ACTIONS ------------------------- */
    private fun onCallClick() {
        val u = (prefs.getString("username", "") ?: "").trim().ifBlank { "کاربر" + (Math.random() * 900 + 100).toInt() }
        val n = (prefs.getString("name", "") ?: "").trim().ifBlank { u }
        val emo = prefs.getString("emoji", "😎") ?: "😎"
        val b = (prefs.getString("bio", "") ?: "").trim()
        callStart = System.currentTimeMillis()
        runCatching { CallManager.connect(this, SERVER, u, n, "$emo|$b", deviceId()) }
        render()
    }

    private fun doToggleMute() {
        runCatching {
            val m = CallManager::class.java.methods.firstOrNull {
                it.name == "toggleMute" || it.name == "toggleMic" || it.name == "toggleMicrophone"
            }
            if (m != null) {
                m.invoke(CallManager)
                render()
                return
            }
            val s = CallManager::class.java.methods.firstOrNull {
                it.name == "setMuted" && it.parameterCount == 1
            }
            if (s != null) {
                s.invoke(CallManager, !CallManager.muted)
                render()
                return
            }
            CallManager.muted = !CallManager.muted
        }
        vibrate()
        render()
    }

    private fun doHangup() {
        runCatching {
            for (nm in listOf("hangup", "hangUp", "disconnect", "leave", "endCall", "stopCall", "cleanup", "stop")) {
                val m = CallManager::class.java.methods.firstOrNull { it.name == nm && it.parameterCount == 0 }
                if (m != null) {
                    m.invoke(CallManager)
                    callStart = 0L
                    return
                }
            }
        }
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
        }
        speakerBtn.kind = if (speakerOn) "speaker" else "speakerOff"
        speakerBtn.invalidate()
        toast(if (speakerOn) "🔊 بلندگو روشن" else "🔈 بلندگو خاموش")
    }

    private fun deviceId(): String = runCatching {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }.getOrDefault("unknown")

    @Suppress("DEPRECATION")
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
            super.onBackPressed()
        }
    }

    /* ------------------------- SPLASH ------------------------- */
    private fun showSplash() {
        runCatching {
            val splash = FrameLayout(this)
            splash.setBackgroundColor(Color.parseColor("#05070F"))
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            val logo = TextView(this)
            logo.text = "esy call"
            logo.textSize = 44f
            logo.typeface = Typeface.DEFAULT_BOLD
            logo.gravity = Gravity.CENTER
            gradientText(logo, intArrayOf(a1, a2))
            box.addView(logo, LinearLayout.LayoutParams(-2, -2))
            val sub = TextView(this)
            sub.text = "تماس عمومی — نسخه ۶"
            sub.textSize = 13f
            sub.setTextColor(cMuted)
            sub.gravity = Gravity.CENTER
            sub.setPadding(0, dp(10f), 0, 0)
            box.addView(sub, LinearLayout.LayoutParams(-2, -2))
            splash.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            val deco = WaveView(this)
            deco.accent = a1
            deco.accent2 = a2
            splash.addView(deco, FrameLayout.LayoutParams(-1, dp(220f), Gravity.BOTTOM))
            rootRef.addView(splash, FrameLayout.LayoutParams(-1, -1))
            deco.tick(0.1f)
            splash.animate().alpha(0f).setStartDelay(950).setDuration(550).withEndAction {
                runCatching { rootRef.removeView(splash) }
            }.start()
        }
    }
}
