package io.github.skitknasctrl.mathchallange1

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to the server API on a background thread:
 * - keeps the knowledge questions (and their images) in sync via QuestionSync,
 * - keeps finished levels in an outbox folder and uploads them to stats.php when there is a connection.
 * All callbacks run on the main thread.
 */
class Backend(context: Context, private val appVersion: String) {
    companion object {
        const val BASE_URL = "https://gr8eam.com/myntkorgen/api/"
        /** The app's key. It is not secret (anyone can read it from the app), so the server only lets it
         *  send statistics and read questions. */
        const val APP_KEY = "mk_app_33a43f5843bb3c6ed426b44013da9c69c7143d7c"
        private const val MAX_OUTBOX = 500
        private const val MAX_IMAGE_BYTES = 600_000
        private const val MIN_SYNC_INTERVAL_MS = 10 * 60 * 1000L
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val outbox = File(context.filesDir, "outbox")
    private val questions = QuestionSync(context.filesDir, { path -> request("GET", path, null) }, ::downloadImage)
    private var counter = 0

    val lastSyncMillis: Long get() = questions.lastSyncMillis
    @Volatile var pending = 0
        private set
    @Volatile var lastError: String? = null
        private set

    /** Called on the main thread whenever the outbox or the sync state changed. */
    var onChange: () -> Unit = {}

    init {
        outbox.mkdirs()
        pending = outbox.list()?.size ?: 0
    }

    fun imageFile(url: String): File = questions.imageFile(url)

    // ---- Questions ----

    /** Loads the local copy of the questions (if any). */
    fun loadCached(onBank: (QuestionBank) -> Unit) {
        executor.execute {
            val bank = try { questions.load() } catch (e: Exception) { QuestionBank() }
            main.post { onBank(bank) }
        }
    }

    /** Fetches questions changed since the last sync (at most every ten minutes unless forced). */
    fun sync(force: Boolean, onBank: (QuestionBank) -> Unit) {
        if (!force && System.currentTimeMillis() - lastSyncMillis < MIN_SYNC_INTERVAL_MS) return
        executor.execute {
            try {
                val bank = questions.sync(System.currentTimeMillis())
                lastError = null
                main.post { onBank(bank); onChange() }
            } catch (e: Exception) {
                lastError = "frågor: " + (e.message ?: e.javaClass.simpleName)
                main.post { onChange() }
            }
        }
    }

    private fun downloadImage(url: String, target: File): Boolean {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Myntkorgen/$appVersion (Android)")
            if (conn.responseCode != 200) return false
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16384)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_IMAGE_BYTES) return false
                }
                buf.toByteArray()
            }
            val tmp = File(target.path + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    // ---- Statistics ----

    /** Stores one level in the outbox and tries to upload everything waiting there. */
    fun queue(json: String) {
        executor.execute {
            val name = "${System.currentTimeMillis()}-${counter++}.json"
            File(outbox, name).writeText(json)
            val files = outbox.listFiles()?.sortedBy { it.name } ?: emptyList()
            if (files.size > MAX_OUTBOX) files.take(files.size - MAX_OUTBOX).forEach { it.delete() }
            uploadAll()
        }
    }

    fun flush() = executor.execute { uploadAll() }

    fun clearOutbox() = executor.execute {
        outbox.listFiles()?.forEach { it.delete() }
        pending = 0
        main.post { onChange() }
    }

    private fun uploadAll() {
        val files = outbox.listFiles()?.sortedBy { it.name } ?: emptyList()
        try {
            for (f in files) {
                val (code, text) = request("POST", "stats.php", f.readText())
                when {
                    code == 200 -> f.delete()
                    // The server rejected the content itself: sending it again will not help.
                    code == 400 || code == 413 -> { f.delete(); lastError = "statistik avvisad: ${text.take(80)}" }
                    else -> { lastError = "statistik: HTTP $code"; break }
                }
            }
        } catch (e: Exception) {
            lastError = "statistik: " + (e.message ?: e.javaClass.simpleName)
        }
        pending = outbox.list()?.size ?: 0
        main.post { onChange() }
    }

    private fun request(method: String, path: String, body: String?): Pair<Int, String> {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("X-Api-Key", APP_KEY)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Myntkorgen/$appVersion (Android)")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    fun shutdown() = executor.shutdown()
}
