package com.esrafil.voicecall

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import io.livekit.android.room.participant.LocalParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
    val time: String,
    val reactions: MutableList<String> = mutableListOf(),
    var quote: String = ""
)

/* ============================ ICONS (vector paths) ============================ */
private object Icons {
    fun draw(c: Canvas, kind: String, color: Int, size: Float, cx: Float, cy: Float) {
        val gc = (color and 0x00FFFFFF) or (0x88 shl 24)
        render(c, kind, gc, size * 1.16f, cx, cy, 14f)
        render(c, kind, color, size, cx, cy, 8.5f)
    }

    private fun render(c: Canvas, kind: String, color: Int, size: Float, cx: Float, cy: Float, strokeW: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeW
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
            "people" -> {
                c.drawCircle(36f, 34f, 13f, p)
                path.reset()
                path.arcTo(RectF(12f, 52f, 60f, 98f), 180f, 180f, true)
                c.drawPath(path, p)
                c.drawCircle(70f, 30f, 11f, p)
                path.reset()
                path.arcTo(RectF(52f, 46f, 96f, 90f), 180f, 180f, true)
                c.drawPath(path, p)
            }
            "bolt" -> {
                path.reset()
                path.moveTo(56f, 8f)
                path.lineTo(26f, 56f)
                path.lineTo(46f, 56f)
                path.lineTo(40f, 92f)
                path.lineTo(76f, 42f)
                path.lineTo(54f, 42f)
                path.close()
                c.drawPath(path, pf)
            }
            "user" -> {
                c.drawCircle(50f, 33f, 15f, p)
                path.reset()
                path.arcTo(RectF(20f, 56f, 80f, 106f), 180f, 180f, true)
                c.drawPath(path, p)
            }
            "gear" -> {
                c.drawCircle(50f, 50f, 15f, p)
                for (k in 0 until 8) {
                    val ang = k * Math.PI.toFloat() / 4f
                    val x1 = 50f + 24f * kotlin.math.cos(ang)
                    val y1 = 50f + 24f * kotlin.math.sin(ang)
                    val x2 = 50f + 36f * kotlin.math.cos(ang)
                    val y2 = 50f + 36f * kotlin.math.sin(ang)
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }
            "shield" -> {
                path.reset()
                path.moveTo(50f, 8f)
                path.lineTo(86f, 22f)
                path.lineTo(86f, 50f)
                path.cubicTo(86f, 74f, 68f, 88f, 50f, 94f)
                path.cubicTo(32f, 88f, 14f, 74f, 14f, 50f)
                path.lineTo(14f, 22f)
                path.close()
                c.drawPath(path, p)
            }
            "copy" -> {
                c.drawRoundRect(RectF(36f, 14f, 82f, 60f), 10f, 10f, p)
                c.drawRoundRect(RectF(18f, 38f, 64f, 86f), 10f, 10f, p)
            }
            "plus" -> {
                c.drawLine(50f, 20f, 50f, 80f, p)
                c.drawLine(20f, 50f, 80f, 50f, p)
            }
            "close" -> {
                c.drawLine(26f, 26f, 74f, 74f, p)
                c.drawLine(74f, 26f, 26f, 74f, p)
            }
            "star" -> {
                path.reset()
                path.moveTo(50f, 8f)
                path.lineTo(62f, 38f)
                path.lineTo(94f, 40f)
                path.lineTo(69f, 60f)
                path.lineTo(78f, 92f)
                path.lineTo(50f, 74f)
                path.lineTo(22f, 92f)
                path.lineTo(31f, 60f)
                path.lineTo(6f, 40f)
                path.lineTo(38f, 38f)
                path.close()
                c.drawPath(path, pf)
            }
            "info" -> {
                c.drawCircle(50f, 50f, 38f, p)
                c.drawLine(50f, 44f, 50f, 74f, p)
                c.drawCircle(50f, 28f, 5f, pf)
            }
            "trash" -> {
                c.drawLine(22f, 30f, 78f, 30f, p)
                path.reset()
                path.moveTo(32f, 38f)
                path.lineTo(68f, 38f)
                path.lineTo(62f, 88f)
                path.lineTo(38f, 88f)
                path.close()
                c.drawPath(path, p)
                path.reset()
                path.arcTo(RectF(40f, 12f, 60f, 32f), 180f, 180f, true)
                c.drawPath(path, p)
            }
            "key" -> {
                c.drawCircle(30f, 38f, 16f, p)
                c.drawLine(42f, 47f, 88f, 80f, p)
                c.drawLine(70f, 67f, 60f, 78f, p)
                c.drawLine(80f, 74f, 72f, 84f, p)
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

private val FONT_HERO: Typeface = Typeface.create("sans-serif-black", Typeface.BOLD)

private class GradientBorderDrawable(val bg: Int, val c1: Int, val c2: Int, val radiusPx: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.1f }
    override fun draw(c: Canvas) {
        val b = bounds
        val r = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        p.color = bg
        c.drawRoundRect(r, radiusPx, radiusPx, p)
        st.shader = LinearGradient(r.left, r.top, r.right, r.bottom, c1, c2, Shader.TileMode.CLAMP)
        val i = 1.05f
        c.drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), radiusPx, radiusPx, st)
        st.shader = null
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class HeroIconView(context: Context, var kind: String, var accent: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        if (r <= 2f) return
        p.color = (accent and 0x00FFFFFF) or (0x33 shl 24)
        c.drawCircle(cx, cy, r, p)
        p.shader = LinearGradient(0f, cy - r, 0f, cy + r, 0xFF1B2748.toInt(), 0xFF0C1322.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.86f, p)
        p.shader = null
        ring.shader = SweepGradient(cx, cy, intArrayOf(accent, 0xFF22D3EE.toInt(), accent), null)
        ring.strokeWidth = r * 0.085f
        c.drawCircle(cx, cy, r * 0.92f, ring)
        ring.shader = null
        Icons.draw(c, kind, 0xFFE4EDFF.toInt(), r * 0.82f, cx, cy)
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
        glow.color = (accent and 0x00FFFFFF) or (0x55 shl 24)
        c.drawCircle(cx, cy, r * 1.24f, glow)
        glow.color = (accent and 0x00FFFFFF) or (0xBB shl 24)
        c.drawCircle(cx, cy, r * 1.06f, glow)
        bg.shader = LinearGradient(0f, cy - r, 0f, cy + r, 0xFF1B2748.toInt(), 0xFF0C1322.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.94f, bg)
        bg.shader = null
        st.color = (accent and 0x00FFFFFF) or (0xCC shl 24)
        st.strokeWidth = 3.5f
        c.drawCircle(cx, cy, r * 0.94f, st)
        st.color = 0x55FFFFFF
        st.strokeWidth = 1.6f
        c.drawCircle(cx, cy, r * 0.80f, st)
        ic.color = if (isPressed) accent else Color.parseColor("#E4EDFF")
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

        fill.color = (cA and 0x00FFFFFF) or (0x2E shl 24)
        c.drawCircle(cx, cy, rCore * 1.30f, fill)

        for (k in 0 until 5) {
            val wave = (kotlin.math.sin(angle * 0.045 + k * 1.15).toFloat()) * 0.5f + 0.5f
            val scale = 1.06f + 0.21f * k + level * (0.24f + 0.26f * k) + wave * 0.055f
            val alpha = ((118 - k * 19) * (0.34f + level * 0.95f) + wave * 30f).toInt().coerceIn(6, 155)
            ripple.color = (cA and 0x00FFFFFF) or (alpha shl 24)
            ripple.strokeWidth = (3.4f - k * 0.5f).coerceAtLeast(1.1f)
            c.drawCircle(cx, cy, rCore * scale, ripple)
        }
        ripple.strokeWidth = 2.6f

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
            p.typeface = FONT_HERO
            val fm = p.fontMetrics
            c.drawText(emoji.ifBlank { "👤" }, cx, cy - (fm.ascent + fm.descent) / 2f, p)
        }
        ringP.shader = SweepGradient(cx, cy, intArrayOf(c1, c2, c1), null)
        ringP.strokeWidth = r * 0.17f
        c.drawCircle(cx, cy, r * 0.95f, ringP)
        ringP.shader = null
        ringP.color = 0x66FFFFFF
        ringP.strokeWidth = r * 0.05f
        c.drawCircle(cx, cy, r * 0.875f, ringP)
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
        Triple("گیمینگ", "#00FFA3", "#00C2FF"),
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

    private lateinit var roomsPage: FrameLayout
    private lateinit var roomChip: LinearLayout
    private lateinit var roomChipTv: TextView
    private lateinit var roomsCurrentTv: TextView
    private lateinit var myCodeTv: TextView
    private lateinit var codeField: EditText
    private lateinit var roomUserField: EditText
    private lateinit var roomPassField: EditText
    private lateinit var joinUserField: EditText
    private lateinit var joinPassField: EditText
    private lateinit var replyBar: LinearLayout
    private lateinit var replyQuoteTv: TextView
    private var pendingReply: Msg? = null
    private var roomCode: String? = null
    private var myRoomCode: String? = null
    private var myRoomUser: String = ""
    private var myRoomPass: String = ""
    private var myPassHash: String = ""
    private var pendingAvatarB64: String = ""
    private var iAmOwner: Boolean = false
    private var joinChecked: Boolean = false
    private var waitingOverlay: View? = null

    private val chatMessages = ArrayList<Msg>()
    private val roomChatStore = HashMap<String, ArrayList<Msg>>()
    private var chatRoomKey = "public"
    private val myLog = ArrayList<JSONObject>()
    private val seenMsgs = HashSet<String>()
    private var savedU = ""
    private var savedB = ""
    private val peerAvatars = HashMap<String, Bitmap>()
    private val peerInfo = HashMap<String, Triple<String, String, String>>()
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
    private var roomFindAt = 0L
    private var lastRenderAt = 0L
    private var renderPending = false
    private var lastPeopleCount = 0
    private var quoteIdx = 0
    private var quoteT = 0f
    private var pendingConnect = false

    private lateinit var pageFrame: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private var currentTab = "home"
    private val tabPages = HashMap<String, View>()
    private val navItems = HashMap<String, LinearLayout>()
    private val navIcons = HashMap<String, IconView>()
    private val navLabels = HashMap<String, TextView>()
    private val navDots = HashMap<String, View>()
    private lateinit var msgCountText: TextView
    private lateinit var reactList: LinearLayout
    private lateinit var settingsPage: FrameLayout
    private lateinit var aboutPage: FrameLayout

    private val quotes = arrayOf(
        "🎤 صدات داره می‌رسه!",
        "🔥 گفتگوی زنده — خوش بگذره",
        "🌐 همه با هم در یک اتاق عمومی",
        "⚡ امواج صدا زنده‌اند",
        "⚡ نسخه ۱۳ — GAMING"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        themeIndex = prefs.getInt("theme", 0)
        selectedEmoji = prefs.getString("emoji", "😎") ?: "😎"
        myRoomCode = prefs.getString("my_room_code", null)
        myRoomUser = prefs.getString("my_room_user", "") ?: ""
        myRoomPass = prefs.getString("my_room_pass", "") ?: ""
        if (myRoomPass.isNotEmpty()) myPassHash = passHash(myRoomPass)
        if (myRoomCode != null) roomCode = myRoomCode

        rootRef = FrameLayout(this)
        buildUi(rootRef)
        setContentView(rootRef)
        showSplash()
        render()
        startTicker()

        CallManager.onChange = { runOnUiThread { if (alive) requestRender() } }
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

    private fun parseEmoji(bio: String?): Pair<String, String> {
        var b = bio ?: return "" to ""
        if (b.startsWith("R:")) {
            val bar = b.indexOf('|')
            b = if (bar >= 0) b.substring(bar + 1) else ""
        }
        if (b.startsWith("#")) {
            val bar = b.indexOf('|')
            b = if (bar >= 0) b.substring(bar + 1) else ""
        }
        val i = b.indexOf('|')
        return if (i >= 0) b.substring(0, i) to b.substring(i + 1) else "" to b
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
            setHintTextColor(Color.parseColor("#6E80A8"))
            setTextColor(cText)
            textSize = 14f
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            if (single) inputType = InputType.TYPE_CLASS_TEXT else inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = if (single) 1 else 4
            background = rounded(Color.parseColor("#0F1730"), 15f, Color.parseColor("#2C3D6B"), 1.5f)
        }

    private fun button(text: String, c1: Int, c2: Int, size: Float = 15f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            typeface = FONT_HERO
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            setPadding(dp(22f), dp(18f), dp(22f), dp(18f))
            setShadowLayer(28f, 0f, 7f, (c1 and 0x00FFFFFF) or (0xCC shl 24))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
                cornerRadius = dp(18f).toFloat()
                setStroke(dp(1.8f), 0x66FFFFFF)
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
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 3200
        anim.repeatCount = android.animation.ValueAnimator.INFINITE
        anim.addUpdateListener { va ->
            val tw = runCatching { tv.paint.measureText(tv.text.toString()) }.getOrDefault(tv.paint.textSize * 6f).coerceAtLeast(tv.paint.textSize * 2f)
            val x = -tw * 0.25f + (va.animatedValue as Float) * tw * 1.5f
            tv.paint.shader = LinearGradient(x - tw * 0.35f, 0f, x + tw * 0.35f, 0f, colors, null, Shader.TileMode.CLAMP)
            tv.invalidate()
        }
        tv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { anim.cancel() }
        })
        anim.start()
        if (colors.size > 1) tv.setShadowLayer(20f, 0f, 5f, (colors[1] and 0x00FFFFFF) or (0x99 shl 24))
        tv.invalidate()
    }

    /* ------------------------- UI build ------------------------- */
    private fun pressFx(v: View) {
        v.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(vw: View, ev: android.view.MotionEvent): Boolean {
                val a = ev.getAction()
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    vw.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                    vw.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                return false
            }
        })
    }

    private fun glassCard(radius: Float = 20f): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        background = GradientBorderDrawable(0xCC0C1226.toInt(), (a1 and 0x00FFFFFF) or (0x99 shl 24), (a2 and 0x00FFFFFF) or (0x77 shl 24), dp(radius).toFloat())
    }

    private fun pageHeader(title: String, withBack: Boolean = false, onBack: (() -> Unit)? = null, rightKind: String? = null, onRight: (() -> Unit)? = null): LinearLayout {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        if (withBack) {
            val b = CircleIconButton(this, "back") { onBack?.invoke() }
            b.accent = a1
            pressFx(b)
            head.addView(b, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        }
        val t = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(cText)
            typeface = FONT_HERO
            letterSpacing = 0.04f
            setPadding(dp(if (withBack) 10f else 4f), 0, 0, 0)
        }
        gradientText(t, intArrayOf(a1, a2))
        head.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        if (rightKind != null) {
            val b = CircleIconButton(this, rightKind) { onRight?.invoke() }
            b.accent = a1
            pressFx(b)
            head.addView(b, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(head)
        box.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(2.5f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x00000000, a1, a2, 0x00000000))
        })
        return box
    }

    /* ------------------------- UI BUILD v9 (pages + nav) ------------------------- */
    private fun buildUi(root: FrameLayout) {
        val outer = FrameLayout(this)

        waveBottom = WaveView(this)
        waveBottom.accent = a1
        waveBottom.accent2 = a2
        waveTop = WaveView(this)
        waveTop.accent = a2
        waveTop.accent2 = a1
        outer.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0B1226.toInt(), 0xFF05070F.toInt(), 0xFF05070F.toInt()))

        pageFrame = FrameLayout(this)
        outer.addView(pageFrame, FrameLayout.LayoutParams(-1, -1))
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))

        buildBottomNav(root)

        buildHomePage(pageFrame)
        buildPeoplePage(pageFrame)
        buildChatPage(pageFrame)
        buildProfilePage(pageFrame)

        buildRoomsPage(root)
        buildSettingsPage(root)
        buildAboutPage(root)

        switchTab(if (isRegistered()) "home" else "profile")
    }

    private fun buildBottomNav(root: FrameLayout) {
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
            background = rounded(0xF20A1122.toInt(), 26f, (a1 and 0x00FFFFFF) or (0x38 shl 24), 1.2f)
        }
        val items = listOf(
            Triple("home", "phone", "تماس"),
            Triple("people", "people", "افراد"),
            Triple("chat", "chat", "چت"),
            Triple("profile", "user", "من")
        )
        for ((key, ico, lbl) in items) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4f), dp(8f), dp(4f), dp(6f))
                setOnClickListener { switchTab(key) }
            }
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(2f).toFloat()
                    setColor(a1)
                }
                visibility = View.INVISIBLE
            }
            navDots[key] = dot
            item.addView(dot, LinearLayout.LayoutParams(dp(22f), dp(3f)))
            val icon = IconView(this, ico)
            navIcons[key] = icon
            item.addView(icon, LinearLayout.LayoutParams(dp(27f), dp(27f)).apply { topMargin = dp(3f) })
            val tv = TextView(this).apply {
                text = lbl
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(cMuted)
                typeface = FONT_HERO
            }
            navLabels[key] = tv
            item.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3f) })
            navItems[key] = item
            bottomNav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        lp.setMargins(dp(12f), 0, dp(12f), dp(12f))
        root.addView(bottomNav, lp)
    }

    private fun isRegistered(): Boolean {
        val n = (prefs.getString("name", "") ?: "").trim()
        val u = (prefs.getString("username", "") ?: "").trim()
        return n.isNotEmpty() && u.isNotEmpty()
    }

    private fun switchTab(tabIn: String) {
        val name = if (tabIn != "profile" && !isRegistered()) {
            toast("اول ثبت‌نام کن ✍️")
            "profile"
        } else tabIn
        currentTab = name
        for ((k, v) in tabPages) {
            if (k == name) {
                v.visibility = View.VISIBLE
                v.alpha = 0f
                v.translationY = dp(12f).toFloat()
                v.animate().alpha(1f).translationY(0f).setDuration(170).start()
            } else {
                v.visibility = View.GONE
            }
        }
        for ((k, dot) in navDots) dot.visibility = if (k == name) View.VISIBLE else View.INVISIBLE
        for ((k, item) in navItems) {
            item.background = if (k == name) rounded((a1 and 0x00FFFFFF) or (0x22 shl 24), 18f, (a1 and 0x00FFFFFF) or (0x55 shl 24), 1.2f) else null
        }
        for ((k, icon) in navIcons) {
            icon.color = if (k == name) a1 else Color.parseColor("#5E7199")
            icon.invalidate()
        }
        for ((k, tv) in navLabels) {
            tv.setTextColor(if (k == name) a1 else cMuted)
            tv.setTypeface(null, if (k == name) Typeface.BOLD else Typeface.NORMAL)
        }
        chatOpen = (name == "chat")
        if (name == "chat") {
            unread = 0
            refreshChatMic()
            rebuildChat()
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
        if (name == "profile") renderProfileAvatar()
        render()
    }

    /* ------------------------- HOME PAGE (call) ------------------------- */
    private fun buildHomePage(root: FrameLayout) {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(26f), dp(16f), dp(120f))
        }
        scroll.addView(main, LinearLayout.LayoutParams(-1, -2))

        // TOP ACCOUNT BAR
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF0C1226.toInt(), 0xFF12204A.toInt())).apply {
                cornerRadius = dp(20f).toFloat()
                setStroke(dp(1.2f), (a1 and 0x00FFFFFF) or (0x33 shl 24))
            }
            setOnClickListener { switchTab("profile") }
        }
        topAvatar = ImageView(this)
        topAvatar.setOnClickListener { switchTab("profile") }
        top.addView(topAvatar, LinearLayout.LayoutParams(dp(46f), dp(46f)))
        val nameCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        topName = TextView(this).apply {
            text = myName()
            textSize = 15f
            setTextColor(cText)
            typeface = FONT_HERO
            letterSpacing = 0.03f
        }
        gradientText(topName, intArrayOf(a1, Color.parseColor("#AEC4E8")))
        topUser = TextView(this).apply {
            text = "@" + (prefs.getString("username", "") ?: "")
            textSize = 11f
            setTextColor(a1)
        }
        nameCol.addView(topName, LinearLayout.LayoutParams(-2, -2))
        nameCol.addView(topUser, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
        top.addView(nameCol, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10f) })
        val verChip = smallPill("نسخه ۱۳ ⚡")
        verChip.setTextColor(a1)
        top.addView(verChip)
        main.addView(top, LinearLayout.LayoutParams(-1, -2))

        // STATUS HERO CARD
        val hero = glassCard(22f)
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8f) })
        val roomMini = smallPill("🏠 اتاق")
        roomMini.setTextColor(a1)
        roomMini.setOnClickListener { openRooms() }
        pressFx(roomMini)
        statusRow.addView(roomMini)
        hero.addView(statusRow, LinearLayout.LayoutParams(-1, -2))

        errorCard = TextView(this).apply {
            visibility = View.GONE
            textSize = 11.5f
            setTextColor(cRed)
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            background = rounded(0x1AFF6B4A, 14f, 0x55FF6B4A, 1f)
        }
        hero.addView(errorCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        roomChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13f), dp(9f), dp(13f), dp(9f))
            background = rounded(0x2200E5FF, 14f, a1, 1.2f)
            setOnClickListener { openRooms() }
        }
        val rDot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cGreen) }
        }
        roomChip.addView(rDot, LinearLayout.LayoutParams(dp(8f), dp(8f)))
        roomChipTv = TextView(this).apply {
            text = "اتاق عمومی — برای ساخت اتاق خصوصی ضربه بزن"
            textSize = 11f
            setTextColor(a1)
        }
        roomChip.addView(roomChipTv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7f) })
        hero.addView(roomChip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(11f) })
        main.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        // MIC CIRCLE
        val micWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20f), 0, 0)
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

        // ACTION BUTTONS + LABELS
        actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20f), 0, 0)
        }
        speakerBtn = CircleIconButton(this, "speaker") { toggleSpeaker() }
        chatBtn = CircleIconButton(this, "chat") { openChat() }
        endBtn = CircleIconButton(this, "hangup") { doHangup(); render() }
        val actDefs = listOf(
            Triple(speakerBtn, "speakerBtn", "اسپیکر"),
            Triple(chatBtn, "chatBtn", "چت"),
            Triple(endBtn, "endBtn", "قطع تماس")
        )
        for ((b, _, lbl) in actDefs) {
            b.accent = a1
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            cell.addView(b, LinearLayout.LayoutParams(dp(60f), dp(60f)))
            val lt = TextView(this).apply {
                text = lbl
                textSize = 9.5f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
            }
            cell.addView(lt, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5f) })
            actionsRow.addView(cell, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(9f); marginEnd = dp(9f) })
        }
        main.addView(actionsRow, LinearLayout.LayoutParams(-1, -2))        // CALL BUTTON
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
            typeface = FONT_HERO
            letterSpacing = 0.05f
        }
        callBtn.addView(callTv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10f) })
        pressFx(callBtn)
        val callWrap = FrameLayout(this).apply { setPadding(dp(5f), dp(5f), dp(5f), dp(5f)) }
        callWrap.addView(View(this).apply { background = rounded((a1 and 0x00FFFFFF) or (0x33 shl 24), 25f) }, FrameLayout.LayoutParams(-1, -1))
        callWrap.addView(callBtn, FrameLayout.LayoutParams(-1, -2))
        main.addView(callWrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })



        // STATS BENTO
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18f), 0, 0)
        }
        fun statTile(label: String, tv: TextView): LinearLayout {
            val tile = glassCard(16f)
            tile.gravity = Gravity.CENTER
            tile.setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            val l = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
            }
            tile.addView(l, LinearLayout.LayoutParams(-1, -2))
            tv.gravity = Gravity.CENTER
            tile.addView(tv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })
            return tile
        }
        timerText = TextView(this).apply { text = "۰۰:۰۰"; textSize = 15f; setTextColor(cText); typeface = FONT_HERO }
        countText = TextView(this).apply { text = "۰ نفر"; textSize = 15f; setTextColor(cText); typeface = FONT_HERO }
        msgCountText = TextView(this).apply { text = "۰"; textSize = 15f; setTextColor(cText); typeface = FONT_HERO }
        statsRow.addView(statTile("⏱ مدت تماس", timerText), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6f) })
        statsRow.addView(statTile("👥 حاضرین", countText), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6f) })
        statsRow.addView(statTile("💬 پیام‌ها", msgCountText), LinearLayout.LayoutParams(0, -2, 1f))
        main.addView(statsRow, LinearLayout.LayoutParams(-1, -2))

        // LAST CHAT CHIP
        lastChatChip = TextView(this).apply {
            text = "💬 آخرین پیام — برای باز کردن چت ضربه بزن"
            textSize = 11.5f
            setTextColor(cMuted)
            setPadding(dp(15f), dp(12f), dp(15f), dp(12f))
            background = rounded(0xCC0C1226.toInt(), 16f, (a1 and 0x00FFFFFF) or (0x33 shl 24), 1.2f)
            setOnClickListener { openChat() }
        }
        pressFx(lastChatChip)
        main.addView(lastChatChip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        // HINT / QUOTE CARD
        val hintCard = glassCard(16f)
        hintText = TextView(this).apply {
            text = "هنوز کسی نیست — دوستانت را دعوت کن!\n" + quotes[quoteIdx]
            textSize = 12f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }
        hintCard.addView(hintText, LinearLayout.LayoutParams(-1, -2))
        main.addView(hintCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        tabPages["home"] = scroll
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    /* ------------------------- PEOPLE PAGE ------------------------- */
    private fun buildPeoplePage(root: FrameLayout) {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(26f), dp(16f), dp(120f))
        }
        scroll.addView(col, LinearLayout.LayoutParams(-1, -2))

        val head = pageHeader("افراد", rightKind = "plus") { shareInvite() }
        col.addView(head, LinearLayout.LayoutParams(-1, -2))
        val sub = TextView(this).apply {
            text = "اعضای اتاق فعلی — برای دعوت، + را بزن"
            textSize = 11.5f
            setTextColor(cMuted)
            setPadding(dp(4f), 0, dp(4f), dp(12f))
        }
        col.addView(sub, LinearLayout.LayoutParams(-1, -2))

        val peopleCard = glassCard(20f)
        peopleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        peopleCard.addView(peopleBox, LinearLayout.LayoutParams(-1, -2))
        col.addView(peopleCard, LinearLayout.LayoutParams(-1, -2))

        val inviteBtn = button("📣 دعوت از دوستان", a1, a2, 13f)
        inviteBtn.setOnClickListener { shareInvite() }
        pressFx(inviteBtn)
        col.addView(inviteBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        tabPages["people"] = scroll
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    /* ------------------------- REACTIONS PAGE ------------------------- */
    private fun buildReactPage(root: FrameLayout) {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(26f), dp(16f), dp(120f))
        }
        scroll.addView(col, LinearLayout.LayoutParams(-1, -2))

        col.addView(pageHeader("ری‌اکشن‌ها"), LinearLayout.LayoutParams(-1, -2))
        val sub = TextView(this).apply {
            text = "ضربه بزن تا برای همه در اتاق بپره ⚡"
            textSize = 11.5f
            setTextColor(cMuted)
            setPadding(dp(4f), 0, dp(4f), dp(12f))
        }
        col.addView(sub, LinearLayout.LayoutParams(-1, -2))

        val cats = listOf(
            "همه" to listOf("❤️", "😍", "🥰", "😘", "🔥", "⚡", "💪", "🚀", "😂", "🤣", "😄", "😁", "🎉", "👏", "🥳", "🎂", "👍", "😎", "🤝", "🙏", "💯", "⭐", "💎", "🎯", "🌙"),
            "حب" to listOf("❤️", "😍", "🥰", "😘", "💕", "💖", "🌹", "✨"),
            "انرژی" to listOf("🔥", "⚡", "💪", "🚀", "💯", "🎯", "🏁", "💥"),
            "خنده" to listOf("😂", "🤣", "😄", "😁", "😆", "😉", "🙃", "😜"),
            "تبریک" to listOf("🎉", "👏", "🥳", "🎂", "🎁", "🏆", "🥂", "🌟")
        )
        var activeCat = 0
        val chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<TextView>()

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            background = rounded(0xCC0C1226.toInt(), 20f, (a1 and 0x00FFFFFF) or (0x40 shl 24), 1.3f)
        }

        fun drawGrid(list: List<String>) {
            grid.removeAllViews()
            var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, e) in list.withIndex()) {
                val tile = TextView(this).apply {
                    text = e
                    textSize = 26f
                    gravity = Gravity.CENTER
                    setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
                    background = rounded(0x14FFFFFF, 16f)
                    setOnClickListener {
                        sendReaction(e)
                        toast("$e ارسال شد ⚡")
                    }
                }
                pressFx(tile)
                row.addView(tile, LinearLayout.LayoutParams(0, dp(56f), 1f).apply { marginEnd = dp(6f); topMargin = dp(6f) })
                if (i % 4 == 3) {
                    grid.addView(row, LinearLayout.LayoutParams(-1, -2))
                    row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                }
            }
            if (row.childCount > 0) grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        fun drawCats() {
            for (i in cats.indices) {
                val ch = chips[i]
                ch.setTextColor(if (i == activeCat) Color.WHITE else cMuted)
                ch.background = if (i == activeCat) {
                    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a1, a2)).apply { cornerRadius = dp(13f).toFloat() }
                } else rounded(Color.parseColor("#111A33"), 13f, Color.parseColor("#25335C"), 1f)
            }
        }

        for ((i, cat) in cats.withIndex()) {
            val ch = TextView(this).apply {
                text = cat.first
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(dp(13f), dp(8f), dp(13f), dp(8f))
                setOnClickListener {
                    activeCat = i
                    drawCats()
                    drawGrid(cat.second)
                }
            }
            pressFx(ch)
            chips.add(ch)
            chipsRow.addView(ch, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7f) })
        }
        col.addView(chipsRow, LinearLayout.LayoutParams(-1, -2))
        col.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        drawGrid(cats[0].second)
        drawCats()

        val histTitle = TextView(this).apply {
            text = "🕑 ری‌اکشن‌های اخیر"
            textSize = 13f
            setTextColor(cText)
            typeface = FONT_HERO
            setPadding(dp(4f), dp(18f), dp(4f), dp(8f))
        }
        col.addView(histTitle, LinearLayout.LayoutParams(-1, -2))
        val histCard = glassCard(18f)
        reactList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val emptyR = TextView(this).apply {
            text = "هنوز ری‌اکشنی نیست — اولی رو تو بفرست!"
            textSize = 11.5f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, dp(10f))
        }
        reactList.addView(emptyR, LinearLayout.LayoutParams(-1, -2))
        histCard.addView(reactList, LinearLayout.LayoutParams(-1, -2))
        col.addView(histCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })

        tabPages["react"] = scroll
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    /* ------------------------- ABOUT PAGE (overlay) ------------------------- */
    private fun buildAboutPage(root: FrameLayout) {
        aboutPage = FrameLayout(this)
        aboutPage.setBackgroundColor(0xF205070F.toInt())
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18f), dp(28f), dp(18f), dp(28f))
        }
        col.addView(pageHeader("درباره برنامه", withBack = true, onBack = { closeAbout() }), LinearLayout.LayoutParams(-1, -2))

        val logoWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(26f), 0, dp(10f))
        }
        val logo = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = min(width, height) / 2f
                val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
                glowP.shader = RadialGradient(cx, cy, r * 1.25f, a1, 0x00000000, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r * 1.25f, glowP)
                val bgP = Paint(Paint.ANTI_ALIAS_FLAG)
                bgP.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, a1, a2, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, bgP)
                val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.07f
                    color = Color.WHITE
                }
                c.drawCircle(cx, cy, r * 0.86f, ringP)
                Icons.draw(c, "phone", Color.WHITE, r * 0.92f, cx, cy)
            }
        }
        logoWrap.addView(logo, LinearLayout.LayoutParams(dp(128f), dp(128f)))
        col.addView(logoWrap, LinearLayout.LayoutParams(-1, -2))

        val appName = TextView(this).apply {
            text = "esy call"
            textSize = 30f
            setTextColor(cText)
            typeface = FONT_HERO
            gravity = Gravity.CENTER
        }
        gradientText(appName, intArrayOf(a1, Color.WHITE, a2))
        col.addView(appName, LinearLayout.LayoutParams(-1, -2))

        val tag = TextView(this).apply {
            text = "تماس صوتی گروهی — سریع، امن، خفن"
            textSize = 12.5f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(20f))
        }
        col.addView(tag, LinearLayout.LayoutParams(-1, -2))

        val info = glassCard(18f)
        fun infoRow(label: String, value: String) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8f), 0, dp(8f))
                background = rounded(0x11FFFFFF, 12f)
            }
            val l = TextView(this).apply { text = label; textSize = 11.5f; setTextColor(cMuted); typeface = FONT_HERO; letterSpacing = 0.03f; setPadding(dp(10f), 0, 0, 0) }
            val v = TextView(this).apply { text = value; textSize = 12f; setTextColor(a1); typeface = FONT_HERO; gravity = Gravity.END; setPadding(0, 0, dp(10f), 0) }
            r.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(v, LinearLayout.LayoutParams(-2, -2))
            info.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5f) })
        }
        infoRow("نسخه", "نسخه ۱۳ ⚡")
        infoRow("تم", themes[themeIndex].first)
        infoRow("امنیت", "🛡 سرور داخل برنامه پنهان است")
        infoRow("ساخته‌شده با", "❤️ برای گفتگوی زنده")
        col.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        aboutPage.addView(col, FrameLayout.LayoutParams(-1, -1))
        aboutPage.visibility = View.GONE
        root.addView(aboutPage, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openAbout() {
        aboutPage.visibility = View.VISIBLE
        aboutPage.alpha = 0f
        aboutPage.translationY = dp(40f).toFloat()
        aboutPage.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun closeAbout() {
        aboutPage.animate().alpha(0f).translationY(dp(40f).toFloat()).setDuration(190).withEndAction {
            aboutPage.visibility = View.GONE
        }.start()
    }

    private val recentReacts = ArrayList<String>()

    private fun addReactLog(who: String, e: String) {
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        recentReacts.add(0, "$e  $who — $t")
        while (recentReacts.size > 12) recentReacts.removeAt(recentReacts.size - 1)
        runOnUiThread {
            if (!alive || !::reactList.isInitialized) return@runOnUiThread
            reactList.removeAllViews()
            for (r in recentReacts) {
                val row = TextView(this).apply {
                    text = r
                    textSize = 13f
                    setTextColor(cText)
                    setPadding(dp(6f), dp(8f), dp(6f), dp(8f))
                }
                reactList.addView(row, LinearLayout.LayoutParams(-1, -2))
                val div = View(this).apply { setBackgroundColor(0x1A3355AA) }
                reactList.addView(div, LinearLayout.LayoutParams(-1, dp(1f)))
            }
        }
    }

    private fun buildSettingsPage(root: FrameLayout) {
        settingsPage = FrameLayout(this)
        settingsPage.setBackgroundColor(0xF205070F.toInt())
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(26f), dp(16f), dp(30f))
        }
        scroll.addView(col, LinearLayout.LayoutParams(-1, -2))
        col.addView(pageHeader("تنظیمات", withBack = true, onBack = { closeSettings() }), LinearLayout.LayoutParams(-1, -2))

        // THEME
        val themeCard = glassCard(20f)
        val themeTitle = TextView(this).apply {
            text = "🎨 تم رنگی"
            textSize = 13.5f
            setTextColor(cText)
            typeface = FONT_HERO
        }
        themeCard.addView(themeTitle, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10f) })
        val themeGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var trow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in themes.indices) {
            val chip = TextView(this).apply {
                text = themes[i].first
                textSize = 11.5f
                setTextColor(if (i == themeIndex) Color.WHITE else cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(13f), dp(9f), dp(13f), dp(9f))
                background = if (i == themeIndex) {
                    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor(themes[i].second), Color.parseColor(themes[i].third))).apply { cornerRadius = dp(13f).toFloat() }
                } else rounded(Color.parseColor("#111A33"), 13f, Color.parseColor("#25335C"), 1f)
                setOnClickListener {
                    themeIndex = i
                    prefs.edit().putInt("theme", i).apply()
                    recreate()
                }
            }
            pressFx(chip)
            trow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7f); topMargin = dp(6f) })
            if (i % 2 == 1) {
                themeGrid.addView(trow, LinearLayout.LayoutParams(-1, -2))
                trow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            }
        }
        if (trow.childCount > 0) themeGrid.addView(trow, LinearLayout.LayoutParams(-1, -2))
        themeCard.addView(themeGrid, LinearLayout.LayoutParams(-1, -2))
        col.addView(themeCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

        // TOGGLES
        val togCard = glassCard(20f)
        val togTitle = TextView(this).apply {
            text = "🔔 صدا و لرزش"
            textSize = 13.5f
            setTextColor(cText)
            typeface = FONT_HERO
        }
        togCard.addView(togTitle, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8f) })
        fun togRow2(label: String, key: String, def: Boolean) {
            val on = prefs.getBoolean(key, def)
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4f), dp(9f), dp(4f), dp(9f))
            }
            val l = TextView(this).apply {
                text = label
                textSize = 12.5f
                setTextColor(cText)
            }
            r.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
            val b = TextView(this).apply {
                text = if (on) "روشن ✅" else "خاموش ⭕"
                textSize = 11f
                setTextColor(if (on) cGreen else cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(13f), dp(7f), dp(13f), dp(7f))
                background = rounded(Color.parseColor("#111A33"), 12f, if (on) cGreen else Color.parseColor("#25335C"), 1.2f)
                setOnClickListener {
                    val v = !(prefs.getBoolean(key, def))
                    prefs.edit().putBoolean(key, v).apply()
                    recreate()
                }
            }
            pressFx(b)
            r.addView(b, LinearLayout.LayoutParams(-2, -2))
            togCard.addView(r, LinearLayout.LayoutParams(-1, -2))
        }
        togRow2("🔔 صدای تماس", "sound", true)
        togRow2("📳 لرزش هنگام ری‌اکشن", "vibrate", true)
        col.addView(togCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

        // SECURITY
        val secCard = glassCard(20f)
        val secTitle = TextView(this).apply {
            text = "🛡 امنیت"
            textSize = 13.5f
            setTextColor(cText)
            typeface = FONT_HERO
        }
        secCard.addView(secTitle, LinearLayout.LayoutParams(-2, -2))
        val secBody = TextView(this).apply {
            text = "سرور داخل برنامه پنهان است و هرگز نمایش داده نمی‌شود.\nکلیدها فقط نزد تو هستند — قبل از انتشار عمومی عوضشان کن."
            textSize = 11.5f
            setTextColor(cMuted)
            setPadding(0, dp(6f), 0, 0)
        }
        secCard.addView(secBody, LinearLayout.LayoutParams(-1, -2))
        col.addView(secCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

        // BUTTONS
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16f), 0, 0) }
        val invite = button("📣 دعوت از دوستان", a1, a2, 12.5f)
        invite.setOnClickListener { shareInvite() }
        pressFx(invite)
        btnRow.addView(invite, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8f) })
        val logout = button("🚪 خروج از حساب", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12.5f)
        logout.setOnClickListener {
            prefs.edit().remove("name").remove("username").remove("bio").remove("emoji").remove("my_photo_b64").apply()
            invalidatePhoto()
            render()
            toast("از حساب محلی خارج شدی")
        }
        pressFx(logout)
        btnRow.addView(logout, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(btnRow, LinearLayout.LayoutParams(-1, -2))

        val aboutRow = button("ℹ️ درباره برنامه", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12.5f)
        aboutRow.setOnClickListener { openAbout() }
        pressFx(aboutRow)
        col.addView(aboutRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        settingsPage.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        settingsPage.visibility = View.GONE
        root.addView(settingsPage, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openSettings() {
        settingsPage.visibility = View.VISIBLE
        settingsPage.alpha = 0f
        settingsPage.translationY = dp(40f).toFloat()
        settingsPage.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun closeSettings() {
        settingsPage.animate().alpha(0f).translationY(dp(40f).toFloat()).setDuration(190).withEndAction {
            settingsPage.visibility = View.GONE
        }.start()
    }

    /* ------------------------- CHAT PAGE (Telegram style) ------------------------- */
    private fun buildChatPage(root: FrameLayout) {
        chatPage = FrameLayout(this)
        chatPage.setBackgroundColor(Color.parseColor("#F205070F"))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(14f), dp(10f), dp(14f))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF0C1226.toInt(), 0xFF12204A.toInt()))
        }
        val back = CircleIconButton(this, "back") { closeChat() }
        back.accent = a1
        header.addView(back, LinearLayout.LayoutParams(dp(50f), dp(50f)))

        chatHeaderAvatar = ImageView(this)
        header.addView(chatHeaderAvatar, LinearLayout.LayoutParams(dp(42f), dp(42f)).apply { marginStart = dp(8f) })

        val tt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val t1 = TextView(this).apply {
            text = "گفتگوی زنده"
            textSize = 15f
            setTextColor(cText)
            typeface = FONT_HERO
        }
        chatSubTv = TextView(this).apply {
            text = "۰ نفر در این اتاق"
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
        header.addView(chatMicBtn, LinearLayout.LayoutParams(dp(50f), dp(50f)))
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
        col.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        replyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(8f), dp(10f), dp(2f))
            visibility = View.GONE
            background = rounded(0x221B4B8F, 12f)
        }
        replyQuoteTv = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(cText)
            maxLines = 1
        }
        replyBar.addView(replyQuoteTv, LinearLayout.LayoutParams(0, -2, 1f))
        val cancelReply = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(cMuted)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener {
                pendingReply = null
                replyBar.visibility = View.GONE
            }
        }
        replyBar.addView(cancelReply, LinearLayout.LayoutParams(-2, -2))
        col.addView(replyBar, LinearLayout.LayoutParams(-1, -2))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(96f))
        }
        chatInput = field("پیام بنویس...", true)
        chatInput.setOnEditorActionListener { _, _, _ -> sendChat(); true }
        inputRow.addView(chatInput, LinearLayout.LayoutParams(0, -2, 1f))
        val sendBtn = CircleIconButton(this, "send") { sendChat() }
        sendBtn.accent = a1
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(54f), dp(54f)).apply { marginStart = dp(10f) })
        col.addView(inputRow, LinearLayout.LayoutParams(-1, -2))

        chatPage.addView(col, FrameLayout.LayoutParams(-1, -1))
        tabPages["chat"] = chatPage
        root.addView(chatPage, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openChat() {
        switchTab("chat")
    }

    private fun closeChat() {
        switchTab("home")
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
        runCatching {
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
            av.setOnClickListener { if (m.mine) openProfile() else viewProfile(m.from) }

            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            val nameTv = TextView(this).apply {
                text = m.from + if (m.mine) " (شما)" else ""
                textSize = 11f
                typeface = FONT_HERO
                setTextColor(if (m.mine) Color.parseColor("#BFE3FF") else a1)
                setPadding(dp(2f), 0, dp(2f), dp(3f))
                setOnClickListener { if (m.mine) openProfile() else viewProfile(m.from) }
            }

            val bubble = TextView(this).apply {
                text = m.text
                textSize = 13.5f
                setTextColor(Color.WHITE)
                maxWidth = dp(262f)
                setPadding(dp(14f), dp(10f), dp(14f), dp(8f))
                background = if (m.mine) {
                    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF1B4B8F.toInt(), 0xFF2E7DD1.toInt())).apply {
                        cornerRadii = floatArrayOf(dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(18f).toFloat(), dp(5f).toFloat(), dp(18f).toFloat())
                        setStroke(dp(1f), 0x22FFFFFF)
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
                        val ts = logEntry("m", m.text, "", m.quote)
                        seenMsgs.add("${m.from}|$ts|m|${m.text}|")
                        pushMeta()
                        rebuildChat()
                        scope.launch {
                            val ok = publishBytes(JSONObject().put("t", "chat").put("n", m.from).put("m", m.text).put("q", m.quote).put("ts", ts).toString().toByteArray(Charsets.UTF_8))
                            m.status = if (ok) 1 else 2
                            if (chatOpen) rebuildChat()
                        }
                    }
                }
                meta.addView(st, LinearLayout.LayoutParams(-2, -2))
            }

            inner.addView(nameTv)
            if (m.quote.isNotEmpty()) {
                val qv = TextView(this).apply {
                    text = m.quote
                    textSize = 10f
                    setTextColor(Color.parseColor("#AEC4E8"))
                    maxLines = 1
                    setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
                    background = rounded(0x33FFFFFF, 10f)
                }
                inner.addView(qv, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(3f) })
            }
            inner.addView(bubble)
            inner.addView(meta)
            if (m.reactions.isNotEmpty()) {
                val rr = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(8f), dp(3f), 0, 0)
                }
                for (e2 in m.reactions.distinct()) {
                    rr.addView(TextView(this).apply {
                        text = e2
                        textSize = 14f
                        setPadding(dp(3f), 0, dp(3f), 0)
                    })
                }
                inner.addView(rr, LinearLayout.LayoutParams(-2, -2))
            }
            bubble.setOnLongClickListener {
                showReactPicker(m)
                true
            }
            var swipeX = 0f
            bubble.setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, ev: android.view.MotionEvent): Boolean {
                    when (ev.getActionMasked()) {
                        0 -> swipeX = ev.getRawX()
                        2 -> {
                            val d = ev.getRawX() - swipeX
                            if (d > 0) v.setTranslationX(min(d * 0.5f, dp(56f).toFloat()))
                        }
                        1, 3 -> {
                            val d = ev.getRawX() - swipeX
                            v.animate().translationX(0f).setDuration(160).start()
                            if (d > dp(64f)) startReply(m)
                        }
                    }
                    return false
                }
            })

            if (m.mine) {
                wrap.addView(inner, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
                wrap.addView(av, LinearLayout.LayoutParams(dp(34f), dp(34f)))
            } else {
                wrap.addView(av, LinearLayout.LayoutParams(dp(34f), dp(34f)))
                wrap.addView(inner, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
            }

            chatList.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }.onFailure {
            runCatching {
                chatList.addView(TextView(this).apply {
                    text = m.from + ": " + m.text
                    textSize = 12f
                    setTextColor(cText)
                    setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }

    private fun startReply(m: Msg) {
        pendingReply = m
        if (::replyQuoteTv.isInitialized) {
            replyQuoteTv.text = "↩️ در پاسخ به " + m.from + ": " + m.text.take(36)
            replyBar.visibility = View.VISIBLE
        }
    }

    private fun showReactPicker(m: Msg) {
        runCatching {
            val overlay = FrameLayout(this)
            overlay.setBackgroundColor(0x9905070F.toInt())
            val holder = LinearLayout(this).apply { gravity = Gravity.CENTER }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
                background = rounded(0xF20C1226.toInt(), 22f, (a1 and 0x00FFFFFF) or (0x55 shl 24), 1.4f)
            }
            val title = TextView(this).apply {
                text = "ری‌اکشن به پیام " + m.from
                textSize = 11f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
            }
            card.addView(title, LinearLayout.LayoutParams(-1, -2))
            val preview = TextView(this).apply {
                text = m.text.take(42)
                textSize = 12f
                setTextColor(cText)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(6f), 0, dp(8f))
            }
            card.addView(preview, LinearLayout.LayoutParams(-1, -2))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (e in listOf("❤️", "🔥", "😂", "👍", "🎉", "⚡")) {
                val tv = TextView(this).apply {
                    text = e
                    textSize = 26f
                    setPadding(dp(9f), dp(7f), dp(9f), dp(7f))
                    setOnClickListener {
                        rootRef.removeView(overlay)
                        sendReactTo(m, e)
                    }
                }
                pressFx(tv)
                row.addView(tv, LinearLayout.LayoutParams(-2, -2))
            }
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
            val replyBtn = TextView(this).apply {
                text = "↩️  پاسخ به این پیام"
                textSize = 12f
                setTextColor(a1)
                gravity = Gravity.CENTER
                setPadding(0, dp(10f), 0, dp(4f))
                setOnClickListener {
                    rootRef.removeView(overlay)
                    startReply(m)
                }
            }
            card.addView(replyBtn, LinearLayout.LayoutParams(-1, -2))
            overlay.setOnClickListener { rootRef.removeView(overlay) }
            holder.addView(card, LinearLayout.LayoutParams(-2, -2))
            overlay.addView(holder, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            rootRef.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun sendReactTo(m: Msg, e: String) {
        runCatching {
            spawnFloat(e)
            tone()
            vibrate()
            val ref = m.from + "|" + m.text
            val ts = logEntry("r", e, ref)
            seenMsgs.add("${myName()}|$ts|r|$e|$ref")
            pushMeta()
            if (!m.reactions.contains(e)) {
                m.reactions.add(e)
                if (chatOpen) rebuildChat()
            }
            scope.launch {
                runCatching { publishBytes(JSONObject().put("t", "react").put("e", e).put("n", myName()).put("r", ref).put("ts", ts).put("rc", roomCode ?: "public").toString().toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    private fun sendChat() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return
        chatInput.setText("")
        val u = myName()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val rep = pendingReply
        val q = if (rep != null) rep.from + ": " + rep.text.take(40) else ""
        val msg = Msg(u, text, true, 0, time)
        msg.quote = q
        pendingReply = null
        if (::replyBar.isInitialized) replyBar.visibility = View.GONE
        chatMessages.add(msg)
        val ts = logEntry("m", text, "", q)
        seenMsgs.add("$u|$ts|m|$text|")
        pushMeta()
        if (chatOpen) {
            addBubble(msg)
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
        render()
        scope.launch {
            runCatching {
                val ok = publishBytes(JSONObject().put("t", "chat").put("n", u).put("m", text).put("q", q).put("ts", ts).put("rc", roomCode ?: "public").toString().toByteArray(Charsets.UTF_8))
                msg.status = if (ok) 1 else 2
                if (!ok) toast("ارسال نشد — روی ✗ بزن دوباره بفرست")
                if (chatOpen) rebuildChat()
            }
        }
    }

    private fun sendReaction(e: String) {
        spawnFloat(e)
        tone()
        vibrate()
        val ts = logEntry("r", e)
        seenMsgs.add("${myName()}|$ts|r|$e|")
        pushMeta()
        addReactLog("شما", e)
        scope.launch {
            runCatching { publishBytes(JSONObject().put("t", "react").put("e", e).put("n", myName()).put("ts", ts).toString().toByteArray(Charsets.UTF_8)) }
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
    private fun buildProfilePage(root: FrameLayout) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(26f), dp(16f), dp(120f))
        }
        scroll.addView(box, LinearLayout.LayoutParams(-1, -2))

        val head = pageHeader("حساب من", rightKind = "gear") { openSettings() }
        box.addView(head, LinearLayout.LayoutParams(-1, -2))

        val avWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        profileAvatar = ImageView(this)
        avWrap.addView(profileAvatar, LinearLayout.LayoutParams(dp(110f), dp(110f)).apply { topMargin = dp(12f) })

        val photoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val pickBtn = button("📷 انتخاب عکس", a1, a2, 12f)
        pickBtn.setOnClickListener { pickPhoto() }
        pressFx(pickBtn)
        photoRow.addView(pickBtn, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
        val rmBtn = button("✂️ حذف عکس", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12f)
        rmBtn.setOnClickListener {
            prefs.edit().remove("my_photo_b64").apply()
            invalidatePhoto()
            renderProfileAvatar()
            toast("عکس حذف شد")
        }
        pressFx(rmBtn)
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

        val save = button(if (!isRegistered()) "ثبت‌نام و ورود ✅" else "ذخیره پروفایل", a1, a2, 14f)
        save.setOnClickListener {
            if (nameField.text.toString().trim().isEmpty() || userField.text.toString().trim().isEmpty()) {
                toast("نام و نام کاربری الزامیه ✍️")
                return@setOnClickListener
            }
            prefs.edit()
                .putString("name", nameField.text.toString().trim())
                .putString("username", userField.text.toString().trim())
                .putString("bio", bioField.text.toString().trim())
                .putString("emoji", selectedEmoji)
                .apply()
            savedB = currentBioFull()
            pushMeta()
            render()
            toast("ثبت‌نام انجام شد ✅")
            switchTab("home")
        }
        pressFx(save)
        box.addView(save, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })

        val linksRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12f), 0, 0) }
        val roomsBtn = button("🏠 اتاق‌ها", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12f)
        roomsBtn.setOnClickListener { openRooms() }
        pressFx(roomsBtn)
        linksRow.addView(roomsBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7f) })
        val setBtn = button("⚙️ تنظیمات", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12f)
        setBtn.setOnClickListener { openSettings() }
        pressFx(setBtn)
        linksRow.addView(setBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7f) })
        val aboutBtn = button("ℹ️ درباره", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 12f)
        aboutBtn.setOnClickListener { openAbout() }
        pressFx(aboutBtn)
        linksRow.addView(aboutBtn, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(linksRow, LinearLayout.LayoutParams(-1, -2))

        /* ---- Contact the Creator ---- */
        val contact = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16f), dp(22f), dp(16f), dp(18f))
            background = rounded(0x0EFFFFFF, 22f, 0x22AEC4E8, 1.2f)
        }
        val ctTitle = TextView(this).apply {
            text = "Contact the Creator"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(FONT_HERO, Typeface.ITALIC)
        }
        gradientText(ctTitle, intArrayOf(a1, Color.parseColor("#AEC4E8"), a2))
        contact.addView(ctTitle, LinearLayout.LayoutParams(-2, -2))
        val ctSub = TextView(this).apply {
            text = "Tap an icon to connect"
            textSize = 10f
            setTextColor(cMuted)
            setPadding(0, dp(4f), 0, dp(14f))
        }
        contact.addView(ctSub, LinearLayout.LayoutParams(-2, -2))

        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val contactItems = listOf(
            Triple(0, "Instagram", "instagram://user?username=esy_442") to "https://instagram.com/esy_442",
            Triple(1, "Telegram", "tg://resolve?domain=esy_382") to "https://t.me/esy_382",
            Triple(2, "Gmail", "mailto:esrafilnazeri@gmail.com") to "https://mail.google.com/mail/?view=cm&to=esrafilnazeri@gmail.com"
        )
        for ((triple, web) in contactItems) {
            val (kind, label, link) = triple
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(13f), dp(6f), dp(13f), dp(6f))
            }
            val icon = LogoIconView(this, kind)
            icon.background = rounded(0x14FFFFFF, 20f)
            val click = View.OnClickListener { openLink(link, web) }
            icon.setOnClickListener(click)
            col.addView(icon, LinearLayout.LayoutParams(dp(58f), dp(58f)))
            val lb = TextView(this).apply {
                text = label
                textSize = 10.5f
                setTextColor(0xFFAEC4E8.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(7f), 0, 0)
            }
            lb.setOnClickListener(click)
            col.addView(lb, LinearLayout.LayoutParams(-2, -2))
            pressFx(col)
            iconRow.addView(col, LinearLayout.LayoutParams(-2, -2))
        }
        contact.addView(iconRow, LinearLayout.LayoutParams(-2, -2))
        box.addView(contact, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20f) })

        tabPages["profile"] = scroll
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
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
        switchTab("profile")
    }

    private fun viewProfile(name: String) {
        runCatching {
            val info = peerInfo[name]
            val username = info?.first ?: ""
            var emo = info?.second ?: ""
            var bioTxt = info?.third ?: ""
            if (emo.isEmpty() || bioTxt.isEmpty()) {
                for (p in (runCatching { ArrayList(CallManager.people) }.getOrNull() ?: ArrayList())) {
                    if (p.name == name) {
                        val pe = parseEmoji(p.bio)
                        if (emo.isEmpty()) emo = pe.first
                        if (bioTxt.isEmpty()) bioTxt = pe.second
                    }
                }
            }
            emo = emo.ifBlank { "👤" }

            val overlay = FrameLayout(this)
            overlay.setBackgroundColor(0x9905070F.toInt())
            val holder = LinearLayout(this).apply { gravity = Gravity.CENTER }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(26f), dp(24f), dp(26f), dp(16f))
                background = rounded(0xF20C1226.toInt(), 24f, (a1 and 0x00FFFFFF) or (0x55 shl 24), 1.4f)
            }

            val av = ImageView(this)
            av.background = AvatarDrawable(peerAvatars[name], emo, a1, a2)
            card.addView(av, LinearLayout.LayoutParams(dp(132f), dp(132f)))
            av.setOnClickListener {
                runCatching {
                    val bmp = peerAvatars[name]
                    if (bmp != null) {
                        val fs = FrameLayout(this)
                        fs.setBackgroundColor(0xF205070F.toInt())
                        val iv = ImageView(this)
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        iv.setImageBitmap(bmp)
                        fs.addView(iv, FrameLayout.LayoutParams(-1, -1))
                        fs.setOnClickListener { rootRef.removeView(fs) }
                        rootRef.addView(fs, FrameLayout.LayoutParams(-1, -1))
                    }
                }
            }

            val nameTv = TextView(this).apply {
                text = name
                textSize = 19f
                setTextColor(Color.WHITE)
                typeface = FONT_HERO
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, 0)
            }
            gradientText(nameTv, intArrayOf(a1, Color.parseColor("#AEC4E8"), a2))
            card.addView(nameTv, LinearLayout.LayoutParams(-1, -2))

            if (username.isNotEmpty()) {
                val uTv = TextView(this).apply {
                    text = "@$username"
                    textSize = 12f
                    setTextColor(cMuted)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3f), 0, 0)
                }
                card.addView(uTv, LinearLayout.LayoutParams(-1, -2))
            }

            val bioCard = TextView(this).apply {
                text = if (bioTxt.isNotEmpty()) bioTxt else "بیویی نداره"
                textSize = 12.5f
                setTextColor(if (bioTxt.isNotEmpty()) cText else cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
                background = rounded(0x14FFFFFF, 16f)
            }
            card.addView(bioCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

            val person = (runCatching { ArrayList(CallManager.people) }.getOrNull() ?: ArrayList()).firstOrNull { it.name == name }
            val statusTv = TextView(this).apply {
                text = if (person?.speaking == true) "🎙 در حال صحبت" else "● آنلاین"
                textSize = 11f
                setTextColor(if (person?.speaking == true) cGreen else cMuted)
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, 0)
            }
            card.addView(statusTv, LinearLayout.LayoutParams(-1, -2))

            val closeBtn = TextView(this).apply {
                text = "بستن ✕"
                textSize = 12.5f
                setTextColor(a1)
                gravity = Gravity.CENTER
                setPadding(dp(24f), dp(14f), dp(24f), dp(6f))
                setOnClickListener { rootRef.removeView(overlay) }
            }
            card.addView(closeBtn, LinearLayout.LayoutParams(-1, -2))

            overlay.setOnClickListener { rootRef.removeView(overlay) }
            holder.addView(card, LinearLayout.LayoutParams(-2, -2))
            overlay.addView(holder, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            rootRef.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun closeProfile() {
        switchTab("home")
    }

    private fun openLink(primary: String, fallback: String? = null) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primary)))
        } catch (e: Exception) {
            if (fallback != null) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
                } catch (e2: Exception) {
                    toast("باز نشد 😅")
                }
            } else toast("باز نشد 😅")
        }
    }

    private inner class LogoIconView(context: Context, private val kind: Int) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            when (kind) {
                0 -> {
                    val shader = LinearGradient(
                        0f, h, w, 0f,
                        intArrayOf(0xFFFEDA75.toInt(), 0xFFFA7E1E.toInt(), 0xFFD62976.toInt(), 0xFF962FBF.toInt(), 0xFF4F5BD5.toInt()),
                        null, Shader.TileMode.CLAMP
                    )
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = w * 0.085f
                    p.shader = shader
                    val r = w * 0.28f
                    c.drawRoundRect(RectF(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f), r, r, p)
                    c.drawCircle(w * 0.5f, h * 0.5f, w * 0.215f, p)
                    p.shader = null
                    p.style = Paint.Style.FILL
                    p.color = 0xFFD62976.toInt()
                    c.drawCircle(w * 0.715f, h * 0.285f, w * 0.055f, p)
                }
                1 -> {
                    p.style = Paint.Style.FILL
                    p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF2AABEE.toInt(), 0xFF229ED9.toInt()), null, Shader.TileMode.CLAMP)
                    c.drawCircle(w * 0.5f, h * 0.5f, w * 0.5f, p)
                    p.shader = null
                    p.color = Color.WHITE
                    val pl = Path()
                    pl.moveTo(w * 0.15f, h * 0.52f)
                    pl.lineTo(w * 0.88f, h * 0.19f)
                    pl.lineTo(w * 0.62f, h * 0.84f)
                    pl.lineTo(w * 0.50f, h * 0.61f)
                    pl.lineTo(w * 0.33f, h * 0.75f)
                    pl.lineTo(w * 0.37f, h * 0.55f)
                    pl.close()
                    c.drawPath(pl, p)
                    p.color = 0xFFA9D3EE.toInt()
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = w * 0.035f
                    p.strokeCap = Paint.Cap.ROUND
                    val fd = Path()
                    fd.moveTo(w * 0.15f, h * 0.52f)
                    fd.lineTo(w * 0.88f, h * 0.19f)
                    fd.moveTo(w * 0.50f, h * 0.61f)
                    fd.lineTo(w * 0.88f, h * 0.19f)
                    c.drawPath(fd, p)
                }
                else -> {
                    p.style = Paint.Style.FILL
                    p.color = Color.WHITE
                    c.drawRoundRect(RectF(w * 0.05f, h * 0.14f, w * 0.95f, h * 0.86f), w * 0.10f, w * 0.10f, p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = w * 0.125f
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeJoin = Paint.Join.ROUND
                    p.color = 0xFFEA4335.toInt()
                    val m = Path()
                    m.moveTo(w * 0.13f, h * 0.72f)
                    m.lineTo(w * 0.13f, h * 0.30f)
                    m.lineTo(w * 0.50f, h * 0.60f)
                    m.lineTo(w * 0.87f, h * 0.30f)
                    m.lineTo(w * 0.87f, h * 0.72f)
                    c.drawPath(m, p)
                    p.color = 0xFFF6AEA9.toInt()
                    p.strokeWidth = w * 0.10f
                    val m2 = Path()
                    m2.moveTo(w * 0.13f, h * 0.26f)
                    m2.lineTo(w * 0.50f, h * 0.56f)
                    m2.lineTo(w * 0.87f, h * 0.26f)
                    c.drawPath(m2, p)
                }
            }
        }
    }


    /* ------------------------- ROOMS ------------------------- */
    private fun buildRoomsPage(root: FrameLayout) {
        roomsPage = FrameLayout(this)
        roomsPage.setBackgroundColor(Color.parseColor("#F205070F"))
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(28f), dp(18f), dp(28f))
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "🏠 اتاق‌ها"
            textSize = 19f
            setTextColor(cText)
            typeface = FONT_HERO
        }
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val closeBtn = CircleIconButton(this, "back") { closeRooms() }
        closeBtn.accent = a1
        head.addView(closeBtn, LinearLayout.LayoutParams(dp(50f), dp(50f)))
        col.addView(head, LinearLayout.LayoutParams(-1, -2))

        roomsCurrentTv = TextView(this).apply {
            text = "🌐 اتاق فعلی: عمومی"
            textSize = 12f
            setTextColor(a1)
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = rounded(0x2200E5FF, 14f, a1, 1.2f)
        }
        col.addView(roomsCurrentTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })

        // --- ساخت اتاق ---
        val mk = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20f), dp(24f), dp(20f), dp(20f))
            background = rounded(0xCC0C1226.toInt(), 22f, 0xFF2A3D72.toInt(), 1.6f)
        }
        mk.addView(HeroIconView(this, "shield", a1), LinearLayout.LayoutParams(dp(72f), dp(72f)))
        mk.addView(TextView(this).apply {
            text = "ساخت اتاق"
            textSize = 17f
            setTextColor(cText)
            typeface = FONT_HERO
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        mk.addView(TextView(this).apply {
            text = "اتاق خصوصی با نام و رمز دلخواه بساز"
            textSize = 11.5f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        val mkBtn = button("ساخت اتاق 🚀", a1, a2, 14f)
        pressFx(mkBtn)
        mkBtn.setOnClickListener { showCreateRoomPage() }
        mk.addView(mkBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
        col.addView(mk, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })

        // --- ورود به اتاق ---
        val jn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20f), dp(24f), dp(20f), dp(20f))
            background = rounded(0xCC0C1226.toInt(), 22f, 0xFF2A3D72.toInt(), 1.6f)
        }
        jn.addView(HeroIconView(this, "key", Color.parseColor("#22D3EE")), LinearLayout.LayoutParams(dp(72f), dp(72f)))
        jn.addView(TextView(this).apply {
            text = "ورود به اتاق"
            textSize = 17f
            setTextColor(cText)
            typeface = FONT_HERO
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        jn.addView(TextView(this).apply {
            text = "با نام اتاق و رمز دوستت وارد شو"
            textSize = 11.5f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        val jnBtn = button("ورود به اتاق 🔑", Color.parseColor("#2E7DD1"), Color.parseColor("#22D3EE"), 14f)
        pressFx(jnBtn)
        jnBtn.setOnClickListener { showJoinRoomPage() }
        jn.addView(jnBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
        col.addView(jn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        // --- عمومی (لینک کوچک) ---
        val pubLink = TextView(this).apply {
            text = "🌐 ورود به اتاق عمومی"
            textSize = 12f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(18f), 0, 0)
            setOnClickListener {
                roomCode = null
                closeRooms()
                onCallClick()
            }
        }
        col.addView(pubLink, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(col, LinearLayout.LayoutParams(-1, -2))
        roomsPage.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.addView(roomsPage, FrameLayout.LayoutParams(-1, -1))
        roomsPage.visibility = View.GONE
    }

    private fun openRooms() {
        refreshRooms()
        roomsPage.visibility = View.VISIBLE
        roomsPage.alpha = 0f
        roomsPage.translationY = dp(40f).toFloat()
        roomsPage.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun closeRooms() {
        roomsPage.animate().alpha(0f).translationY(dp(40f).toFloat()).setDuration(180).withEndAction {
            roomsPage.visibility = View.GONE
        }.start()
    }

    private fun passHash(p: String): String =
        "#" + Integer.toHexString(p.trim().hashCode()).replace("-", "n").take(8)

    private fun sanitizeRoom(u: String): String {
        val s = u.replace(Regex("[^A-Za-z0-9_-]"), "").take(24)
        return s.ifBlank { "room" }
    }

    private fun passHashOf(bio: String?): String {
        var b = bio ?: return ""
        if (b.startsWith("R:")) {
            val bar = b.indexOf('|')
            b = if (bar >= 0) b.substring(bar + 1) else ""
        }
        if (b.startsWith("#")) {
            val bar = b.indexOf('|')
            return if (bar >= 0) b.substring(0, bar) else b
        }
        return ""
    }

    private fun runJoinCheck() {
        runCatching {
            if (myPassHash.isEmpty()) return
            val others = (runCatching { ArrayList(CallManager.people) }.getOrNull() ?: ArrayList()).filter { !it.me }
            if (others.isEmpty()) {
                toast("اتاقی با این نام وجود ندارد!")
            } else {
                val hashes = others.map { passHashOf(it.bio) }.filter { it.isNotEmpty() }
                if (hashes.isNotEmpty() && hashes.none { it == myPassHash }) {
                    toast("رمز اشتباه!")
                    doHangup()
                }
            }
        }
    }

    private fun refreshRooms() {
        if (::roomsCurrentTv.isInitialized) roomsCurrentTv.text = if (roomCode != null) "🔒 اتاق فعلی: " + (if (roomCode == myRoomCode && myRoomUser.isNotEmpty()) "«$myRoomUser»" else "خصوصی") else "🌐 اتاق فعلی: عمومی"
        if (::myCodeTv.isInitialized) myCodeTv.text = if (myRoomUser.isNotEmpty()) "🔒 " + myRoomUser else "اتاقی نساخته‌ای"
    }

    private fun createRoom() {
        showCreateRoomPage()
    }

    private fun doCreateRoom(u: String, p: String) {
        myRoomUser = u
        myRoomPass = p
        myPassHash = passHash(p)
        myRoomCode = sanitizeRoom(u)
        iAmOwner = true
        prefs.edit().putString("my_room_user", u).putString("my_room_pass", p).putString("my_room_code", myRoomCode).apply()
        roomCode = myRoomCode
        refreshRooms()
        closeRooms()
        onCallClick()
        showWaitingRoom()
    }

    private fun showCreateRoomPage() {
        runCatching {
            val overlay = FrameLayout(this)
            overlay.setBackgroundColor(0xF205070F.toInt())
            val scroll = ScrollView(this)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24f), dp(30f), dp(24f), dp(30f))
            }
            scroll.addView(box, LinearLayout.LayoutParams(-1, -2))
            overlay.addView(scroll, FrameLayout.LayoutParams(-1, -1))

            val back = TextView(this).apply {
                text = "→ بازگشت"
                textSize = 12.5f
                setTextColor(cMuted)
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                setOnClickListener { rootRef.removeView(overlay) }
            }
            box.addView(back, LinearLayout.LayoutParams(-2, -2))

            val hero = HeroIconView(this, "shield", a1)
            box.addView(hero, LinearLayout.LayoutParams(dp(96f), dp(96f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(22f); bottomMargin = dp(6f) })

            val title = TextView(this).apply {
                text = "ساخت اتاق خصوصی"
                textSize = 21f
                setTextColor(Color.WHITE)
                typeface = FONT_HERO
                gravity = Gravity.CENTER
            }
            gradientText(title, intArrayOf(a1, Color.parseColor("#AEC4E8"), a2))
            box.addView(title, LinearLayout.LayoutParams(-1, -2))

            val sub = TextView(this).apply {
                text = "نام و رمز رو خودت انتخاب کن\nدوستت با همون نام و رمز وارد میشه"
                textSize = 12f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(10f), dp(8f), dp(10f), dp(20f))
            }
            box.addView(sub, LinearLayout.LayoutParams(-1, -2))

            val nameF = field("نام اتاق (مثلاً: ali_room)")
            nameF.setText(myRoomUser)
            box.addView(nameF, LinearLayout.LayoutParams(-1, -2))

            val passF = field("رمز عبور (خودت انتخاب کن)")
            passF.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passF.setText(myRoomPass)
            box.addView(passF, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

            val createBtn = button("ساخت اتاق و ورود ✅", a1, a2, 14f)
            createBtn.setOnClickListener {
                val u = nameF.text.toString().trim()
                val p = passF.text.toString().trim()
                if (u.isEmpty() || p.isEmpty()) {
                    toast("نام اتاق و رمز رو بنویس ✍️")
                    return@setOnClickListener
                }
                rootRef.removeView(overlay)
                doCreateRoom(u, p)
            }
            pressFx(createBtn)
            box.addView(createBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })

            rootRef.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }.onFailure { toast("خطای صفحه ساخت: " + (it.message ?: "?")) }
    }

    private fun showJoinRoomPage() {
        runCatching {
            val overlay = FrameLayout(this)
            overlay.setBackgroundColor(0xF205070F.toInt())
            val scroll = ScrollView(this)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24f), dp(30f), dp(24f), dp(30f))
            }
            scroll.addView(box, LinearLayout.LayoutParams(-1, -2))
            overlay.addView(scroll, FrameLayout.LayoutParams(-1, -1))

            val back = TextView(this).apply {
                text = "→ بازگشت"
                textSize = 12.5f
                setTextColor(cMuted)
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                setOnClickListener { rootRef.removeView(overlay) }
            }
            box.addView(back, LinearLayout.LayoutParams(-2, -2))

            val hero = HeroIconView(this, "key", Color.parseColor("#22D3EE"))
            box.addView(hero, LinearLayout.LayoutParams(dp(96f), dp(96f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(22f); bottomMargin = dp(6f) })

            val title = TextView(this).apply {
                text = "ورود به اتاق دوست"
                textSize = 21f
                setTextColor(Color.WHITE)
                typeface = FONT_HERO
                gravity = Gravity.CENTER
            }
            gradientText(title, intArrayOf(a1, Color.parseColor("#AEC4E8"), a2))
            box.addView(title, LinearLayout.LayoutParams(-1, -2))

            val sub = TextView(this).apply {
                text = "نام اتاق و رمزی که دوستت بهت داده رو وارد کن"
                textSize = 12f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(10f), dp(8f), dp(10f), dp(20f))
            }
            box.addView(sub, LinearLayout.LayoutParams(-1, -2))

            joinUserField = field("نام اتاق دوستت...")
            joinUserField.setText(myRoomUser)
            box.addView(joinUserField, LinearLayout.LayoutParams(-1, -2))

            joinPassField = field("رمز عبور اتاق")
            joinPassField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(joinPassField, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

            val joinBtn = button("ورود به اتاق ✅", Color.parseColor("#2E7DD1"), Color.parseColor("#22D3EE"), 14f)
            joinBtn.setOnClickListener {
                rootRef.removeView(overlay)
                joinWithCode()
            }
            pressFx(joinBtn)
            box.addView(joinBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18f) })

            rootRef.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }.onFailure { toast("خطای صفحه ورود: " + (it.message ?: "?")) }
    }

    private fun showWaitingRoom() {
        runCatching {
            val overlay = FrameLayout(this)
            overlay.setBackgroundColor(0xF205070F.toInt())
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(30f), 0, dp(30f), 0)
            }
            overlay.addView(box, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

            val hero = HeroIconView(this, "send", a1)
            box.addView(hero, LinearLayout.LayoutParams(dp(104f), dp(104f)))
            hero.alpha = 0f
            hero.animate().alpha(1f).setDuration(700).start()

            val title = TextView(this).apply {
                text = "اتاق «$myRoomUser» ساخته شد 🔒"
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = FONT_HERO
                gravity = Gravity.CENTER
                setPadding(0, dp(6f), 0, 0)
            }
            gradientText(title, intArrayOf(a1, Color.parseColor("#AEC4E8"), a2))
            box.addView(title, LinearLayout.LayoutParams(-1, -2))

            val st = TextView(this).apply {
                text = "● در انتظار افراد دیگر..."
                textSize = 14f
                setTextColor(cAmber)
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, 0)
            }
            box.addView(st, LinearLayout.LayoutParams(-1, -2))
            val pulse = object : Runnable {
                override fun run() {
                    runCatching {
                        st.animate().alpha(if (st.alpha > 0.7f) 0.45f else 1f).setDuration(750).withEndAction(this).start()
                    }
                }
            }
            st.animate().alpha(0.45f).setDuration(750).withEndAction(pulse).start()

            val hint = TextView(this).apply {
                text = "نام اتاق و رمز رو به دوستت بده\nوقتی وارد شد، خودکار وارد اتاق میشید"
                textSize = 11.5f
                setTextColor(cMuted)
                gravity = Gravity.CENTER
                setPadding(dp(10f), dp(14f), dp(10f), dp(22f))
                background = rounded(0x14FFFFFF, 16f)
            }
            box.addView(hint, LinearLayout.LayoutParams(-1, -2))

            val shareBtn = button("📤 دعوت دوستان", Color.parseColor("#1B2440"), Color.parseColor("#101728"), 13f)
            shareBtn.setOnClickListener { shareInvite() }
            pressFx(shareBtn)
            box.addView(shareBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

            val enterBtn = button("🚀 ورود به اتاق", a1, a2, 14f)
            enterBtn.setOnClickListener {
                waitingOverlay = null
                rootRef.removeView(overlay)
                switchTab("home")
            }
            pressFx(enterBtn)
            box.addView(enterBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

            waitingOverlay = overlay
            rootRef.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }.onFailure { toast("خطای صفحه انتظار: " + (it.message ?: "?")) }
    }

    private fun joinWithCode() {
        val u = if (::joinUserField.isInitialized) joinUserField.text.toString().trim() else myRoomUser
        val p = if (::joinPassField.isInitialized) joinPassField.text.toString().trim() else myRoomPass
        if (u.isEmpty() || p.isEmpty()) {
            toast("نام اتاق و رمز رو بنویس ✍️")
            return
        }
        myRoomUser = u
        myRoomPass = p
        myPassHash = passHash(p)
        myRoomCode = sanitizeRoom(u)
        iAmOwner = false
        prefs.edit().putString("my_room_user", u).putString("my_room_pass", p).putString("my_room_code", myRoomCode).apply()
        roomCode = myRoomCode
        refreshRooms()
        closeRooms()
        onCallClick()
    }

    private fun copyCode() {
        val c = if (myRoomUser.isNotEmpty()) myRoomUser else (myRoomCode ?: roomCode)
        if (c == null) {
            toast("هنوز اتاقی نساخته‌ای — «ساخت» رو بزن")
            return
        }
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("room", c))
        }
        toast("نام اتاق «$c» کپی شد 📋")
    }

    private fun shareInvite() {
        runCatching {
            val txt = if (myRoomUser.isNotEmpty())
                "بیا تو اتاق «$myRoomUser» در esy call! 🚀\nنام اتاق: $myRoomUser\nرمز: $myRoomPass\nنصب کن → «اتاق‌ها» → «ورود» → نام و رمز رو بزن."
            else
                "بیا تو تماس عمومی esy call! برنامه رو نصب کن و «شروع تماس» رو بزن 🚀"
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, txt)
            startActivity(Intent.createChooser(i, "اشتراک‌گذاری"))
        }
    }

    /* ------------------------- PHOTO ------------------------- */
    private var photoCache: Bitmap? = null
    private var photoCacheValid = false

    private fun myPhoto(): Bitmap? {
        if (!photoCacheValid) {
            val b = prefs.getString("my_photo_b64", "") ?: ""
            photoCache = if (b.isEmpty()) null else b64ToBitmap(b)
            photoCacheValid = true
        }
        return photoCache
    }

    private fun invalidatePhoto() {
        photoCacheValid = false
        photoCache = null
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
                    val src = decodeSampled(uri, 900) ?: return@launch
                    val big = circleCrop(src, 512)
                    val b64 = bitmapToB64(big, 85)
                    runOnUiThread {
                        if (!alive) return@runOnUiThread
                        prefs.edit().putString("my_photo_b64", b64).apply()
                        invalidatePhoto()
                        render()
                        renderProfileAvatar()
                        toast("عکس پروفایل تنظیم شد ✅")
                    }
                    runOnUiThread { if (alive) sendMyAvatar(force = true) }
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
        val tb = b64ToBitmap(b)
        val sb64 = if (tb != null) bitmapToB64(Bitmap.createScaledBitmap(tb, 384, 384, true), 82) else b
        pendingAvatarB64 = sb64
        runCatching { pushMeta() }
        scope.launch {
            runCatching {
                val o = JSONObject().put("t", "av").put("n", myName()).put("a", sb64).put("rc", roomCode ?: "public")
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
        val nowT = System.currentTimeMillis()
        if (nowT - roomFindAt < 4000) return null
        roomFindAt = nowT
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
        // 3) SAFE shallow scan: fields only (no getter side effects!), own packages only
        fun scan(obj: Any?, depth: Int, seen: java.util.IdentityHashMap<Any, Boolean>): Any? {
            if (obj == null || depth > 2) return null
            if (seen.put(obj, true) != null) return null
            if (isRoomLike(obj)) return obj
            val pkg = obj.javaClass.`package`?.name ?: ""
            if (depth > 0 && !(pkg.startsWith("com.esrafil") || pkg.startsWith("io.livekit"))) return null
            for (f in obj.javaClass.declaredFields) {
                val t = f.type
                if (t == String::class.java || t.isPrimitive || t.isEnum) continue
                runCatching {
                    f.isAccessible = true
                    val v = f.get(obj) ?: return@runCatching
                    val r = scan(v, depth + 1, seen)
                    if (r != null) return r
                }
            }
            return null
        }
        val r = scan(CallManager, 0, java.util.IdentityHashMap())
        if (r != null) cachedRoom = r
        return r
    }

    private fun getLp(room: Any): Any? = runCatching {
        room.javaClass.methods.firstOrNull { it.name == "getLocalParticipant" && it.parameterCount == 0 }?.invoke(room)
    }.getOrNull()

    /** FIXED: continuation must be Any? (LiveKit resumes with Result, not Boolean — the old Boolean cast CRASHED the app). */
    private suspend fun publishBytes(data: ByteArray, topic: String = "chat"): Boolean {
        val room = findRoom() ?: run { lastSendErr = "اتصال پیدا نشد"; return false }
        val lp = getLp(room) ?: run { lastSendErr = "participant پیدا نشد"; return false }

        // Strategy A: exact suspend publishData (5 args: bytes, reliability, topic, destinations, cont)
        val exact = lp.javaClass.methods.firstOrNull {
            it.name.startsWith("publishData") && !it.name.contains("$") && it.parameterCount == 5
        }
        if (exact != null) {
            val r = withTimeoutOrNull(9000) {
                suspendCancellableCoroutine<Any?> { cont ->
                    try {
                        val ps = exact.parameterTypes
                        val rel = ps[1].enumConstants?.firstOrNull { (it as Enum<*>).name == "RELIABLE" }
                        val out = exact.invoke(lp, data, rel, topic, null, cont)
                        if (out !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) cont.resume(out)
                    } catch (t: Throwable) {
                        lastSendErr = "publishData: " + (t.cause?.message ?: t.message ?: "?")
                        runCatching { cont.resume(t) }
                    }
                }
            }
            if (r != null && r !is Throwable && !r.toString().startsWith("Failure")) return true
            if (r != null && r.toString().startsWith("Failure")) lastSendErr = r.toString().take(70)
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
                    val r = withTimeoutOrNull(9000) {
                        suspendCancellableCoroutine<Any?> { cont ->
                            args[args.size - 1] = cont
                            try {
                                val out = m.invoke(lp, *args)
                                if (out !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) cont.resume(out)
                            } catch (t: Throwable) {
                                lastSendErr = "pb: " + (t.cause?.message ?: t.message ?: "?")
                                runCatching { cont.resume(t) }
                            }
                        }
                    }
                    if (r != null && r !is Throwable && !r.toString().startsWith("Failure")) return true
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
            val rcPkt = o.optString("rc")
            if (rcPkt.isNotEmpty() && rcPkt != (roomCode ?: "public")) return@runCatching
            when (o.optString("t")) {
                "chat" -> {
                    val kn = o.optString("n")
                    val kt = o.optLong("ts")
                    if (kt > 0 && !seenMsgs.add("$kn|$kt|m|${o.optString("m")}|")) return@runCatching
                    runOnUiThread {
                    if (alive) runCatching {
                        val m = Msg(o.optString("n"), o.optString("m"), false, 1, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                        m.quote = o.optString("q")
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
                    }
                }
                "react" -> {
                    val kn = o.optString("n")
                    val kt = o.optLong("ts")
                    if (kt > 0 && !seenMsgs.add("$kn|$kt|r|${o.optString("e")}|${o.optString("r")}")) return@runCatching
                    runOnUiThread {
                    if (alive) runCatching {
                        val e2 = o.optString("e")
                        spawnFloat(e2)
                        val ref2 = o.optString("r")
                        if (ref2.isNotBlank()) {
                            val target = chatMessages.firstOrNull { it.from + "|" + it.text == ref2 }
                            if (target != null && !target.reactions.contains(e2)) {
                                target.reactions.add(e2)
                                if (chatOpen) rebuildChat()
                            }
                        }
                    }
                    }
                }
                "av" -> runOnUiThread {
                    if (alive) runCatching {
                        val bmp = b64ToBitmap(o.optString("a"))
                        if (bmp != null) {
                            val keyAv = o.optString("n")
                            val oldAv = peerAvatars[keyAv]
                            if (oldAv == null || bmp.width >= oldAv.width) {
                                peerAvatars[keyAv] = bmp
                                render()
                                if (chatOpen) rebuildChat()
                            }
                        }
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
                    runCatching {
                        if (ev is RoomEvent.DataReceived) {
                            val raw = runCatching { ev.data.toString(Charsets.UTF_8) }.getOrNull()
                            if (raw != null) handlePacket(raw)
                        } else if (ev is RoomEvent.ParticipantMetadataChanged) {
                            val nm = runCatching { ev.participant.name ?: "" }.getOrDefault("")
                            val md = runCatching { ev.participant.metadata ?: "" }.getOrDefault("")
                            if (md.isNotEmpty()) handleMeta(nm, md)
                        }
                    }
                }
            }
            eventsJob = null
        }
    }

    /* ------- metadata transport (works WITHOUT server changes!) ------- */
    private fun roomCodeOf(bio: String?): String {
        val b = bio ?: return ""
        val m = Regex("^R:([A-Za-z0-9_-]{2,24})\\|").find(b) ?: return ""
        return m.groupValues[1]
    }

    private fun sameRoom(code: String): Boolean = code == (roomCode ?: "")

    private fun sameRoomSoft(bio: String?): Boolean {
        val c = roomCodeOf(bio ?: "")
        if (roomCode == null) return c.isEmpty()
        if (c.isNotEmpty() && c != roomCode) return false
        val h = passHashOf(bio ?: "")
        return h.isEmpty() || h == myPassHash
    }

    private fun currentBioFull(): String {
        val emo = prefs.getString("emoji", "😎") ?: "😎"
        val b = (prefs.getString("bio", "") ?: "").trim()
        val rc = roomCode
        return (if (rc != null) "R:$rc|$myPassHash|" else "") + "$emo|$b"
    }

    private fun logEntry(k: String, x: String, ref: String = "", q: String = ""): Long {
        val ts = System.currentTimeMillis()
        val e = JSONObject()
            .put("t", ts)
            .put("k", k)
            .put("x", x)
            .put("ref", ref)
            .put("q", q)
            .put("n", myName())
        myLog.add(e)
        while (myLog.size > 12) myLog.removeAt(0)
        return ts
    }

    private fun pushMeta(): Boolean {
        return try {
            val room = findRoom() ?: return false
            val lp = getLp(room) ?: return false
            val l = JSONArray()
            for (e in myLog) l.put(e)
            val o = JSONObject()
                .put("u", savedU.ifBlank { prefs.getString("username", "") ?: "" })
                .put("b", savedB.ifBlank { currentBioFull() })
                .put("an", myName())
                .put("l", l)
            if (pendingAvatarB64.isNotEmpty()) o.put("a", pendingAvatarB64)
            val inst = lp as? LocalParticipant
            if (inst != null) {
                inst.updateMetadata(o.toString())
                pendingAvatarB64 = ""
                true
            } else {
                val m = lp.javaClass.methods.firstOrNull { it.name == "updateMetadata" && it.parameterCount == 1 }
                if (m != null) {
                    m.invoke(lp, o.toString())
                    pendingAvatarB64 = ""
                    true
                } else false
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun handleMeta(partName: String, meta: String) {
        runCatching {
            val o = JSONObject(meta)
            val senderCode = roomCodeOf(o.optString("b"))
            if (!sameRoom(senderCode)) return
            if (roomCode != null) {
                val sh = passHashOf(o.optString("b"))
                if (myPassHash.isNotEmpty() && sh.isNotEmpty() && sh != myPassHash) return
            }
            val pu = o.optString("u")
            val pb = o.optString("b")
            if (partName.isNotEmpty() && (pu.isNotEmpty() || pb.isNotEmpty())) {
                val pe = parseEmoji(pb)
                peerInfo[partName] = Triple(pu, pe.first, pe.second)
            }
            val av = o.optString("a")
            if (av.isNotEmpty()) {
                val bmp = b64ToBitmap(av)
                if (bmp != null) {
                    val keys = listOf(o.optString("an").ifBlank { partName }, partName, pu)
                    runOnUiThread {
                        if (alive) runCatching {
                            for (kk in keys) {
                                if (kk.isBlank()) continue
                                val oldAv = peerAvatars[kk]
                                if (oldAv == null || bmp.width >= oldAv.width) peerAvatars[kk] = bmp
                            }
                            render()
                            if (chatOpen) rebuildChat()
                        }
                    }
                }
            }
            val l = o.optJSONArray("l") ?: return
            for (i in 0 until l.length()) {
                val e = l.getJSONObject(i)
                val k = e.optString("k")
                val x = e.optString("x")
                val t = e.optLong("t")
                val n = e.optString("n").ifBlank { partName }
                val ref = e.optString("ref")
                val q = e.optString("q")
                val key = "$n|$t|$k|$x|$ref"
                if (seenMsgs.size > 600) seenMsgs.clear()
                if (!seenMsgs.add(key)) continue
                when (k) {
                    "m" -> runOnUiThread {
                        if (alive) runCatching {
                            val tm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
                            val msg = Msg(n, x, false, 1, tm)
                            msg.quote = q
                            chatMessages.add(msg)
                            if (chatOpen) {
                                addBubble(msg)
                                chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
                            } else {
                                unread++
                                tone()
                                vibrate()
                            }
                            render()
                        }
                    }
                    "r" -> runOnUiThread {
                        if (alive) runCatching {
                            if (n != myName()) {
                                spawnFloat(x)
                                addReactLog(n, x)
                            }
                            if (ref.isNotBlank()) {
                                val target = chatMessages.firstOrNull { it.from + "|" + it.text == ref }
                                if (target != null && !target.reactions.contains(x)) {
                                    target.reactions.add(x)
                                    if (chatOpen) rebuildChat()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /* ------------------------- RENDER + TICKER ------------------------- */
    private fun requestRender() {
        val now = System.currentTimeMillis()
        if (now - lastRenderAt < 600) {
            renderPending = true
            return
        }
        render()
    }

    private fun render() {
        lastRenderAt = System.currentTimeMillis()
        runCatching {
        val st = CallManager.status
        val inCall = st == "connected" || st == "connecting" || st == "reconnecting"

        callBtn.visibility = if (inCall) View.GONE else View.VISIBLE
        micView.visibility = if (inCall) View.VISIBLE else View.GONE
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

        if (::roomChipTv.isInitialized) {
            roomChipTv.text = if (roomCode != null) "اتاق خصوصی 🔒 $roomCode — ضربه برای مدیریت"
            else "اتاق عمومی — برای ساخت اتاق خصوصی ضربه بزن"
        }

        if (chatMessages.isNotEmpty()) {
            val last = chatMessages[chatMessages.size - 1]
            val prefix = if (unread > 0) "💬 ($unread) " else "💬 "
            lastChatChip.text = prefix + last.from + ": " + last.text.take(42)
        } else {
            lastChatChip.text = "💬 آخرین پیام — برای باز کردن چت ضربه بزن"
        }

        if (::chatSubTv.isInitialized) {
            val vc = (runCatching { ArrayList(CallManager.people) }.getOrNull() ?: ArrayList()).count { sameRoomSoft(it.bio) }
            chatSubTv.text = fa(vc.toString()) + " نفر در این اتاق"
            countText.text = fa(vc.toString()) + " نفر"
            if (::msgCountText.isInitialized) msgCountText.text = fa(chatMessages.size.toString())
        }
        }
    }

    private fun renderPeople() {
        if (!::peopleBox.isInitialized) return
        runCatching {
        peopleBox.removeAllViews()
        val list = (runCatching { ArrayList(CallManager.people) }.getOrNull() ?: ArrayList()).filter { sameRoomSoft(it.bio) }
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
        for ((idx, p) in list.withIndex()) {
            if (idx > 0) {
                val div = View(this).apply { setBackgroundColor(0x223355AA) }
                peopleBox.addView(div, LinearLayout.LayoutParams(-1, dp(1f)))
            }
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
                typeface = FONT_HERO
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

            row.setOnClickListener { if (p.me) openProfile() else viewProfile(p.name) }
            pressFx(row)
            peopleBox.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        }.onFailure {
            runCatching {
                peopleBox.removeAllViews()
                peopleBox.addView(TextView(this).apply {
                    text = "⚠️ خطا در نمایش افراد"
                    textSize = 11f
                    setTextColor(cRed)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10f), 0, dp(10f))
                }, LinearLayout.LayoutParams(-1, -2))
            }
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

                val speaking = runCatching { CallManager.people.any { it.speaking } }.getOrDefault(false)
                val lvl = if (speaking) 0.5f + 0.5f * abs(sin(t * 5f)) else 0f
                if (::micView.isInitialized) {
                    micView.level = lvl
                    micView.tick(0.1f)
                }

                waveActive = lastStatus == "connected" && speaking
                waveBottom.active = false
                waveTop.active = false

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

                if (!joinChecked && roomCode != null && !iAmOwner && callStart > 0 && lastStatus == "connected" && System.currentTimeMillis() - callStart > 6000) {
                    joinChecked = true
                    runJoinCheck()
                }
                // self-heal: room might appear later
                if (eventsJob == null) startEventListener()

                if (renderPending && System.currentTimeMillis() - lastRenderAt > 600) {
                    renderPending = false
                    render()
                }

                // new joiners -> re-send avatar
                val pc = runCatching { CallManager.people.size }.getOrDefault(0)
                if (pc != lastPeopleCount) {
                    if (pc > lastPeopleCount) sendMyAvatar()
                    lastPeopleCount = pc
                    requestRender()
                }
                if (waitingOverlay != null && pc >= 2) {
                    val w = waitingOverlay
                    waitingOverlay = null
                    runCatching { rootRef.removeView(w) }
                    toast("دوستت وارد شد! 🎉")
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
        if (!isRegistered()) {
            switchTab("profile")
            toast("اول ثبت‌نام کن ✍️")
            return
        }
        val u = (prefs.getString("username", "") ?: "").trim().ifBlank { "کاربر" + (Math.random() * 900 + 100).toInt() }
        val n = (prefs.getString("name", "") ?: "").trim().ifBlank { u }
        val emo = prefs.getString("emoji", "😎") ?: "😎"
        val b = (prefs.getString("bio", "") ?: "").trim()
        val rc = roomCode
        val server = SERVER
        val bioFull = (if (rc != null) "R:$rc|$myPassHash|" else "") + "$emo|$b"
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingConnect = true
            val perms = arrayListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 100)
            return
        }
        savedU = u
        savedB = bioFull
        joinChecked = false
        switchChatRoom(rc ?: "public")
        toast(if (rc != null) "اتصال به اتاق «$rc» ..." else "اتصال به اتاق عمومی ...")
        callStart = System.currentTimeMillis()
        forceDisconnect()
        runCatching { CallManager.connect(this, server, u, n, bioFull, deviceId()) }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && pendingConnect) {
            pendingConnect = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onCallClick()
            } else {
                toast("بدون اجازه میکروفون نمی‌شه تماس گرفت 🎤")
            }
        }
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

    private fun forceDisconnect() {
        runCatching {
            eventsJob?.cancel()
            eventsJob = null
            for (nm in listOf("hangup", "hangUp", "disconnect", "leave", "endCall", "stopCall", "cleanup", "stop", "close", "destroy")) {
                runCatching {
                    val m = CallManager::class.java.methods.firstOrNull { it.name == nm && it.parameterCount == 0 }
                    if (m != null) m.invoke(CallManager)
                }
                runCatching {
                    val rm = cachedRoom
                    if (rm != null) {
                        val dm = rm.javaClass.methods.firstOrNull { it.name == nm && it.parameterCount == 0 }
                        if (dm != null) dm.invoke(rm)
                    }
                }
            }
            cachedRoom = null
            roomFindAt = 0L
        }
    }

    private fun switchChatRoom(newKey: String) {
        runCatching {
            if (newKey == chatRoomKey) return
            roomChatStore[chatRoomKey] = ArrayList(chatMessages)
            chatMessages.clear()
            chatMessages.addAll(roomChatStore[newKey] ?: ArrayList())
            chatRoomKey = newKey
            unread = 0
            if (chatOpen) rebuildChat()
        }
    }

    private fun doHangup() {
        forceDisconnect()
        callStart = 0L
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
        if (::roomsPage.isInitialized && roomsPage.visibility == View.VISIBLE) {
            closeRooms()
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
            logo.typeface = FONT_HERO
            logo.letterSpacing = 0.1f
            logo.gravity = Gravity.CENTER
            gradientText(logo, intArrayOf(a1, a2))
            box.addView(logo, LinearLayout.LayoutParams(-2, -2))
            val sub = TextView(this)
            sub.text = "نسخه ۱۳ ⚡ GAMING EDITION"
            sub.textSize = 15f
            sub.setTextColor(a1)
            sub.typeface = FONT_HERO
            sub.letterSpacing = 0.08f
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
// END_OF_ESYCALL_UI
