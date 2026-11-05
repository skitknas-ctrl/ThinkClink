package io.github.skitknasctrl.thinkclink

import android.app.Activity
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.min

/** Android side of the game: frame loop, touch, saving, sound, network and the settings dialog. */
class GameView(private val host: Activity) : View(host), GameListener {
    private val prefs = host.getSharedPreferences("thinkclink", 0)
    private val sound = Sound(host)
    private val appVersion = readAppVersion()
    private val backend = Backend(host, appVersion)
    private val images = ImageStore { url -> backend.imageFile(url) }
    private val gfx = AndroidGfx { url -> images.get(url) }
    private val engine: Engine
    private val renderer: Renderer
    private var scale = 1f
    private var last = 0L
    private var settingsOpen = false
    private var language = prefs.getString("language", "en") ?: "en"
    var active = true

    init {
        T.lang = language; language = T.lang
        engine = Engine(loadRules(), prefs.getInt("high", 0), this)
        engine.appVersion = appVersion
        engine.language = language
        renderer = Renderer(engine)
        renderer.savedLevel = if (prefs.contains("save_level")) prefs.getInt("save_level", 0) else 0
        sound.enabled = engine.rules.sound
        isFocusable = true
        backend.onChange = { updateInfo() }
        backend.loadCached { bank -> setBank(bank) }
        backend.sync(true) { bank -> setBank(bank) }
        if (engine.rules.sendStats) backend.flush()
        updateInfo()
    }

    private fun setBank(bank: QuestionBank) {
        engine.bank = bank
        images.refresh()
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
        val count = engine.bank.countForGrade(grade, language)
        val synced = if (backend.lastSyncMillis > 0)
            T.f("info.fetched", SimpleDateFormat("d/M HH:mm", T.locale).format(Date(backend.lastSyncMillis)))
        else T.s("info.notFetched")
        val stats = if (engine.rules.sendStats) T.f("info.statsOn", backend.pending) else T.s("info.statsOff")
        renderer.info = listOfNotNull(
            T.f("info.questions", grade, count, synced),
            stats,
            backend.lastError?.let { T.f("info.netError", it) },
            T.f("info.version", appVersion),
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
        backend.sync(false) { bank -> setBank(bank) }
        if (engine.rules.sendStats) backend.flush()
    }

    fun release() { sound.release(); backend.shutdown() }

    // ---- Frame loop ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val portrait = height >= width
        scale = if (portrait) width / Engine.W else height / Engine.W
        renderer.layout(width / scale, height / scale)
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
            "settings" -> openSettings(false)
            "test" -> openSettings(true)
            "continue" -> loadSave()?.let { engine.continueGame(it, prefs.getString("save_session", null)) } ?: engine.newGame()
            else -> engine.press(b.id, b.arg)
        }
    }

    // ---- Settings screens ----

    private fun openSettings(test: Boolean) {
        if (settingsOpen) return
        settingsOpen = true
        val dialog = if (test) Dialogs.testSettings(host, prefs, engine.rules) { values ->
            engine.rules = Rules(values)
            sound.enabled = engine.rules.sound
            if (!engine.rules.sendStats) backend.clearOutbox()
            engine.newGame()
            updateInfo()
        } else Dialogs.settings(host, prefs, engine.rules, language) { lang, values ->
            language = lang; T.lang = lang; engine.language = lang
            engine.rules = Rules(engine.rules.asMap() + values)
            sound.enabled = engine.rules.sound
            if (!engine.rules.sendStats) backend.clearOutbox()
            updateInfo()
        }
        dialog.setOnDismissListener { settingsOpen = false; last = 0L; invalidate() }
        dialog.show()
    }
}
