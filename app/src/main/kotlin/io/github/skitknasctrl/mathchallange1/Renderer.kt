package io.github.skitknasctrl.mathchallange1

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class Button(val l: Float, val t: Float, val r: Float, val b: Float, val id: String, val arg: Int = 0) {
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/**
 * Draws the whole game in game points (360 wide, worldHeight high) and records where the buttons are.
 * Layout from the top: HUD 0–122, falling area, basket rim at worldHeight − 157, ground at
 * worldHeight − 119, note line, buttons between worldHeight − 78 and worldHeight − 32.
 * The view itself is placed inside the system bars, so nothing is drawn under the status bar.
 */
class Renderer(private val e: Engine) {
    val buttons = ArrayList<Button>()
    /** Level of the saved game shown on the start screen, 0 if there is none. */
    var savedLevel = 0

    fun zoneAt(x: Float, y: Float): Int {
        val h = e.worldHeight
        if (y < h * 0.25f || y > h * 0.75f) return 0
        return if (x <= 36f) -1 else if (x >= 324f) 1 else 0
    }

    fun draw(g: Gfx) {
        buttons.clear()
        val h = e.worldHeight
        background(g, h)
        if (e.mode != Mode.MENU) hud(g)
        if (e.mode == Mode.PLAYING) zones(g, h)
        drops(g)
        scenery(g)
        labels(g)
        if (e.mode == Mode.PLAYING) controls(g, h)
        when (e.mode) {
            Mode.MENU -> menu(g, h)
            Mode.PAUSED -> pause(g, h)
            Mode.SHOP -> shop(g, h)
            Mode.QUESTION -> question(g, h)
            Mode.GAME_OVER -> gameOver(g, h)
            Mode.PLAYING -> banner(g, h)
        }
    }

    // ---- Pieces ----

    private fun background(g: Gfx, h: Float) {
        g.gradientRect(0f, 0f, Engine.W, h, rgb(10, 18, 39), rgb(28, 45, 70))
        val span = max(1, (e.ground - 140f).toInt())
        for (i in 0..35) g.circle(((i * 83 + i * i * 37) % 360).toFloat(), (130 + (i * 197 + i * i * 31) % span).toFloat(), 1.5f, rgb(45, 63, 88))
        val gy = e.ground
        g.rect(0f, gy, Engine.W, h, rgb(20, 26, 38))
        g.rect(0f, gy, Engine.W, gy + 5f, rgb(86, 66, 44))
    }

    private fun hud(g: Gfx) {
        g.text("NIVÅ ${e.shownLevel()}", 180f, 30f, 20f, Col.GOLD)
        g.text("Mynt: ${e.balance}     Fångat: ${e.caught}", 180f, 56f, 16f, Col.WHITE)
        val maxLives = e.maxLives()
        if (maxLives <= 9) {
            for (i in 0 until maxLives) heart(g, 22f + i * 16f, 79f, if (i < e.lives) Col.HEART else Col.HEART_OFF)
        } else {
            heart(g, 22f, 79f, Col.HEART); g.text("${e.lives}", 34f, 84f, 14f, Col.WHITE, -1)
        }
        gem(g, 306f, 78f, 0.8f, e.clock)
        g.text("${e.gems}", 318f, 84f, 15f, Col.WHITE, -1)
        if (e.rules.showMf && (e.mode == Mode.PLAYING || e.mode == Mode.PAUSED || (e.midLevelShop && e.mode != Mode.GAME_OVER))) {
            g.text("MF ${e.effectiveMf().roundToInt()}", 214f, 84f, 12f, Col.SOFT)
        }
        val frac = (e.levelTime / e.rules.levelSeconds).coerceIn(0f, 1f)
        g.roundRect(16f, 96f, 344f, 101f, 2.5f, rgb(40, 52, 74))
        if (frac > 0f) g.roundRect(16f, 96f, 16f + 328f * frac, 101f, 2.5f, if (e.slowLeft > 0f) rgb(140, 170, 255) else Col.TEAL)
        val s = ceil(e.timeRemaining()).toInt()
        g.text("${s / 60}:${(s % 60).toString().padStart(2, '0')}", 344f, 116f, 11f, Col.SOFT, 1)
        g.text("Fart ${e.speedLevel + 1}/${Draggers.STEPS}   Korg ${e.sizeLevel + 1}/${Engine.SIZE_STEPS + 1}", 16f, 116f, 11f, Col.SOFT, -1)
    }

    private fun heart(g: Gfx, cx: Float, cy: Float, color: Int) {
        g.circle(cx - 3.3f, cy - 2f, 4f, color)
        g.circle(cx + 3.3f, cy - 2f, 4f, color)
        g.poly(floatArrayOf(cx - 7.1f, cy - 0.8f, cx + 7.1f, cy - 0.8f, cx, cy + 7f), color)
    }

    private fun gem(g: Gfx, cx: Float, cy: Float, s: Float, time: Float) {
        g.poly(floatArrayOf(cx, cy - 11f * s, cx + 9f * s, cy - 3f * s, cx, cy + 11f * s, cx - 9f * s, cy - 3f * s), rgb(40, 150, 210))
        g.poly(floatArrayOf(cx - 9f * s, cy - 3f * s, cx, cy - 11f * s, cx + 9f * s, cy - 3f * s, cx, cy), rgb(110, 215, 245))
        g.poly(floatArrayOf(cx - 4f * s, cy - 3f * s, cx, cy - 9f * s, cx + 1f * s, cy - 3f * s), rgb(220, 250, 255))
        val tw = (sin(time * 5f) + 1f) / 2f
        g.line(cx + 6f * s, cy - 10f * s, cx + 6f * s, cy - (10f - 6f * tw) * s - 6f * s, 1.2f, alpha(Col.WHITE, (200 * tw).toInt()))
    }

    private fun zones(g: Gfx, h: Float) {
        val top = h * 0.25f
        val bottom = h * 0.75f
        for (side in intArrayOf(-1, 1)) {
            val l = if (side < 0) 0f else 324f
            val held = if (side < 0) e.heldLeft else e.heldRight
            g.rect(l, top, l + 36f, bottom, argb(if (held) 90 else 34, 255, 255, 255))
            val cx = l + 18f
            val cy = (top + bottom) / 2
            g.poly(floatArrayOf(cx + side * 10f, cy, cx - side * 8f, cy - 14f, cx - side * 8f, cy + 14f), argb(if (held) 220 else 110, 255, 255, 255))
        }
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
        g.poly(floatArrayOf(cx - hw, rim, cx + hw, rim, cx + bw, rim + 38f, cx - bw, rim + 38f), body)
        val weave = darker(body)
        for (k in 1..3) {
            val yy = rim + k * 9.5f
            val half = hw + (bw - hw) * (k * 9.5f / 38f)
            g.line(cx - half + 1.5f, yy, cx + half - 1.5f, yy, 1.4f, weave)
        }
        for (k in -2..2) g.line(cx + k * hw * 0.4f, rim + 3f, cx + k * bw * 0.4f, rim + 36f, 1.2f, weave)
        g.roundRect(cx - hw - 3f, rim - 2f, cx + hw + 3f, rim + 5f, 3f, band)
    }

    private fun scenery(g: Gfx) {
        val bw = e.basketWidth()
        val left = e.basketX - bw / 2
        val right = e.basketX + bw / 2
        val moving = e.mode == Mode.PLAYING && e.moveDir != 0
        val step = e.speedLevel + 1
        if (e.groundLeft > 0f && e.mode != Mode.MENU) {
            basket(g, e.groundX, e.rim, bw, rgb(120, 150, 70), rgb(185, 215, 120))
            g.text("${ceil(e.groundLeft).toInt()} s", e.groundX, e.rim - 8f, 11f, rgb(200, 235, 150))
        }
        Draggers.drawBehind(g, step, left, right, e.ground, e.facing, e.travelled, moving, e.clock)
        basket(g, e.basketX, e.rim, bw)
        Draggers.drawFront(g, step, left, right, e.ground, e.facing, moving, e.clock)
    }

    private fun labels(g: Gfx) {
        for (l in e.labels) g.text(l.text, l.x, l.y - l.age * 34f, 13f, alpha(l.color, (255 * (1f - l.age / 0.9f)).toInt()))
    }

    private fun controls(g: Gfx, h: Float) {
        val noteText = when {
            e.slowLeft > 0f -> "Slowmotion ${ceil(e.slowLeft).toInt()} s"
            e.level == 1 && e.levelTime < 8f -> "Håll fingret i kantfälten för att flytta korgen"
            else -> e.note
        }
        g.fitText(noteText, 180f, h - 94f, 12f, Col.NOTE, 340f)
        val gems = e.gems >= 1
        button(g, "Markkorg", 8f, h - 78f, 120f, h - 32f, gems && e.groundLeft <= 0f, "ground",
            sub = if (e.groundLeft > 0f) "${ceil(e.groundLeft).toInt()} s kvar" else "1 sten")
        button(g, "Slowmo", 124f, h - 78f, 236f, h - 32f, gems && e.slowLeft <= 0f, "slow",
            sub = if (e.slowLeft > 0f) "${ceil(e.slowLeft).toInt()} s kvar" else "1 sten")
        button(g, "Paus", 240f, h - 78f, 352f, h - 32f, true, "pause")
    }

    private fun button(
        g: Gfx, label: String, l: Float, t: Float, r: Float, b: Float, enabled: Boolean, id: String,
        arg: Int = 0, sub: String? = null, size: Float = 16f,
    ) {
        g.roundRect(l, t, r, b, 12f, if (enabled) Col.BUTTON else Col.BUTTON_OFF)
        val c = if (enabled) Col.WHITE else rgb(150, 156, 170)
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        if (sub == null) g.fitText(label, cx, cy + size * 0.36f, size, c, r - l - 12f)
        else {
            g.fitText(label, cx, cy - 1f, size, c, r - l - 12f)
            g.fitText(sub, cx, cy + 14f, 11.5f, if (enabled) rgb(200, 230, 235) else c, r - l - 12f)
        }
        if (enabled) buttons.add(Button(l, t, r, b, id, arg))
    }

    private fun overlay(g: Gfx, h: Float, from: Float = 122f) = g.rect(0f, from, Engine.W, h, Col.OVERLAY)

    private fun noteLines(g: Gfx, text: String, y: Float, color: Int = Col.NOTE): Float {
        var yy = y
        for (line in g.wrap(text, 13f, 320f).take(3)) { g.text(line, 180f, yy, 13f, color); yy += 17f }
        return yy
    }

    private fun banner(g: Gfx, h: Float) {
        if (e.banner <= 0f) return
        val a = (min(1f, e.banner / 0.6f) * 255).toInt()
        g.text("NIVÅ ${e.level}", 180f, h * 0.4f, 36f, alpha(Col.GOLD, a))
    }

    private fun menu(g: Gfx, h: Float) {
        g.rect(0f, 0f, Engine.W, e.ground - 70f, argb(200, 7, 13, 29))
        val top = max(40f, h * 0.14f)
        g.text("MYNTKORGEN", 180f, top, 32f, Col.GOLD)
        g.text("Fånga mynt. Räkna dig till fart.", 180f, top + 30f, 14f, Col.WHITE)
        g.text("Rekord: ${e.high} fångade", 180f, top + 54f, 13f, Col.SOFT)
        var y = top + 90f
        if (savedLevel > 0) { button(g, "Fortsätt", 55f, y, 305f, y + 50f, true, "continue", sub = "nivå $savedLevel"); y += 62f }
        button(g, "Nytt spel", 55f, y, 305f, y + 50f, true, "new"); y += 62f
        button(g, "Testinställningar", 55f, y, 305f, y + 50f, true, "settings")
    }

    private fun pause(g: Gfx, h: Float) {
        overlay(g, h)
        val mid = (h + 122f) / 2
        g.text("PAUS", 180f, mid - 110f, 26f, Col.WHITE)
        button(g, "Fortsätt", 55f, mid - 80f, 305f, mid - 32f, true, "resume")
        button(g, "Testinställningar", 55f, mid - 20f, 305f, mid + 28f, true, "settings")
        button(g, "Till menyn", 55f, mid + 40f, 305f, mid + 88f, true, "menu", sub = "nivån börjar om nästa gång")
    }

    private fun shop(g: Gfx, h: Float) {
        overlay(g, h)
        val top = 132f
        val mid = e.midLevelShop
        g.text(if (mid) "DU TAPPADE ETT LIV" else "NIVÅ ${e.finishedLevel} KLAR", 180f, top + 26f, 22f, if (mid) Col.ALERT else Col.GOLD)
        g.text("Mynt ${e.balance}  ·  Liv ${e.lives}/${e.maxLives()}  ·  Stenar ${e.gems}", 180f, top + 52f, 14f, Col.WHITE)
        g.fitText("Dragare: ${Draggers.name(e.speedLevel + 1)}  ·  Korg ${e.sizeLevel + 1}/${Engine.SIZE_STEPS + 1}", 180f, top + 73f, 13f, Col.SOFT, 330f)
        noteLines(g, e.note, top + 97f)
        val y0 = top + 140f
        val rows = if (e.mathMenu) 4 else 5
        val step = min(56f, (h - 24f - y0) / rows)
        val bh = step - 8f
        var y = y0
        fun row(label: String, enabled: Boolean, id: String, sub: String?, arg: Int = 0) {
            button(g, label, 45f, y, 315f, y + bh, enabled, id, arg, sub); y += step
        }
        if (e.mathMenu) {
            row("Lätt", true, "mathMoney", "+${e.mathReward(0)} mynt", 0)
            row("Medel", true, "mathMoney", "+${e.mathReward(1)} mynt", 1)
            row("Svår", true, "mathMoney", "+${e.mathReward(2)} mynt", 2)
            row("Tillbaka", true, "mathBack", null)
            return
        }
        row("Snabbare korg", e.canBuySpeed(), "speed",
            if (e.speedLevel >= Engine.MAX_SPEED_LEVEL) "högsta farten" else "${e.speedPrice()} mynt · mattefråga")
        row("Större korg", e.canBuySize(), "size",
            if (e.sizeLevel >= Engine.SIZE_STEPS) "största korgen" else "${e.sizePrice()} stenar")
        row("Köp liv", e.canBuyLife(), "life", when {
            e.lives >= e.maxLives() -> "max ${e.maxLives()} liv"
            e.livesBought >= Engine.LIVES_PER_VISIT -> "max ${Engine.LIVES_PER_VISIT} per besök"
            else -> "${e.lifePrice()} mynt"
        })
        if (mid) row("Räkna för liv", e.canMathLife(), "mathLife", when {
            e.lifeMathDone -> "klart"
            e.lives >= e.maxLives() -> "max ${e.maxLives()} liv"
            e.lifeMathLeft <= 0 -> "inga frågor kvar"
            else -> "1 rätt räcker · ${e.lifeMathLeft} frågor kvar"
        })
        else row("Räkna för pengar", e.moneyMathLeft > 0, "mathMenu", "${e.moneyMathLeft} frågor kvar")
        row(if (mid) "Fortsätt nivån" else "Starta nivå ${e.level}", true, "leave", null)
    }

    private fun question(g: Gfx, h: Float) {
        val q = e.question ?: return
        overlay(g, h)
        val top = max(130f, (h - 360f) / 2)
        val (title, sub) = when (e.purpose) {
            Purpose.SPEED -> "KÖP SNABBARE KORG" to "Insats: ${e.stake} mynt"
            Purpose.MONEY -> "RÄKNA FÖR PENGAR" to "Rätt svar ger ${e.reward} mynt"
            Purpose.LIFE -> "RÄKNA FÖR LIV" to "Rätt svar ger ett liv"
        }
        g.text(title, 180f, top + 25f, 21f, Col.WHITE)
        g.text(sub, 180f, top + 52f, 15f, Col.SOFT)
        g.fitText("${q.text} = ?", 180f, top + 104f, 34f, Col.GOLD, 330f)
        g.text(String.format(java.util.Locale.forLanguageTag("sv-SE"), "%.1f sekunder kvar", e.timeLeft), 180f, top + 140f, 17f, Col.WHITE)
        g.roundRect(30f, top + 155f, 330f, top + 162f, 3.5f, rgb(60, 66, 80))
        g.roundRect(30f, top + 155f, 30f + 300f * (e.timeLeft / e.timeTotal), top + 162f, 3.5f, Col.TEAL)
        q.options.forEachIndexed { i, option ->
            val x = 25f + (i % 2) * 160f
            val y = top + 182f + (i / 2) * 60f
            button(g, option.toString(), x, y, x + 150f, y + 50f, true, "answer", option, size = 20f)
        }
    }

    private fun gameOver(g: Gfx, h: Float) {
        overlay(g, h)
        val mid = (h + 122f) / 2
        g.text("SPELET SLUT", 180f, mid - 120f, 28f, Col.ALERT)
        g.text("Du kom till nivå ${e.finishedLevel}", 180f, mid - 88f, 16f, Col.WHITE)
        g.text("Fångat: ${e.caught}     Rekord: ${e.high}", 180f, mid - 62f, 15f, Col.SOFT)
        button(g, "Nytt spel", 55f, mid - 30f, 305f, mid + 20f, true, "new")
        button(g, "Till menyn", 55f, mid + 32f, 305f, mid + 82f, true, "menu")
    }
}
