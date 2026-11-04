package io.github.skitknasctrl.mathchallange1

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Android side of the game: frame loop, touch, saving, sound, network and the settings dialog. */
class GameView(private val host: Activity) : View(host), GameListener {
    private val prefs = host.getSharedPreferences("mathchallange", 0)
    private val gfx = AndroidGfx()
    private val sound = Sound(host)
    private val appVersion = readAppVersion()
    private val backend = Backend(host, appVersion)
    private val engine: Engine
    private val renderer: Renderer
    private var scale = 1f
    private var last = 0L
    private var settingsOpen = false
    var active = true

    init {
        engine = Engine(loadRules(), prefs.getInt("high", 0), this)
        engine.appVersion = appVersion
        renderer = Renderer(engine)
        renderer.savedLevel = if (prefs.contains("save_level")) prefs.getInt("save_level", 0) else 0
        sound.enabled = engine.rules.sound
        isFocusable = true
        backend.onChange = { updateInfo() }
        backend.loadCached { bank -> engine.bank = bank; updateInfo() }
        backend.sync(true) { bank -> engine.bank = bank }
        if (engine.rules.sendStats) backend.flush()
        updateInfo()
    }

    @Suppress("DEPRECATION")
    private fun readAppVersion(): String = try {
        host.packageManager.getPackageInfo(host.packageName, 0).versionName ?: "dev"
    } catch (e: Exception) {
        "dev"
    }

    /** Status lines on the start screen, so it is easy to see that the server connection works. */
    private fun updateInfo() {
        val grade = engine.rules.grade
        val count = engine.bank.countForGrade(grade)
        val synced = if (backend.lastSyncMillis > 0)
            "hämtade " + SimpleDateFormat("d/M HH:mm", Locale.forLanguageTag("sv-SE")).format(Date(backend.lastSyncMillis))
        else "inte hämtade än"
        val stats = if (engine.rules.sendStats) "Anonym statistik: på · ${backend.pending} nivåer väntar på uppladdning" else "Anonym statistik: av"
        renderer.info = listOfNotNull(
            "Kunskapsfrågor för åk $grade: $count ($synced)",
            stats,
            backend.lastError?.let { "Senaste nätfel: $it" },
            "Version $appVersion",
        )
    }

    private fun loadRules() = Rules(SettingsSpec.all.associate { it.key to prefs.getInt("set_${it.key}", it.default) })

    private fun loadSave(): Map<String, Int>? {
        if (!prefs.contains("save_level")) return null
        return Engine.SAVE_KEYS.filter { prefs.contains("save_$it") }.associateWith { prefs.getInt("save_$it", 0) }
    }

    // ---- GameListener ----

    override fun sound(s: Sfx) = sound.play(s)

    override fun saveProgress(state: Map<String, Int>?) {
        val edit = prefs.edit()
        for (key in Engine.SAVE_KEYS) edit.remove("save_$key")
        edit.remove("save_session")
        if (state != null) {
            state.forEach { (k, v) -> edit.putInt("save_$k", v) }
            edit.putString("save_session", engine.sessionId)
        }
        edit.apply()
        renderer.savedLevel = state?.get("level") ?: 0
    }

    override fun newRecord(high: Int) { prefs.edit().putInt("high", high).apply() }

    override fun statsReady(json: String) {
        if (engine.rules.sendStats) backend.queue(json)
    }

    // ---- Lifecycle ----

    fun onHostPause() { active = false; engine.lifecyclePause() }

    fun onHostResume() {
        active = true; last = 0L; invalidate()
        backend.sync(false) { bank -> engine.bank = bank }
        if (engine.rules.sendStats) backend.flush()
    }

    fun release() { sound.release(); backend.shutdown() }

    // ---- Frame loop ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        scale = width / Engine.W
        engine.worldHeight = height / scale
        val now = System.nanoTime()
        val running = active && !settingsOpen
        val dt = if (last == 0L || !running) 0f else min(0.05f, (now - last) / 1_000_000_000f)
        last = now
        if (running) engine.tick(dt)
        canvas.save()
        canvas.scale(scale, scale)
        gfx.canvas = canvas
        renderer.draw(gfx)
        canvas.restore()
        if (active) postInvalidateOnAnimation()
    }

    // ---- Touch: buttons on release, edge zones while held ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val lifting = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
        val ended = if (lifting) event.actionIndex else -1
        if (lifting) {
            performClick()
            val x = event.getX(ended) / scale
            val y = event.getY(ended) / scale
            renderer.buttons.lastOrNull { it.contains(x, y) }?.let { press(it) }
        }
        var left = false
        var right = false
        if (action != MotionEvent.ACTION_CANCEL && engine.mode == Mode.PLAYING) {
            for (i in 0 until event.pointerCount) {
                if (i == ended) continue
                when (renderer.zoneAt(event.getX(i) / scale, event.getY(i) / scale)) {
                    -1 -> left = true
                    1 -> right = true
                }
            }
        }
        engine.setInput(left, right)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun press(b: Button) {
        when (b.id) {
            "settings" -> openSettings()
            "continue" -> loadSave()?.let { engine.continueGame(it, prefs.getString("save_session", null)) } ?: engine.newGame()
            else -> engine.press(b.id, b.arg)
        }
    }

    // ---- Developer settings ----

    private fun openSettings() {
        if (settingsOpen) return
        settingsOpen = true
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 16, 40, 16) }
        val fields = SettingsSpec.all.map { spec ->
            content.addView(TextView(host).apply { text = "${spec.label} (${spec.min}–${spec.max})" })
            EditText(host).apply {
                setText(engine.rules[spec.key].toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine()
                content.addView(this)
            }
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("Testinställningar")
            .setMessage("Spara startar ett nytt spel. Inställningarna sparas mellan appstarter.")
            .setView(ScrollView(host).apply { addView(content) })
            .setNegativeButton("Avbryt", null)
            .setNeutralButton("Standardvärden", null)
            .setPositiveButton("Spara & nytt spel", null)
            .create()
        dialog.setOnDismissListener { settingsOpen = false; last = 0L; invalidate() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                SettingsSpec.all.forEachIndexed { i, spec -> fields[i].setText(spec.default.toString()); fields[i].error = null }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var valid = true
                val values = HashMap<String, Int>()
                SettingsSpec.all.forEachIndexed { i, spec ->
                    val v = fields[i].text.toString().toIntOrNull()
                    if (v == null || v !in spec.min..spec.max) {
                        fields[i].error = "Ange ${spec.min}–${spec.max}"; valid = false
                    } else values[spec.key] = v
                }
                if (valid) {
                    val edit = prefs.edit()
                    values.forEach { (k, v) -> edit.putInt("set_$k", v) }
                    edit.apply()
                    engine.rules = Rules(values)
                    sound.enabled = engine.rules.sound
                    if (!engine.rules.sendStats) backend.clearOutbox()
                    engine.newGame()
                    updateInfo()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
