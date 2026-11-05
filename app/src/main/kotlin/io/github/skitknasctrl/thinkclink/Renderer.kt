package io.github.skitknasctrl.thinkclink

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class Button(val l: Float, val t: Float, val r: Float, val b: Float, val id: String, val arg: Int = 0) {
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/**
 * Draws the whole game and records where the buttons are. All coordinates are view points:
 * the view is vw × vh points and the playfield (always 360 wide, engine x = 0..360) starts at px0.
 *
 * The game is always played in landscape: the playfield is in the middle, the HUD in the left
 * panel and the three symbol buttons in the right panel, next to the playfield. Draggers
 * (trains, cars …) are not clipped at the playfield edge, so they run out over the panels.
 */
class Renderer(private val e: Engine) {
    companion object {
        const val BTN = 54f
        /** The view is scaled so that it is at least this many points wide: 360 playfield + two 120 panels. */
        const val MIN_WIDTH = 600f
    }

    val buttons = ArrayList<Button>()
    /** Level of the saved game shown on the start screen, 0 if there is none. */
    var savedLevel = 0
    /** Small status lines on the start screen (downloaded questions, statistics). */
    var info: List<String> = emptyList()

    var vw = 360f
        private set
    var vh = 740f
        private set
    var px0 = 0f
        private set
    private val W = Engine.W
    private val cx: Float get() = px0 + W / 2

    /** Sets the geometry for the current view size. Called before every frame. */
    fun layout(viewW: Float, viewH: Float) {
        vw = viewW; vh = viewH
        px0 = ((vw - W) / 2).roundToInt().toFloat()
        e.spawnY = 28f; e.groundY = vh - 18f
    }

    /** -1 move left, 1 move right, 0 nothing. Buttons are never steering areas. */
    fun zoneAt(x: Float, y: Float): Int {
        if (buttons.any { it.contains(x, y) }) return 0
        return if (x < cx) -1 else 1
    }

    fun draw(g: Gfx) {
        buttons.clear()
        background(g)
        g.save(); g.translate(px0, 0f)
        drops(g)
        scenery(g)
        labels(g)
        g.restore()
        if (e.mode != Mode.MENU) hud(g)
        if (e.mode == Mode.PLAYING) controls(g) else if (e.mode != Mode.MENU) idleButtons(g)
        when (e.mode) {
            Mode.MENU -> menu(g)
            Mode.PAUSED -> pause(g)
            Mode.SHOP -> shop(g)
            Mode.QUESTION -> question(g)
            Mode.GAME_OVER -> gameOver(g)
            Mode.PLAYING -> { banner(g); freeze(g) }
            Mode.LOST -> lost(g)
        }
    }

    // ---- Background and playfield ----

    private fun background(g: Gfx) {
        g.gradientRect(0f, 0f, vw, vh, rgb(10, 18, 39), rgb(28, 45, 70))
        val span = max(1, (e.ground - e.spawnY - 20f).toInt())
        for (i in 0..35) g.circle(px0 + ((i * 83 + i * i * 37) % 360).toFloat(), (e.spawnY + 10f + (i * 197 + i * i * 31) % span), 1.5f, rgb(45, 63, 88))
        g.rect(0f, e.ground, vw, vh, rgb(20, 26, 38))
        g.rect(0f, e.ground, vw, e.ground + 5f, rgb(86, 66, 44))
    }

    private fun drops(g: Gfx) {
        if (e.mode == Mode.MENU || e.mode == Mode.GAME_OVER) return
        for (d in e.active) {
            val y = e.y(d)
            if (y > e.ground + 4f) continue
            val x = d.x(e.levelTime)
            when (d.kind) {
                Kind.COIN -> coin(g, x, y)
                Kind.BILL -> bill(g, x, y, sin(d.phase + (e.levelTime - d.release) * Engine.SWAY_SPEED + 1.3f) * 22f)
                Kind.GEM -> gem(g, x, y, 1f, e.clock + d.phase)
            }
        }
    }

    private fun coin(g: Gfx, x: Float, y: Float) {
        g.circle(x, y, 11f, rgb(239, 166, 38))
        g.circle(x - 1f, y - 1f, 8f, rgb(255, 220, 105))
        g.text("1", x, y + 4f, 12f, rgb(136, 84, 10))
    }

    private fun bill(g: Gfx, x: Float, y: Float, tilt: Float) {
        g.save(); g.translate(x, y); g.rotate(tilt)
        g.roundRect(-18f, -9.5f, 18f, 9.5f, 2f, rgb(64, 140, 78))
        g.roundRect(-15f, -6.5f, 15f, 6.5f, 1.5f, rgb(150, 210, 150))
        g.circle(-9f, 0f, 4f, rgb(64, 140, 78))
        g.text("${e.rules.billValue}", 4f, 3.6f, 9f, rgb(30, 80, 40))
        g.restore()
    }

    fun basket(g: Gfx, cx: Float, rim: Float, w: Float, body: Int = rgb(178, 107, 48), band: Int = rgb(231, 171, 90)) {
        val hw = w / 2
        val bw = w * 0.41f
        val hh = Engine.BASKET_HEIGHT
        g.poly(floatArrayOf(cx - hw, rim, cx + hw, rim, cx + bw, rim + hh, cx - bw, rim + hh), body)
        val weave = darker(body)
        for (k in 1..3) {
            val yy = rim + k * 9.5f
            val half = hw + (bw - hw) * (k * 9.5f / hh)
            g.line(cx - half + 1.5f, yy, cx + half - 1.5f, yy, 1.4f, weave)
        }
        for (k in -2..2) g.line(cx + k * hw * 0.4f, rim + 3f, cx + k * bw * 0.4f, rim + hh - 2f, 1.2f, weave)
        g.roundRect(cx - hw - 3f, rim - 2f, cx + hw + 3f, rim + 5f, 3f, band)
    }

    /** The safety net: a mesh across the whole playfield just above the basket rim. */
    private fun net(g: Gfx) {
        val y = e.rim - 6f
        val c = rgb(150, 220, 170)
        var x = 0f
        while (x <= W) { g.line(x, y - 8f, x + 10f, y + 8f, 1.2f, c); g.line(x + 10f, y - 8f, x, y + 8f, 1.2f, c); x += 10f }
        g.line(0f, y - 8f, W, y - 8f, 2f, c)
        g.line(0f, y + 8f, W, y + 8f, 2f, c)
        g.text("${ceil(e.netLeft).toInt()}", W / 2, y - 12f, 12f, c)
    }

    private fun scenery(g: Gfx) {
        val bw = e.basketWidth()
        val left = e.basketX - bw / 2
        val right = e.basketX + bw / 2
        val moving = e.mode == Mode.PLAYING && e.moveDir != 0
        val step = e.speedLevel + 1
        Draggers.drawBehind(g, step, left, right, e.ground, e.facing, e.travelled, moving, e.clock)
        basket(g, e.basketX, e.rim, bw)
        Draggers.drawFront(g, step, left, right, e.ground, e.facing, moving, e.clock)
        if (e.netLeft > 0f && e.mode != Mode.MENU) net(g)
    }

    private fun labels(g: Gfx) {
        for (l in e.labels) g.text(l.text, l.x, l.y - l.age * 34f, 13f, alpha(l.color, (255 * (1f - l.age / 0.9f)).toInt()))
    }

    // ---- Icons ----

    private fun heart(g: Gfx, cx: Float, cy: Float, color: Int, s: Float = 1f) {
        g.circle(cx - 3.3f * s, cy - 2f * s, 4f * s, color)
        g.circle(cx + 3.3f * s, cy - 2f * s, 4f * s, color)
        g.poly(floatArrayOf(cx - 7.1f * s, cy - 0.8f * s, cx + 7.1f * s, cy - 0.8f * s, cx, cy + 7f * s), color)
    }

    private fun gem(g: Gfx, cx: Float, cy: Float, s: Float, time: Float) {
        g.poly(floatArrayOf(cx, cy - 11f * s, cx + 9f * s, cy - 3f * s, cx, cy + 11f * s, cx - 9f * s, cy - 3f * s), rgb(40, 150, 210))
        g.poly(floatArrayOf(cx - 9f * s, cy - 3f * s, cx, cy - 11f * s, cx + 9f * s, cy - 3f * s, cx, cy), rgb(110, 215, 245))
        g.poly(floatArrayOf(cx - 4f * s, cy - 3f * s, cx, cy - 9f * s, cx + 1f * s, cy - 3f * s), rgb(220, 250, 255))
        if (s >= 0.7f) {
            val tw = (sin(time * 5f) + 1f) / 2f
            g.line(cx + 6f * s, cy - 10f * s, cx + 6f * s, cy - (10f - 6f * tw) * s - 6f * s, 1.2f, alpha(Col.WHITE, (200 * tw).toInt()))
        }
    }

    private fun flag(g: Gfx, x: Float, y: Float, s: Float, color: Int) {
        g.line(x, y - 12f * s, x, y + 6f * s, 2f * s, Col.SOFT)
        g.poly(floatArrayOf(x + 1f * s, y - 12f * s, x + 13f * s, y - 8f * s, x + 1f * s, y - 4f * s), color)
    }

    private fun iconNet(g: Gfx, cx: Float, cy: Float, color: Int) {
        val l = cx - 15f; val r = cx + 15f
        g.line(l, cy - 10f, l, cy + 8f, 2.2f, color)
        g.line(r, cy - 10f, r, cy + 8f, 2.2f, color)
        var px = l; var py = cy - 8f
        for (i in 1..6) {
            val x = l + (r - l) * i / 6f
            val y = cy - 8f + 7f * sin(Math.PI.toFloat() * i / 6f)
            g.line(px, py, x, y, 1.8f, color); px = x; py = y
        }
        for (i in 1..5) {
            val x = l + (r - l) * i / 6f
            val y = cy - 8f + 7f * sin(Math.PI.toFloat() * i / 6f)
            g.line(x, y, x, cy + 6f, 1.2f, color)
        }
        g.line(l + 2f, cy + 1f, r - 2f, cy + 1f, 1.2f, color)
        g.line(l + 2f, cy + 6f, r - 2f, cy + 6f, 1.6f, color)
    }

    private fun iconSnail(g: Gfx, cx: Float, cy: Float, color: Int) {
        val body = darker(color, 0.85f)
        g.line(cx - 14f, cy + 6f, cx + 8f, cy + 6f, 5f, body)
        g.line(cx - 13f, cy + 5f, cx - 13f, cy - 2f, 4.5f, body)
        g.line(cx - 14f, cy - 3f, cx - 18f, cy - 9f, 1.4f, body)
        g.line(cx - 11f, cy - 3f, cx - 9f, cy - 9f, 1.4f, body)
        g.circle(cx - 18f, cy - 10f, 1.6f, body); g.circle(cx - 9f, cy - 10f, 1.6f, body)
        g.circle(cx + 3f, cy - 2f, 9.5f, color)
        g.ring(cx + 3f, cy - 2f, 6f, 1.6f, darker(color, 0.6f))
        g.ring(cx + 4f, cy - 1.5f, 2.5f, 1.6f, darker(color, 0.6f))
    }

    private fun iconPause(g: Gfx, cx: Float, cy: Float, color: Int) {
        g.roundRect(cx - 9f, cy - 11f, cx - 3f, cy + 11f, 1.5f, color)
        g.roundRect(cx + 3f, cy - 11f, cx + 9f, cy + 11f, 1.5f, color)
    }

    // ---- HUD ----


    private fun progress(g: Gfx, l: Float, r: Float, y: Float) {
        val frac = (e.levelTime / e.rules.levelSeconds).coerceIn(0f, 1f)
        g.roundRect(l, y, r, y + 5f, 2.5f, rgb(40, 52, 74))
        if (frac > 0f) g.roundRect(l, y, l + (r - l) * frac, y + 5f, 2.5f, if (e.slowLeft > 0f) rgb(140, 170, 255) else Col.TEAL)
    }

    private fun timeText(): String { val s = ceil(e.timeRemaining()).toInt(); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
    private fun basketText() = "${e.speedLetter()} ${e.sizeLevel + 1}/${Engine.SIZE_STEPS + 1}"
    private fun showMf() = e.rules.showMf && (e.mode == Mode.PLAYING || e.mode == Mode.PAUSED || (e.midLevelShop && e.mode != Mode.GAME_OVER))

    private fun hearts(g: Gfx, x0: Float, y: Float, step: Float, s: Float = 1f) {
        for (i in 0 until e.maxLives()) heart(g, x0 + i * step, y, if (i < e.lives) Col.HEART else Col.HEART_OFF, s)
    }

    /** The left panel: level, money, lives, gems, time and basket. */
    private fun hud(g: Gfx) {
        val c = px0 / 2
        val l = max(8f, c - 56f)
        flag(g, l + 2f, 26f, 1.3f, Col.GOLD)
        g.text("${e.shownLevel()}", l + 24f, 33f, 22f, Col.GOLD, -1)
        g.text("$", l, 64f, 20f, Col.GOLD, -1)
        g.text("${e.balance}", l + 16f, 64f, 20f, Col.WHITE, -1)
        hearts(g, l + 7f, 88f, 17f)
        gem(g, l + 8f, 118f, 0.9f, e.clock)
        g.text("${e.gems}", l + 22f, 125f, 18f, Col.WHITE, -1)
        progress(g, l, min(px0 - 8f, l + 112f), 144f)
        g.text(timeText(), l, 166f, 12f, Col.SOFT, -1)
        g.text(basketText(), l, 186f, 13f, Col.SOFT, -1)
        if (showMf()) g.text("MF ${e.effectiveMf().roundToInt()}", l, 204f, 11f, Col.SOFT, -1)
    }

    // ---- Buttons during play ----

    private fun controls(g: Gfx) {
        val noteText = when {
            e.slowLeft > 0f -> T.f("play.slow", ceil(e.slowLeft).toInt())
            e.level == 1 && e.levelTime < 8f -> T.s("play.tip")
            else -> e.note
        }
        g.fitText(noteText, cx, 14f, 11f, Col.NOTE, W - 16f)
        val x = px0 + W + 8f
        var y = 24f
        for (kind in 0..2) { iconButton(g, kind, x, y, true); y += BTN + 10f }
    }

    /** The right panel outside play: the same buttons, greyed out, so the layout does not jump. */
    private fun idleButtons(g: Gfx) {
        val x = px0 + W + 8f
        var y = 24f
        for (kind in 0..2) { iconButton(g, kind, x, y, false); y += BTN + 10f }
    }

    /** kind 0 = net, 1 = slow motion, 2 = pause. Power-ups show their price as small gems. */
    private fun iconButton(g: Gfx, kind: Int, x: Float, y: Float, playing: Boolean) {
        val enabled = playing && when (kind) { 0 -> e.gems >= 1 && e.netLeft <= 0f; 1 -> e.gems >= 1 && e.slowLeft <= 0f; else -> true }
        val id = when (kind) { 0 -> "net"; 1 -> "slow"; else -> "pause" }
        g.roundRect(x, y, x + BTN, y + BTN, 12f, if (enabled) Col.BUTTON else Col.BUTTON_OFF)
        val c = if (enabled) Col.WHITE else rgb(150, 156, 170)
        val icy = y + if (kind == 2) BTN / 2 else BTN / 2 - 6f
        when (kind) {
            0 -> iconNet(g, x + BTN / 2, icy, c)
            1 -> iconSnail(g, x + BTN / 2, icy, if (enabled) rgb(255, 200, 110) else c)
            else -> iconPause(g, x + BTN / 2, icy, c)
        }
        if (kind < 2) {
            val left = if (kind == 0) e.netLeft else e.slowLeft
            if (left > 0f) g.text("${ceil(left).toInt()}", x + BTN / 2, y + BTN - 6f, 11f, c)
            else gem(g, x + BTN / 2, y + BTN - 10f, 0.45f, 0f)
        }
        if (enabled) buttons.add(Button(x, y, x + BTN, y + BTN, id))
    }

    private fun button(
        g: Gfx, label: String, l: Float, t: Float, r: Float, b: Float, enabled: Boolean, id: String,
        arg: Int = 0, sub: String? = null, size: Float = 16f,
    ) {
        g.roundRect(l, t, r, b, 12f, if (enabled) Col.BUTTON else Col.BUTTON_OFF)
        val c = if (enabled) Col.WHITE else rgb(150, 156, 170)
        val mx = (l + r) / 2
        val my = (t + b) / 2
        if (sub == null) g.fitText(label, mx, my + size * 0.36f, size, c, r - l - 12f)
        else {
            g.fitText(label, mx, my - 1f, size, c, r - l - 12f)
            g.fitText(sub, mx, my + 14f, 11.5f, if (enabled) rgb(200, 230, 235) else c, r - l - 12f)
        }
        if (enabled) buttons.add(Button(l, t, r, b, id, arg))
    }

    private fun overlay(g: Gfx) = g.rect(0f, 0f, vw, vh, Col.OVERLAY)

    private fun noteLines(g: Gfx, text: String, y: Float, width: Float, color: Int = Col.NOTE): Float {
        var yy = y
        for (line in g.wrap(text, 13f, width).take(3)) { g.text(line, vw / 2, yy, 13f, color); yy += 17f }
        return yy
    }

    private fun banner(g: Gfx) {
        if (e.banner <= 0f) return
        val a = (min(1f, e.banner / 0.6f) * 255).toInt()
        g.text(T.f("level", e.level), cx, (e.spawnY + e.ground) * 0.45f, 36f, alpha(Col.GOLD, a))
    }

    private fun freeze(g: Gfx) {
        if (e.freezeLeft <= 0f) return
        val y = (e.spawnY + e.ground) * 0.45f
        g.text("${ceil(e.freezeLeft).toInt()}", cx, y, 48f, Col.GOLD)
        g.fitText(T.s("play.wait"), cx, y + 30f, 14f, Col.WHITE, W - 16f)
    }

    // ---- Losing a life: half a second with the missed coin marked, then half a second with a sad face ----

    private fun lost(g: Gfx) {
        if (e.lostTime < Engine.LOST_DELAY) {
            val a = 40 + (80 * (1f - e.lostTime / Engine.LOST_DELAY)).toInt()
            g.rect(px0, 0f, px0 + W, vh, argb(a, 200, 30, 40))
            for (d in e.active) if (d.state == 2 && d.kind == Kind.COIN) g.ring(px0 + d.x(d.land), min(e.y(d), e.rim), 16f, 3f, Col.ALERT)
        } else {
            overlay(g)
            sadFace(g, vw / 2, vh / 2, min(90f, vh * 0.28f))
        }
    }

    private fun sadFace(g: Gfx, x: Float, y: Float, r: Float) {
        val brown = rgb(90, 55, 20)
        g.circle(x, y, r, rgb(240, 180, 40))
        g.circle(x, y - r * 0.03f, r * 0.95f, rgb(255, 208, 70))
        g.ellipse(x - r * 0.34f, y - r * 0.12f, r * 0.09f, r * 0.15f, brown)
        g.ellipse(x + r * 0.34f, y - r * 0.12f, r * 0.09f, r * 0.15f, brown)
        g.line(x - r * 0.55f, y - r * 0.38f, x - r * 0.2f, y - r * 0.52f, r * 0.06f, brown)
        g.line(x + r * 0.55f, y - r * 0.38f, x + r * 0.2f, y - r * 0.52f, r * 0.06f, brown)
        var px = x - r * 0.38f
        var py = y + r * 0.55f
        for (i in 1..12) {
            val t = -1f + 2f * i / 12f
            val nx = x + t * r * 0.38f
            val ny = y + r * 0.55f - (1f - t * t) * r * 0.18f
            g.line(px, py, nx, ny, r * 0.07f, brown)
            px = nx; py = ny
        }
        val tx = x - r * 0.36f
        val ty = y + r * 0.22f
        g.poly(floatArrayOf(tx, ty - r * 0.16f, tx + r * 0.08f, ty, tx - r * 0.08f, ty), rgb(90, 170, 240))
        g.circle(tx, ty + r * 0.02f, r * 0.085f, rgb(90, 170, 240))
    }

    // ---- Overlays. A "stack" of buttons in two columns. ----

    private fun stack(g: Gfx, y0: Float, rows: List<Triple<String, Boolean, Pair<String, Int>>>, subs: List<String?>, maxBottom: Float): Float {
        val cols = 2
        val bw = min(250f, (vw - 60f) / 2)
        val n = rows.size
        val lines = (n + cols - 1) / cols
        var step = 50f
        step = min(step, (maxBottom - y0) / max(1, lines))
        val bh = step - 8f
        rows.forEachIndexed { i, (label, enabled, idArg) ->
            val col = i % cols; val line = i / cols
            val x = vw / 2 - bw - 10f + col * (bw + 20f)
            val y = y0 + line * step
            button(g, label, x, y, x + bw, y + bh, enabled, idArg.first, idArg.second, subs[i])
        }
        return y0 + lines * step
    }

    private fun menu(g: Gfx) {
        g.rect(0f, 0f, vw, e.ground - 70f, argb(200, 7, 13, 29))
        val top = 34f
        g.text("THINKCLINK", vw / 2, top, 32f, Col.GOLD)
        g.fitText(T.s("menu.tagline"), vw / 2, top + 28f, 14f, Col.WHITE, vw - 20f)
        g.text(T.f("menu.record", e.high), vw / 2, top + 50f, 13f, Col.SOFT)
        val rows = ArrayList<Triple<String, Boolean, Pair<String, Int>>>()
        val subs = ArrayList<String?>()
        if (savedLevel > 0) { rows.add(Triple(T.s("menu.continue"), true, "continue" to 0)); subs.add(T.f("menu.level", savedLevel)) }
        rows.add(Triple(T.s("menu.new"), true, "new" to 0)); subs.add(null)
        rows.add(Triple(T.s("menu.settings"), true, "settings" to 0)); subs.add(null)
        rows.add(Triple(T.s("menu.test"), true, "test" to 0)); subs.add(null)
        var y = stack(g, top + 72f, rows, subs, e.ground - 90f)
        y += 14f
        for (line in info) { g.fitText(line, vw / 2, y, 11f, Col.SOFT, vw - 20f); y += 15f }
    }

    private fun pause(g: Gfx) {
        overlay(g)
        val top = 40f
        g.text(T.s("pause.title"), vw / 2, top, 26f, Col.WHITE)
        stack(g, top + 30f, listOf(
            Triple(T.s("pause.resume"), true, "resume" to 0),
            Triple(T.s("menu.settings"), true, "settings" to 0),
            Triple(T.s("menu.test"), true, "test" to 0),
            Triple(T.s("pause.menu"), true, "menu" to 0),
        ), listOf(null, null, null, T.s("pause.menuSub")), vh - 10f)
    }

    private fun shop(g: Gfx) {
        overlay(g)
        val top = 4f
        val mid = e.midLevelShop
        g.text(if (mid) T.s("shop.lostLife") else T.f("shop.levelDone", e.finishedLevel), vw / 2, top + 24f, 21f, if (mid) Col.ALERT else Col.GOLD)
        g.text("$ ${e.balance}  ·  ♥ ${e.lives}/${e.maxLives()}  ·  ◆ ${e.gems}  ·  " + T.f("shop.basket", e.speedLetter(), e.sizeLevel + 1, Engine.SIZE_STEPS + 1),
            vw / 2, top + 48f, 13f, Col.WHITE)
        val y0 = noteLines(g, e.note, top + 70f, vw - 40f) + 10f
        val rows = ArrayList<Triple<String, Boolean, Pair<String, Int>>>()
        val subs = ArrayList<String?>()
        if (e.mathMenu) {
            for ((i, key) in listOf("shop.easy", "shop.medium", "shop.hard").withIndex()) { rows.add(Triple(T.s(key), true, "mathMoney" to i)); subs.add(T.f("shop.reward", e.mathReward(i))) }
            rows.add(Triple(T.s("shop.back"), true, "mathBack" to 0)); subs.add(null)
            stack(g, y0, rows, subs, vh - 8f)
            return
        }
        rows.add(Triple(T.s("shop.speed"), e.canBuySpeed(), "speed" to 0))
        subs.add(if (e.speedLevel >= Engine.MAX_SPEED_LEVEL) T.s("shop.speedMax") else T.f("shop.speedPrice", e.speedPrice()))
        rows.add(Triple(T.s("shop.size"), e.canBuySize(), "size" to 0))
        subs.add(if (e.sizeLevel >= Engine.SIZE_STEPS) T.s("shop.sizeMax") else T.f("shop.sizePrice", e.sizePrice()))
        rows.add(Triple(T.s("shop.life"), e.canBuyLife(), "life" to 0))
        subs.add(when {
            e.lives >= e.maxLives() -> T.f("shop.lifeMax", e.maxLives())
            e.livesBought >= Engine.LIVES_PER_VISIT -> T.f("shop.lifePerVisit", Engine.LIVES_PER_VISIT)
            else -> T.f("shop.lifePrice", e.lifePrice())
        })
        if (mid) {
            rows.add(Triple(T.s("shop.mathLife"), e.canMathLife(), "mathLife" to 0))
            subs.add(when {
                e.lifeMathDone -> T.s("shop.done")
                e.lives >= e.maxLives() -> T.f("shop.lifeMax", e.maxLives())
                e.lifeMathLeft <= 0 -> T.s("shop.noQuestions")
                else -> T.f("shop.lifeSub", e.lifeMathLeft)
            })
        } else {
            rows.add(Triple(T.s("shop.mathMoney"), e.moneyMathLeft > 0, "mathMenu" to 0))
            subs.add(T.f("shop.questionsLeft", e.moneyMathLeft))
        }
        if (!mid) { rows.add(Triple(T.s("menu.settings"), true, "settings" to 0)); subs.add(null) }
        val gate = if (mid) 0 else e.gateRemaining()
        rows.add(Triple(if (mid) T.s("shop.continue") else T.f("shop.start", e.level), true, "leave" to 0))
        subs.add(when { gate == 1 -> T.s("shop.gate1"); gate > 1 -> T.f("shop.gateN", gate); else -> null })
        stack(g, y0, rows, subs, vh - 8f)
    }

    private fun question(g: Gfx) {
        val q = e.question ?: return
        overlay(g)
        val (title, sub) = when (e.purpose) {
            Purpose.SPEED -> T.s("q.speedTitle") to T.f("q.stake", e.stake)
            Purpose.MONEY -> T.s("q.moneyTitle") to T.f("q.reward", e.reward)
            Purpose.LIFE -> T.s("q.lifeTitle") to T.s("q.lifeSub")
            Purpose.GATE -> T.f("q.gateTitle", e.level) to
                if (e.rules.gateQuestions > 1) T.f("q.gateSubN", e.gateCorrect + 1, e.rules.gateQuestions) else T.s("q.gateSub")
        }
        // An image gets its own column to the left of the text.
        val size = q.imageUrl?.let { g.imageSize(it) }?.takeIf { it[0] > 0f && it[1] > 0f }
        var left = 20f
        val right = vw - 20f
        if (size != null) {
            var imgH = min(vh - 40f, 240f)
            var imgW = imgH * size[0] / size[1]
            if (imgW > vw * 0.38f) { imgW = vw * 0.38f; imgH = imgW * size[1] / size[0] }
            left = 30f + imgW
            g.roundRect(17f, (vh - imgH) / 2 - 3f, 23f + imgW, (vh + imgH) / 2 + 3f, 6f, rgb(40, 50, 70))
            g.image(q.imageUrl!!, 20f, (vh - imgH) / 2, 20f + imgW, (vh + imgH) / 2)
        }
        val colW = right - left
        val mx = (left + right) / 2
        val isBank = q.bankId != null
        fun linesFor(sz: Float) = if (isBank) g.wrap(q.text, sz, colW - 10f).take(5) else listOf(q.text)
        var textSize = if (isBank) 17f else 28f
        var lines = linesFor(textSize)
        if (isBank && lines.size > 3) { textSize = 14f; lines = linesFor(textSize) }
        // Answers in three columns when they are short, otherwise two (long answers shrink to fit).
        val optCols = if (q.options.size % 3 == 0 && q.options.all { g.textWidth(it, 19f) <= colW / 3 - 24f }) 3 else 2
        val rows = (q.options.size + optCols - 1) / optCols
        val lineH = textSize * 1.25f
        val total = 70f + lines.size * lineH + 42f + rows * 44f
        var y = max(4f, (vh - total) / 2)
        g.fitText(title, mx, y + 20f, 18f, Col.WHITE, colW)
        g.fitText(sub, mx, y + 42f, 13f, Col.SOFT, colW)
        g.text(q.subjectName, mx, y + 62f, 11f, Col.TEAL)
        y += 70f
        for (line in lines) { y += lineH; g.fitText(line, mx, y - lineH * 0.22f, textSize, Col.GOLD, colW) }
        y += 18f
        g.text(T.f("q.timeLeft", e.timeLeft), mx, y, 13f, Col.WHITE)
        g.roundRect(left + 10f, y + 8f, right - 10f, y + 13f, 2.5f, rgb(60, 66, 80))
        g.roundRect(left + 10f, y + 8f, left + 10f + (colW - 20f) * (e.timeLeft / e.timeTotal), y + 13f, 2.5f, Col.TEAL)
        y += 24f
        val step = min(44f, (vh - 6f - y) / rows)
        val gap = 10f
        val ow = (colW - gap * (optCols - 1)) / optCols
        q.options.forEachIndexed { i, option ->
            val x = left + (i % optCols) * (ow + gap)
            val yy = y + (i / optCols) * step
            button(g, option, x, yy, x + ow, yy + step - 8f, true, "answer", i, size = 18f)
        }
    }

    private fun gameOver(g: Gfx) {
        overlay(g)
        val top = 40f
        g.text(T.s("over.title"), vw / 2, top, 28f, Col.ALERT)
        g.text(T.f("over.reached", e.finishedLevel), vw / 2, top + 32f, 16f, Col.WHITE)
        g.text(T.f("over.caught", e.caught, e.high), vw / 2, top + 58f, 15f, Col.SOFT)
        stack(g, top + 86f, listOf(Triple(T.s("menu.new"), true, "new" to 0), Triple(T.s("pause.menu"), true, "menu" to 0)), listOf(null, null), vh - 10f)
    }
}
