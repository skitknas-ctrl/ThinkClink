package io.github.skitknasctrl.mathchallange1

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The figures and vehicles that move the basket, one per bought speed step (31 steps).
 * Steps 1–26 are 13 draggers in two variants each; the second variant carries a gold star.
 * Steps 27–31 are jet engines on the basket in five sizes.
 *
 * Draggers 0–8 stand to the left of the basket. They are drawn facing right with their front at
 * local x = 0 and their body at negative x; when the basket moves left the drawing is mirrored so
 * the dragger faces left and tows the basket. Trains have a locomotive at each end.
 */
object Draggers {
    const val STEPS = 31
    private const val PAIRED = 26
    private val names = listOf(
        "Gubbe", "Starkare gubbe", "Muskelgubbe", "Lastcykel", "Åsna", "Oxe", "Motorcykel",
        "Traktor", "Lastbil", "Litet ånglok", "Stort ånglok", "Ellok", "X2000",
    )
    private val unitLength = floatArrayOf(28f, 28f, 30f, 58f, 54f, 64f, 60f, 66f, 86f)
    private val unitHeight = floatArrayOf(48f, 49f, 50f, 50f, 56f, 44f, 52f, 56f, 48f)

    fun name(step: Int): String {
        val s = step.coerceIn(1, STEPS)
        return if (s > PAIRED) "Jetmotorer ${s - PAIRED}" else names[(s - 1) / 2] + if ((s - 1) % 2 == 1) " ★" else ""
    }

    private fun type(step: Int) = if (step > PAIRED) 13 else (step - 1) / 2

    /** Drawn before the basket. */
    fun drawBehind(g: Gfx, step: Int, left: Float, right: Float, ground: Float, facing: Int, travelled: Float, moving: Boolean, time: Float) {
        val s = step.coerceIn(1, STEPS)
        val t = type(s)
        val gold = s <= PAIRED && (s - 1) % 2 == 1
        when {
            t <= 8 -> unit(g, t, left, ground, facing, travelled, moving, time, gold)
            t <= 12 -> train(g, t - 9, left, right, ground, travelled, moving, time, gold)
        }
    }

    /** Drawn after the basket (jet engines sit on its sides). */
    fun drawFront(g: Gfx, step: Int, left: Float, right: Float, ground: Float, facing: Int, moving: Boolean, time: Float) {
        val s = step.coerceIn(1, STEPS)
        if (s > PAIRED) jets(g, s - PAIRED, left, right, ground, facing, moving, time)
    }

    private fun unit(g: Gfx, t: Int, left: Float, ground: Float, facing: Int, travelled: Float, moving: Boolean, time: Float, gold: Boolean) {
        val len = unitLength[t]
        val pull = facing < 0
        g.save()
        if (pull) { g.translate(left - len, ground); g.scale(-1f, 1f) } else g.translate(left, ground)
        when (t) {
            0, 1, 2 -> person(g, t, len, travelled, moving, pull)
            3 -> cargoBike(g, len, travelled, moving, pull)
            4 -> donkey(g, len, travelled, moving, pull)
            5 -> ox(g, len, travelled, moving, pull)
            6 -> motorbike(g, len, travelled, pull)
            7 -> tractor(g, len, travelled, moving, pull, time)
            8 -> truck(g, len, travelled, pull)
        }
        if (gold) g.star(-len / 2, -unitHeight[t] - 9f, 7f, Col.GOLD)
        g.restore()
    }

    // ---- Shared parts ----

    private fun rot(x: Float, y: Float, deg: Float): Pair<Float, Float> {
        val a = deg * PI.toFloat() / 180f
        return (x * cos(a) - y * sin(a)) to (x * sin(a) + y * cos(a))
    }

    private fun wheel(g: Gfx, cx: Float, cy: Float, r: Float, angle: Float, rimColor: Int = Col.STEEL) {
        g.circle(cx, cy, r, rgb(28, 29, 34))
        g.circle(cx, cy, r * 0.62f, rimColor)
        g.circle(cx, cy, r * 0.45f, darker(rimColor, 0.55f))
        for (k in 0 until 3) {
            val a = angle + k * 2.094f
            g.line(cx, cy, cx + r * 0.58f * cos(a), cy + r * 0.58f * sin(a), max(1f, r * 0.14f), rimColor)
        }
        g.circle(cx, cy, max(1.2f, r * 0.16f), rimColor)
    }

    private fun lugWheel(g: Gfx, cx: Float, cy: Float, r: Float, angle: Float) {
        for (k in 0 until 12) {
            val a = angle + k * PI.toFloat() / 6f
            g.line(cx + (r - 1f) * cos(a), cy + (r - 1f) * sin(a), cx + (r + 1.6f) * cos(a), cy + (r + 1.6f) * sin(a), 2.6f, rgb(28, 29, 34))
        }
        wheel(g, cx, cy, r, angle, rgb(235, 190, 40))
    }

    private fun puffs(g: Gfx, x: Float, y: Float, time: Float, moving: Boolean, big: Float = 1f) {
        val speed = if (moving) 1.6f else 0.5f
        for (i in 0 until 4) {
            val age = (time * speed + i * 0.25f) % 1f
            g.circle(x - age * 10f * big, y - age * 22f * big, (2.5f + age * 6f) * big, argb((150 * (1 - age)).toInt(), 225, 225, 230))
        }
    }

    private fun hitch(g: Gfx, len: Float, y: Float) = g.line(-len + 8f, y, -len - 2f, y, 1.6f, Col.ROPE)

    // ---- People ----

    private fun person(g: Gfx, strength: Int, len: Float, travelled: Float, moving: Boolean, pull: Boolean) {
        val cx = -len / 2 - 1f
        val sw = if (moving) sin(travelled / 6f) else 0f
        val shirt = when (strength) { 0 -> rgb(70, 130, 205); 1 -> rgb(205, 82, 60); else -> rgb(56, 160, 92) }
        val pants = rgb(46, 54, 84)
        val legW = 3.2f + strength * 1.2f
        val armW = 2.6f + strength * 1.5f
        val bodyW = 5f + strength * 2.8f
        val lean = if (moving) (if (pull) 16f else 11f) else 3f
        g.save(); g.translate(cx, 0f); g.rotate(lean)
        leg(g, -sw, legW, darker(pants))
        arm(g, pull, armW, darker(Col.SKIN), -1.5f)
        leg(g, sw, legW, pants)
        g.line(0f, -18f, 1f, -32f, bodyW, shirt)
        if (strength == 2) { g.ellipse(2.8f, -27.5f, 4.6f, 4.2f, shirt); g.circle(1f, -31f, 4f, shirt) }
        if (strength == 1) g.circle(1.4f, -30.5f, 3.2f, shirt)
        val hr = 6f + strength * 0.4f
        g.circle(1.8f, -38.5f - hr * 0.3f, hr, Col.SKIN)
        g.ellipse(0.6f, -41.5f - hr * 0.3f, hr, hr * 0.55f, rgb(92, 60, 34))
        g.circle(5.2f, -39f - hr * 0.3f, 0.95f, Col.DARK)
        if (strength == 2) g.line(-4.4f, -42f, 7.5f, -42f, 1.8f, rgb(225, 40, 40))
        arm(g, pull, armW, Col.SKIN, 0f)
        g.restore()
        if (pull) {
            val (hx, hy) = rot(-9f, -24f, lean)
            g.line(cx + hx, hy, -len - 2f, -20f, 1.4f, Col.ROPE)
        }
    }

    private fun leg(g: Gfx, s: Float, w: Float, color: Int) {
        val kx = 4f * s + 1.5f
        val fx = 8f * s
        g.line(0f, -18f, kx, -9f, w, color)
        g.line(kx, -9f, fx, -1.5f, w, color)
        g.line(fx, -1.2f, fx + 3.6f, -1.2f, w * 0.9f, Col.DARK)
    }

    private fun arm(g: Gfx, pull: Boolean, w: Float, color: Int, dx: Float) {
        if (pull) {
            g.line(1f + dx, -30f, -4f + dx, -27f, w, color)
            g.line(-4f + dx, -27f, -9f + dx, -24f, w, color)
        } else {
            g.line(1f + dx, -30f, 7f + dx, -27f, w, color)
            g.line(7f + dx, -27f, 13f + dx, -22f, w, color)
        }
    }

    // ---- Vehicles and animals (front at x = 0, facing right) ----

    private fun cargoBike(g: Gfx, len: Float, travelled: Float, moving: Boolean, pull: Boolean) {
        val frame = rgb(40, 150, 160)
        wheel(g, -48f, -8f, 8f, travelled / 8f)
        wheel(g, -12f, -6f, 6f, travelled / 6f)
        g.roundRect(-32f, -21f, -2f, -9f, 2f, Col.WOOD)
        for (k in 1..3) g.line(-32f + k * 7.5f, -20f, -32f + k * 7.5f, -10f, 1f, darker(Col.WOOD))
        g.line(-48f, -8f, -39f, -24f, 2.6f, frame)
        g.line(-39f, -24f, -34f, -10f, 2.6f, frame)
        g.line(-34f, -10f, -48f, -8f, 2.6f, frame)
        g.line(-34f, -10f, -12f, -7f, 2.6f, frame)
        g.line(-31f, -21f, -29f, -32f, 2.2f, frame)
        g.line(-32f, -32f, -25f, -32f, 2.2f, Col.DARK)
        g.line(-42f, -25.5f, -36f, -25.5f, 3f, Col.DARK)
        val a = travelled / 5f
        val p1x = -34f + 4.5f * cos(a); val p1y = -10f + 4.5f * sin(a)
        val p2x = -34f - 4.5f * cos(a); val p2y = -10f - 4.5f * sin(a)
        val hipX = -39f; val hipY = -28f
        pedalLeg(g, hipX, hipY, p2x, p2y, darker(rgb(46, 54, 84)))
        g.line(hipX, hipY, -34f, -42f, 5f, rgb(240, 170, 40))
        g.circle(-32f, -48.5f, 5.5f, Col.SKIN)
        g.ellipse(-33f, -52f, 5.5f, 3f, rgb(92, 60, 34))
        g.circle(-29f, -49f, 0.9f, Col.DARK)
        g.line(-34f, -40f, -28f, -32f, 2.6f, Col.SKIN)
        pedalLeg(g, hipX, hipY, p1x, p1y, rgb(46, 54, 84))
        if (pull) hitch(g, len, -12f)
    }

    private fun pedalLeg(g: Gfx, hx: Float, hy: Float, px: Float, py: Float, color: Int) {
        val kx = (hx + px) / 2 + 4f
        val ky = (hy + py) / 2 - 3f
        g.line(hx, hy, kx, ky, 3.2f, color)
        g.line(kx, ky, px, py, 3.2f, color)
    }

    private fun animalLeg(g: Gfx, x: Float, top: Float, phase: Float, w: Float, color: Int, moving: Boolean) {
        val s = if (moving) sin(phase) else 0f
        val fx = x + 4.5f * s
        val kx = x + 2.2f * s + 1f
        val ky = top * 0.45f
        g.line(x, top, kx, ky, w, color)
        g.line(kx, ky, fx, -1.5f, w * 0.85f, color)
        g.line(fx - 1.5f, -1f, fx + 1.8f, -1f, w, Col.DARK)
    }

    private fun donkey(g: Gfx, len: Float, travelled: Float, moving: Boolean, pull: Boolean) {
        val body = rgb(140, 124, 108)
        val far = darker(body, 0.8f)
        val ph = travelled / 7f
        animalLeg(g, -14f, -17f, ph + PI.toFloat(), 3.2f, far, moving)
        animalLeg(g, -38f, -17f, ph, 3.2f, far, moving)
        g.line(-45f, -27f, -50f, -15f, 1.8f, darker(body, 0.5f))
        g.circle(-50f, -14f, 2.2f, darker(body, 0.5f))
        g.ellipse(-30f, -24f, 16f, 8.5f, body)
        g.ellipse(-30f, -19.5f, 12f, 4f, rgb(190, 178, 164))
        g.poly(floatArrayOf(-20f, -29f, -14f, -31f, -6f, -41f, -11f, -45f), body)
        g.line(-11.5f, -44f, -19f, -30f, 2.2f, darker(body, 0.5f))
        g.poly(floatArrayOf(-10f, -45f, -14f, -58f, -6.5f, -46f), body)
        g.poly(floatArrayOf(-7f, -45f, -5f, -57f, -3.5f, -45f), far)
        g.save(); g.translate(-6f, -42f); g.rotate(30f)
        g.roundRect(-4f, -4.6f, 11f, 4.6f, 4.6f, body)
        g.roundRect(6f, -3.8f, 12f, 3.8f, 3.8f, rgb(105, 96, 90))
        g.circle(1.5f, -1.6f, 1.1f, Col.DARK)
        g.restore()
        animalLeg(g, -18f, -17f, ph, 3.4f, body, moving)
        animalLeg(g, -42f, -17f, ph + PI.toFloat(), 3.4f, body, moving)
        if (pull) hitch(g, len, -23f) else g.line(-13f, -24f, 0f, -22f, 2.6f, Col.WOOD)
    }

    private fun ox(g: Gfx, len: Float, travelled: Float, moving: Boolean, pull: Boolean) {
        val body = rgb(122, 76, 42)
        val far = darker(body, 0.8f)
        val horn = rgb(238, 228, 200)
        val ph = travelled / 8f
        animalLeg(g, -17f, -18f, ph + PI.toFloat(), 4.6f, far, moving)
        animalLeg(g, -45f, -18f, ph, 4.6f, far, moving)
        g.line(-54f, -31f, -58f, -14f, 2f, darker(body, 0.5f))
        g.circle(-58f, -13f, 2.6f, darker(body, 0.5f))
        g.ellipse(-35f, -27f, 20f, 11.5f, body)
        g.ellipse(-24f, -35f, 8f, 4.5f, body)
        g.poly(floatArrayOf(-22f, -34f, -14f, -35f, -9f, -24f, -19f, -18f), body)
        g.roundRect(-15f, -32f, -2f, -18f, 5f, body)
        g.roundRect(-7f, -25f, 0f, -17f, 3.5f, rgb(200, 158, 128))
        g.circle(-9f, -27.5f, 1.2f, Col.DARK)
        g.line(-12f, -31f, -17f, -37f, 2.4f, horn); g.line(-17f, -37f, -14f, -41f, 2f, horn)
        g.line(-6f, -31f, -3f, -38f, 2.4f, horn); g.line(-3f, -38f, -6f, -42f, 2f, horn)
        g.roundRect(-18f, -36f, -14f, -15f, 1.5f, Col.WOOD)
        animalLeg(g, -21f, -18f, ph, 4.8f, body, moving)
        animalLeg(g, -49f, -18f, ph + PI.toFloat(), 4.8f, body, moving)
        if (pull) hitch(g, len, -28f)
    }

    private fun rider(g: Gfx, hipX: Float, hipY: Float, jacket: Int, helmet: Int, handX: Float, handY: Float) {
        g.line(hipX, hipY, hipX + 9f, hipY - 12f, 5.5f, jacket)
        g.circle(hipX + 11f, hipY - 18f, 6f, helmet)
        g.poly(floatArrayOf(hipX + 12f, hipY - 21f, hipX + 17.5f, hipY - 19f, hipX + 16f, hipY - 15.5f, hipX + 12f, hipY - 16f), Col.GLASS)
        g.line(hipX + 9f, hipY - 11f, handX, handY, 2.8f, jacket)
    }

    private fun motorbike(g: Gfx, len: Float, travelled: Float, pull: Boolean) {
        val red = rgb(210, 45, 45)
        wheel(g, -50f, -9f, 9f, travelled / 9f)
        wheel(g, -12f, -9f, 9f, travelled / 9f)
        g.line(-50f, -9f, -35f, -16f, 2.6f, Col.STEEL)
        g.line(-12f, -9f, -20f, -32f, 2.6f, Col.STEEL)
        g.line(-37f, -10f, -56f, -14f, 2.6f, rgb(200, 200, 205))
        g.roundRect(-38f, -18f, -25f, -9f, 2f, rgb(90, 92, 100))
        g.poly(floatArrayOf(-42f, -24f, -22f, -27f, -16f, -20f, -26f, -15f, -40f, -16f), red)
        g.ellipse(-28f, -25f, 7f, 3.6f, rgb(235, 80, 70))
        g.roundRect(-48f, -28f, -34f, -24f, 2f, Col.DARK)
        g.circle(-16f, -24f, 2.6f, rgb(255, 230, 120))
        g.line(-22f, -32f, -17f, -34f, 2.2f, Col.DARK)
        g.line(-40f, -28f, -32f, -21f, 3.4f, rgb(40, 44, 60))
        g.line(-32f, -21f, -34f, -12f, 3.4f, rgb(40, 44, 60))
        rider(g, -41f, -28f, rgb(40, 44, 60), red, -21f, -33f)
        if (pull) hitch(g, len, -14f) else g.line(-6f, -13f, 0f, -13f, 3f, Col.STEEL)
    }

    private fun tractor(g: Gfx, len: Float, travelled: Float, moving: Boolean, pull: Boolean, time: Float) {
        val green = rgb(48, 146, 64)
        lugWheel(g, -48f, -14f, 14f, travelled / 14f)
        wheel(g, -15f, -8f, 8f, travelled / 8f, rgb(235, 190, 40))
        g.roundRect(-42f, -29f, -9f, -13f, 3f, green)
        for (k in 0 until 3) g.line(-12f, -26f + k * 4.5f, -9.5f, -26f + k * 4.5f, 1.2f, darker(green, 0.5f))
        g.roundRect(-64f, -32f, -32f, -27f, 3f, darker(green, 0.75f))
        g.line(-60f, -54f, -60f, -30f, 2.2f, Col.DARK)
        g.line(-40f, -54f, -40f, -30f, 2.2f, Col.DARK)
        g.rect(-59f, -52f, -41f, -31f, Col.GLASS)
        g.roundRect(-63f, -57f, -37f, -53f, 2f, darker(green, 0.75f))
        g.circle(-50f, -40f, 4.6f, Col.SKIN)
        g.ellipse(-50f, -44f, 5f, 2.4f, rgb(200, 60, 50))
        g.line(-20f, -29f, -20f, -41f, 2.6f, Col.DARK)
        puffs(g, -20f, -43f, time, moving, 0.8f)
        g.line(-33f, -24f, -7f, -18f, 3.2f, rgb(235, 190, 40))
        g.poly(floatArrayOf(-7f, -27f, 0f, -27f, 0f, -6f, -4f, -6f, -9f, -15f), rgb(120, 126, 136))
        if (pull) hitch(g, len, -14f)
    }

    private fun truck(g: Gfx, len: Float, travelled: Float, pull: Boolean) {
        val blue = rgb(40, 90, 170)
        g.rect(-86f, -15f, -2f, -10f, Col.DARK)
        g.roundRect(-86f, -48f, -30f, -15f, 3f, rgb(232, 234, 238))
        g.rect(-86f, -28f, -30f, -24f, blue)
        // Undo the mirroring for the text so that it never reads backwards
        g.save(); g.translate(-58f, -33f); if (pull) g.scale(-1f, 1f)
        g.text("MYNT", 0f, 0f, 11f, blue)
        g.restore()
        g.poly(floatArrayOf(-29f, -46f, -11f, -46f, -3f, -31f, -2f, -12f, -29f, -12f), rgb(205, 52, 46))
        g.poly(floatArrayOf(-25f, -43f, -13f, -43f, -7.5f, -32f, -25f, -32f), Col.GLASS)
        g.circle(-16f, -36f, 4f, Col.SKIN)
        g.roundRect(-5f, -17f, 0f, -8f, 1.5f, Col.STEEL)
        g.circle(-3.5f, -22f, 2f, rgb(255, 230, 120))
        wheel(g, -74f, -8f, 8f, travelled / 8f)
        wheel(g, -57f, -8f, 8f, travelled / 8f)
        wheel(g, -17f, -8f, 8f, travelled / 8f)
        if (pull) hitch(g, len, -12f)
    }

    // ---- Trains: a locomotive at each end of the basket ----

    private fun train(g: Gfx, kind: Int, left: Float, right: Float, ground: Float, travelled: Float, moving: Boolean, time: Float, gold: Boolean) {
        val sleeper = rgb(92, 70, 50)
        var x = -6f
        while (x < Engine.W + 6f) { g.rect(x - 3f, ground - 1f, x + 3f, ground + 3f, sleeper); x += 14f }
        g.line(-10f, ground - 1.2f, Engine.W + 10f, ground - 1.2f, 2f, rgb(170, 172, 180))
        g.save(); g.translate(right, ground); loco(g, kind, travelled, moving, time); g.restore()
        g.save(); g.translate(left, ground); g.scale(-1f, 1f); loco(g, kind, travelled, moving, time)
        if (gold) g.star(locoLength(kind) / 2, -locoHeight(kind) - 9f, 7f, Col.GOLD)
        g.restore()
    }

    private fun locoLength(kind: Int) = when (kind) { 0 -> 50f; 1 -> 66f; 2 -> 60f; else -> 76f }
    private fun locoHeight(kind: Int) = when (kind) { 0 -> 46f; 1 -> 58f; 2 -> 54f; else -> 40f }

    /** Locomotive with its coupling at x = 0 and its front at +x. */
    private fun loco(g: Gfx, kind: Int, travelled: Float, moving: Boolean, time: Float) {
        g.rect(0f, -12f, 4f, -8f, Col.DARK)
        when (kind) {
            0 -> steam(g, 1f, rgb(150, 35, 35), travelled, moving, time)
            1 -> steam(g, 1.3f, rgb(30, 100, 60), travelled, moving, time)
            2 -> electric(g, travelled)
            else -> x2000(g, travelled)
        }
    }

    private fun steam(g: Gfx, k: Float, cab: Int, travelled: Float, moving: Boolean, time: Float) {
        val a = travelled / (5.5f * k)
        g.save(); g.scale(k, k)
        g.rect(2f, -14f, 47f, -10f, darker(cab, 0.6f))
        g.roundRect(14f, -32f, 45f, -14f, 8f, rgb(40, 45, 52))
        for (bx in floatArrayOf(22f, 31f, 40f)) g.line(bx, -31f, bx, -15f, 1.2f, rgb(180, 150, 60))
        g.roundRect(2f, -40f, 17f, -12f, 2f, cab)
        g.roundRect(0f, -43f, 19f, -39f, 2f, Col.DARK)
        g.rect(6f, -36f, 13f, -28f, Col.GLASS)
        g.poly(floatArrayOf(34f, -32f, 40f, -32f, 42f, -42f, 32f, -42f), Col.DARK)
        g.rect(31f, -45f, 43f, -42f, Col.DARK)
        g.ellipse(25f, -33f, 4f, 3f, rgb(200, 165, 70))
        g.circle(46f, -25f, 2.2f, rgb(255, 230, 120))
        g.poly(floatArrayOf(44f, -12f, 51f, -1f, 44f, -1f), Col.DARK)
        val wheels = floatArrayOf(12f, 25f, 38f)
        for (wx in wheels) wheel(g, wx, -5.6f, 5.6f, a, rgb(200, 45, 40))
        val ox = 2.6f * cos(a); val oy = 2.6f * sin(a)
        g.line(wheels[0] + ox, -5.6f + oy, wheels[2] + ox, -5.6f + oy, 1.6f, Col.STEEL)
        g.restore()
        puffs(g, 37f * k, -46f * k, time, moving, k)
    }

    private fun electric(g: Gfx, travelled: Float) {
        val orange = rgb(228, 118, 38)
        g.line(22f, -42f, 30f, -53f, 1.4f, Col.DARK)
        g.line(30f, -53f, 38f, -42f, 1.4f, Col.DARK)
        g.line(24f, -54f, 36f, -54f, 1.8f, Col.DARK)
        g.rect(6f, -43f, 54f, -40f, Col.STEEL)
        g.roundRect(0f, -40f, 60f, -11f, 4f, orange)
        g.rect(0f, -24f, 60f, -21f, rgb(250, 240, 230))
        g.rect(50f, -36f, 57f, -28f, Col.GLASS)
        for (wx in floatArrayOf(10f, 22f, 34f)) g.rect(wx, -35f, wx + 7f, -29f, Col.GLASS)
        g.circle(57f, -16f, 1.8f, rgb(255, 230, 120))
        g.rect(4f, -11f, 25f, -7f, Col.DARK); g.rect(35f, -11f, 56f, -7f, Col.DARK)
        for (wx in floatArrayOf(9f, 20f, 40f, 51f)) wheel(g, wx, -4.5f, 4.5f, travelled / 4.5f)
    }

    private fun x2000(g: Gfx, travelled: Float) {
        g.line(20f, -38f, 26f, -46f, 1.3f, Col.DARK)
        g.line(26f, -46f, 32f, -38f, 1.3f, Col.DARK)
        g.line(21f, -47f, 31f, -47f, 1.6f, Col.DARK)
        g.poly(floatArrayOf(0f, -38f, 54f, -38f, 74f, -18f, 76f, -11f, 0f, -11f), rgb(206, 210, 218))
        g.poly(floatArrayOf(3f, -33f, 52f, -33f, 59f, -26f, 3f, -26f), rgb(40, 50, 72))
        g.poly(floatArrayOf(0f, -18f, 72f, -18f, 74f, -14.5f, 0f, -14.5f), rgb(30, 72, 165))
        g.circle(71f, -13f, 1.6f, rgb(255, 230, 120))
        g.rect(0f, -11f, 72f, -8f, Col.DARK)
        for (wx in floatArrayOf(10f, 20f, 50f, 60f)) wheel(g, wx, -4f, 4f, travelled / 4f)
    }

    // ---- Jets ----

    private fun jets(g: Gfx, lvl: Int, left: Float, right: Float, ground: Float, facing: Int, moving: Boolean, time: Float) {
        val y = ground - 20f
        val length = 13f + lvl * 1.5f
        for (side in intArrayOf(-1, 1)) {
            val x0 = if (side < 0) left - 1f else right + 1f
            val outer = x0 + side * length
            g.line(x0 - side * 3f, y, x0 + side * 2f, y, 3f, Col.DARK)
            g.roundRect(min(x0, outer), y - 6.5f, max(x0, outer), y + 6.5f, 6f, rgb(150, 158, 170))
            g.rect(min(x0, outer) + 3f, y - 1.2f, max(x0, outer) - 3f, y + 1.2f, rgb(200, 60, 50))
            g.roundRect(min(outer, outer - side * 3f), y - 5f, max(outer, outer - side * 3f), y + 5f, 2f, Col.DARK)
            val firing = moving && side == -facing
            val flicker = 1f + 0.18f * sin(time * 40f + side * 2f)
            val l = (if (firing) 12f + lvl * 10f else 5f) * flicker
            g.poly(floatArrayOf(outer, y - 5f, outer + side * l, y, outer, y + 5f), if (firing) rgb(255, 120, 30) else argb(210, 90, 150, 255))
            g.poly(floatArrayOf(outer, y - 3f, outer + side * l * 0.6f, y, outer, y + 3f), if (firing) rgb(255, 232, 100) else argb(230, 190, 225, 255))
        }
    }
}
