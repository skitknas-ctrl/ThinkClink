package io.github.skitknasctrl.mathchallange1

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Gfx on top of an Android Canvas. images looks up a downloaded question image by its URL. */
class AndroidGfx(private val images: (String) -> Bitmap? = { null }) : Gfx {
    lateinit var canvas: Canvas
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rf = RectF()
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    private fun fill(color: Int) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
    }

    private fun stroke(color: Int, width: Float) {
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.color = color
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
    }

    override fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) { fill(color); canvas.drawRect(l, t, r, b, paint) }

    override fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill(color); rf.set(l, t, r, b); canvas.drawRoundRect(rf, radius, radius, paint)
    }

    override fun gradientRect(l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, r, b, paint)
        paint.shader = null
    }

    override fun circle(cx: Float, cy: Float, r: Float, color: Int) { fill(color); canvas.drawCircle(cx, cy, r, paint) }

    override fun ring(cx: Float, cy: Float, r: Float, width: Float, color: Int) { stroke(color, width); canvas.drawCircle(cx, cy, r, paint) }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        stroke(color, width); canvas.drawLine(x1, y1, x2, y2, paint)
    }

    override fun poly(points: FloatArray, color: Int) {
        fill(color)
        path.reset()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) { path.lineTo(points[i], points[i + 1]); i += 2 }
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Int) {
        fill(color)
        paint.textSize = size; paint.typeface = bold
        paint.textAlign = when (align) { -1 -> Paint.Align.LEFT; 1 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
        canvas.drawText(s, x, y, paint)
    }

    override fun textWidth(s: String, size: Float): Float {
        paint.textSize = size; paint.typeface = bold
        return paint.measureText(s)
    }

    override fun imageSize(key: String): FloatArray? =
        images(key)?.let { floatArrayOf(it.width.toFloat(), it.height.toFloat()) }

    override fun image(key: String, l: Float, t: Float, r: Float, b: Float) {
        val bmp = images(key) ?: return
        rf.set(l, t, r, b)
        canvas.drawBitmap(bmp, null, rf, imagePaint)
    }

    override fun save() { canvas.save() }
    override fun restore() { canvas.restore() }
    override fun translate(dx: Float, dy: Float) = canvas.translate(dx, dy)
    override fun scale(sx: Float, sy: Float) = canvas.scale(sx, sy)
    override fun rotate(degrees: Float) = canvas.rotate(degrees)
}
