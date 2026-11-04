package io.github.skitknasctrl.thinkclink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal drawing surface. All game drawing goes through this interface so that the drawing code
 * has no Android dependencies (the Android implementation is AndroidGfx). Coordinates are game
 * points: the board is 360 points wide, y grows downwards, rotation is clockwise in degrees.
 */
interface Gfx {
    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int)
    fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int)
    fun gradientRect(l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int)
    fun circle(cx: Float, cy: Float, r: Float, color: Int)
    fun ring(cx: Float, cy: Float, r: Float, width: Float, color: Int)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int)
    /** Filled polygon from x,y pairs. */
    fun poly(points: FloatArray, color: Int)
    /** Bold text with its baseline at y. align: -1 left, 0 centre, 1 right. */
    fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Int = 0)
    fun textWidth(s: String, size: Float): Float
    /** Width and height of a downloaded image (key = its URL), or null if it is not available. */
    fun imageSize(key: String): FloatArray? = null
    /** Draws a downloaded image scaled into the rectangle. */
    fun image(key: String, l: Float, t: Float, r: Float, b: Float) {}
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float)
    fun rotate(degrees: Float)
}

fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)

fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

fun darker(color: Int, f: Float = 0.72f): Int {
    val r = ((color shr 16) and 255) * f
    val g = ((color shr 8) and 255) * f
    val b = (color and 255) * f
    return argb((color ushr 24) and 255, r.toInt(), g.toInt(), b.toInt())
}

fun Gfx.ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
    val n = 24
    val pts = FloatArray(n * 2)
    for (i in 0 until n) {
        val a = 2 * PI * i / n
        pts[2 * i] = cx + rx * cos(a).toFloat()
        pts[2 * i + 1] = cy + ry * sin(a).toFloat()
    }
    poly(pts, color)
}

fun Gfx.star(cx: Float, cy: Float, r: Float, color: Int) {
    val pts = FloatArray(20)
    for (i in 0 until 10) {
        val a = -PI / 2 + PI * i / 5
        val rr = if (i % 2 == 0) r else r * 0.45f
        pts[2 * i] = cx + rr * cos(a).toFloat()
        pts[2 * i + 1] = cy + rr * sin(a).toFloat()
    }
    poly(pts, darker(color, 0.8f))
    save(); translate(cx, cy); scale(0.72f, 0.72f); translate(-cx, -cy)
    poly(pts, color)
    restore()
}

/** Draws text, shrinking it until it fits maxWidth. */
fun Gfx.fitText(s: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float, align: Int = 0) {
    var sz = size
    while (sz > 8f && textWidth(s, sz) > maxWidth) sz -= 0.5f
    text(s, x, y, sz, color, align)
}

/** Splits text into lines no wider than maxWidth. */
fun Gfx.wrap(s: String, size: Float, maxWidth: Float): List<String> {
    val lines = ArrayList<String>()
    var cur = ""
    for (word in s.split(' ')) {
        val next = if (cur.isEmpty()) word else "$cur $word"
        if (cur.isNotEmpty() && textWidth(next, size) > maxWidth) { lines.add(cur); cur = word } else cur = next
    }
    if (cur.isNotEmpty()) lines.add(cur)
    return lines
}

object Col {
    val WHITE = rgb(255, 255, 255)
    val GOLD = rgb(255, 209, 93)
    val SOFT = rgb(185, 194, 212)
    val NOTE = rgb(255, 219, 137)
    val ALERT = rgb(255, 128, 112)
    val BUTTON = rgb(35, 112, 133)
    val BUTTON_OFF = rgb(45, 53, 67)
    val OVERLAY = argb(232, 7, 13, 29)
    val TEAL = rgb(78, 211, 178)
    val HEART = rgb(235, 72, 88)
    val HEART_OFF = rgb(70, 76, 92)
    val SKIN = rgb(242, 199, 155)
    val WOOD = rgb(150, 100, 55)
    val ROPE = rgb(214, 190, 140)
    val DARK = rgb(38, 40, 46)
    val GLASS = argb(200, 170, 215, 240)
    val STEEL = rgb(150, 156, 168)
}
