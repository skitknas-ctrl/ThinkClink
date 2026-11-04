package io.github.skitknasctrl.mathchallange1

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to the server API (server/api in the repo) on a background thread:
 * - downloads knowledge questions and keeps a local copy so the game works offline,
 * - keeps finished levels in an outbox folder and uploads them when there is a connection.
 * All callbacks run on the main thread.
 */
class Backend(context: Context, private val appVersion: String) {
    companion object {
        const val BASE_URL = "https://gr8eam.com/myntkorgen/api/"
        /** The app's key. It is not secret (anyone can read it from the app), so the server only lets it
         *  send statistics and read questions. */
        const val APP_KEY = "mk_app_33a43f5843bb3c6ed426b44013da9c69c7143d7c"
        private const val MAX_OUTBOX = 500
        private const val MIN_SYNC_INTERVAL_MS = 10 * 60 * 1000L
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val outbox = File(context.filesDir, "outbox")
    private val cacheFile = File(context.filesDir, "questions.json")
    private val prefs = context.getSharedPreferences("mathchallange_net", 0)
    private var counter = 0

    @Volatile var lastSyncMillis: Long = prefs.getLong("last_sync", 0)
        private set
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

    // ---- Questions ----

    /** Loads the local copy of the questions (if any). */
    fun loadCached(onBank: (QuestionBank) -> Unit) {
        executor.execute {
            val cache = readCache()
            val bank = toBank(cache)
            main.post { onBank(bank) }
        }
    }

    /** Fetches questions changed since the last sync and updates the local copy. */
    fun sync(force: Boolean, onBank: (QuestionBank) -> Unit) {
        if (!force && System.currentTimeMillis() - lastSyncMillis < MIN_SYNC_INTERVAL_MS) return
        executor.execute {
            try {
                val cache = readCache()
                val subjects = cache.optJSONObject("subjects") ?: JSONObject()
                val byId = LinkedHashMap<Int, JSONObject>()
                cache.optJSONArray("questions")?.let { for (i in 0 until it.length()) it.optJSONObject(i)?.let { q -> byId[q.getInt("id")] = q } }
                var since = prefs.getLong("since", 0)
                var afterId = prefs.getLong("after_id", 0)
                for (page in 0 until 50) {
                    val (code, text) = request("GET", "questions.php?since=$since&after_id=$afterId&limit=500", null)
                    if (code != 200) throw IllegalStateException("HTTP $code ${text.take(80)}")
                    val r = JSONObject(text)
                    r.optJSONArray("subjects")?.let { arr ->
                        for (i in 0 until arr.length()) arr.getJSONObject(i).let { s -> subjects.put(s.getString("code"), s.getString("name")) }
                    }
                    val qs = r.getJSONArray("questions")
                    for (i in 0 until qs.length()) {
                        val q = qs.getJSONObject(i)
                        if (q.optString("status") == "active") byId[q.getInt("id")] = q else byId.remove(q.getInt("id"))
                    }
                    since = r.getLong("next_since")
                    afterId = r.getLong("next_after_id")
                    if (!r.optBoolean("more")) break
                }
                val out = JSONObject().put("subjects", subjects).put("questions", JSONArray(byId.values))
                val tmp = File(cacheFile.path + ".tmp")
                tmp.writeText(out.toString())
                tmp.renameTo(cacheFile)
                lastSyncMillis = System.currentTimeMillis()
                prefs.edit().putLong("since", since).putLong("after_id", afterId).putLong("last_sync", lastSyncMillis).apply()
                lastError = null
                val bank = toBank(out)
                main.post { onBank(bank); onChange() }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                main.post { onChange() }
            }
        }
    }

    private fun readCache(): JSONObject = try {
        if (cacheFile.exists()) JSONObject(cacheFile.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    private fun toBank(cache: JSONObject): QuestionBank {
        val names = HashMap<String, String>()
        cache.optJSONObject("subjects")?.let { s -> s.keys().forEach { k -> names[k] = s.optString(k, k) } }
        val list = ArrayList<BankQuestion>()
        val arr = cache.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val q = arr.optJSONObject(i) ?: continue
            val wrongArr = q.optJSONArray("wrong") ?: JSONArray()
            val wrong = (0 until wrongArr.length()).map { wrongArr.optString(it) }.filter { it.isNotEmpty() }
            list.add(BankQuestion(
                id = q.optInt("id"), subject = q.optString("subject"),
                gradeMin = q.optInt("grade_min", 1), gradeMax = q.optInt("grade_max", 12),
                difficulty = q.optInt("difficulty", 3), text = q.optString("text"), answer = q.optString("answer"),
                wrong = wrong, explanation = if (q.isNull("explanation")) null else q.optString("explanation"),
            ))
        }
        return QuestionBank(list, names)
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
                    code == 400 || code == 413 -> { f.delete(); lastError = "Statistik avvisad: ${text.take(80)}" }
                    else -> { lastError = "HTTP $code"; break }
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
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
