package com.esrafil.voicecall

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

class MainActivity : Activity() {

    private companion object {
        const val SERVER = "https://shrill-wave-0b90.esrafilnazeri.workers.dev"
        const val PREFS = "voicecall"
    }

    private val cText = Color.parseColor("#F3F0FF")
    private val cMutedText = Color.parseColor("#9A92C9")
    private val cCyan = Color.parseColor("#22D3EE")
    private val cViolet = Color.parseColor("#7C3AED")
    private val cGreen = Color.parseColor("#34D399")
    private val cAmber = Color.parseColor("#FBBF24")
    private val cGray = Color.parseColor("#6B7280")

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var errorCard: TextView
    private lateinit var profileCard: LinearLayout
    private lateinit var peopleBox: LinearLayout
    private lateinit var callBtn: TextView
    private lateinit var muteBtn: TextView
    private lateinit var endBtn: TextView
    private lateinit var fUser: EditText
    private lateinit var fName: EditText
    private lateinit var fBio: EditText
    private var pendingConnect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        CallManager.onChange = { runOnUiThread { render() } }
        render()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

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
            intArrayOf(Color.argb(95, r, g, b), Color.argb(0, r, g, b))
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
        tv.setShadowLayer(dp(18f).toFloat(), 0f, 0f, Color.parseColor("#7C3AED"))
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

    private fun button(text: String, c1: Int, c2: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14.5f
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
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                }
                false
            }
        }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0A0A1F"))

        root.addView(orb(Color.parseColor("#22D3EE"), dp(320f), dp(-90f), dp(-60f)))
        root.addView(orb(Color.parseColor("#F472B6"), dp(300f), dp(190f), dp(430f)))
        root.addView(orb(Color.parseColor("#7C3AED"), dp(360f), dp(-120f), dp(650f)))

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
        gradientText(title, intArrayOf(Color.parseColor("#67E8F9"), Color.parseColor("#A78BFA"), Color.parseColor("#F472B6")))
        header.addView(title)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val profileBtn = TextView(this)
        profileBtn.text = "پروفایل"
        profileBtn.textSize = 12f
        profileBtn.setTextColor(cCyan)
        profileBtn.gravity = Gravity.CENTER
        profileBtn.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        profileBtn.background = rounded(Color.parseColor("#141033"), 20f, Color.parseColor("#3A2E75"), 1f)
        profileBtn.setOnClickListener {
            profileCard.visibility = if (profileCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
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
        statusText.setTextColor(cMutedText)
        pill.addView(statusText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
        main.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14f) })

        errorCard = TextView(this)
        errorCard.textSize = 12.5f
        errorCard.setTextColor(Color.parseColor("#FDA4AF"))
        errorCard.setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
        errorCard.background = rounded(Color.parseColor("#3B1220"), 16f, Color.parseColor("#7F1D33"), 1f)
        errorCard.visibility = View.GONE
        main.addView(errorCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

        profileCard = LinearLayout(this)
        profileCard.orientation = LinearLayout.VERTICAL
        profileCard.setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        profileCard.background = rounded(Color.parseColor("#171138"), 22f, Color.parseColor("#3A2E75"), 1.5f)
        profileCard.visibility = View.GONE

        val profTitle = TextView(this)
        profTitle.text = "پروفایل"
        profTitle.textSize = 15f
        profTitle.setTextColor(cText)
        profTitle.typeface = Typeface.DEFAULT_BOLD
        profileCard.addView(profTitle)

        fUser = field("نام کاربری")
        fName = field("اسم")
        fBio = field("بیو", single = false)
        fUser.setText(prefs.getString("username", "") ?: "")
        fName.setText(prefs.getString("name", "") ?: "")
        fBio.setText(prefs.getString("bio", "") ?: "")
        profileCard.addView(fUser, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        profileCard.addView(fName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
        profileCard.addView(fBio, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })

        val saveBtn = button("ذخیره پروفایل", cViolet, cCyan)
        saveBtn.setOnClickListener {
            prefs.edit()
                .putString("username", fUser.text.toString().trim())
                .putString("name", fName.text.toString().trim())
                .putString("bio", fBio.text.toString().trim())
                .apply()
            profileCard.visibility = View.GONE
            render()
        }
        profileCard.addView(saveBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        main.addView(profileCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        peopleBox = LinearLayout(this)
        peopleBox.orientation = LinearLayout.VERTICAL
        scroll.addView(peopleBox, LinearLayout.LayoutParams(-1, -2))
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12f) })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        callBtn = button("شروع تماس", cViolet, cCyan)
        callBtn.setOnClickListener { onCallClick() }
        row.addView(callBtn, LinearLayout.LayoutParams(0, -2, 1f))
        muteBtn = button("صدا دار", Color.parseColor("#2A2470"), Color.parseColor("#3B2F8F"))
        muteBtn.setOnClickListener { doToggleMute(); render() }
        muteBtn.visibility = View.GONE
        row.addView(muteBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8f) })
        endBtn = button("قطع تماس", Color.parseColor("#9F1239"), Color.parseColor("#F43F5E"))
        endBtn.setOnClickListener { doHangup(); render() }
        endBtn.visibility = View.GONE
        row.addView(endBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8f) })
        main.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })

        setContentView(root)
    }

    private fun personRow(p: Person): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
        row.background = rounded(
            Color.parseColor("#1C1545"), 20f,
            if (p.speaking) cGreen else Color.parseColor("#2A2258"),
            if (p.speaking) 2f else 1f
        )

        val avatar = TextView(this)
        avatar.text = (p.name.trim().firstOrNull() ?: '?').uppercaseChar().toString()
        avatar.textSize = 16f
        avatar.setTextColor(Color.WHITE)
        avatar.typeface = Typeface.DEFAULT_BOLD
        avatar.gravity = Gravity.CENTER
        avatar.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cCyan, cViolet)).apply {
            shape = GradientDrawable.OVAL
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(42f), dp(42f)))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(11f) })

        val nameTv = TextView(this)
        nameTv.text = p.name + if (p.me) "  (شما)" else ""
        nameTv.textSize = 14f
        nameTv.setTextColor(cText)
        nameTv.typeface = Typeface.DEFAULT_BOLD
        info.addView(nameTv)

        if (p.bio.isNotBlank()) {
            val bioTv = TextView(this)
            bioTv.text = p.bio
            bioTv.textSize = 11.5f
            bioTv.setTextColor(cMutedText)
            bioTv.maxLines = 1
            info.addView(bioTv)
        }

        if (p.speaking) {
            val sp = TextView(this)
            sp.text = "در حال صحبت"
            sp.textSize = 10.5f
            sp.setTextColor(cGreen)
            row.addView(sp)
        }

        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9f) }
        return row
    }

    private fun render() {
        val st = CallManager.status
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
        muteBtn.visibility = if (st == "connected") View.VISIBLE else View.GONE
        endBtn.visibility = if (inCall) View.VISIBLE else View.GONE

        if (CallManager.muted) {
            muteBtn.text = "بی صدا"
            muteBtn.background = rounded(Color.parseColor("#7F1D33"), 26f, Color.parseColor("#BE123C"), 1f)
        } else {
            muteBtn.text = "صدا دار"
            muteBtn.background = rounded(Color.parseColor("#2A2470"), 26f, Color.parseColor("#4C3FA8"), 1f)
        }

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
        }
    }

    private fun onCallClick() {
        val st = CallManager.status
        if (st == "connected" || st == "connecting" || st == "reconnecting") return
        val u = (prefs.getString("username", "") ?: "").trim()
        val n = (prefs.getString("name", "") ?: "").trim()
        if (u.isEmpty() || n.isEmpty()) {
            profileCard.visibility = View.VISIBLE
            errorCard.visibility = View.VISIBLE
            errorCard.text = "لطفا اول پروفایل را کامل کن (نام کاربری و اسم)"
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
        val b = prefs.getString("bio", "") ?: ""
        CallManager.connect(this, SERVER, u, n, b, deviceId())
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
            for (name in listOf("hangup", "hangUp", "disconnect", "leave", "endCall", "stopCall", "cleanup", "stop")) {
                val m = CallManager::class.java.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                if (m != null) {
                    m.invoke(CallManager)
                    return
                }
            }
        }
    }

    override fun onBackPressed() {
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
