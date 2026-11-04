package io.github.skitknasctrl.thinkclink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Decodes downloaded question images on demand (scaled down to at most 800 px) and keeps a few in memory. */
class ImageStore(private val fileFor: (String) -> File) {
    private val cache = LruCache<String, Bitmap>(4)
    private val failed = HashSet<String>()

    fun get(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        if (url in failed) return null
        val f = fileFor(url)
        val bmp = if (f.exists()) decode(f) else null
        if (bmp == null) failed.add(url) else cache.put(url, bmp)
        return bmp
    }

    /** Forget earlier misses, e.g. after a sync downloaded new images. */
    fun refresh() = failed.clear()

    private fun decode(f: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }
}
