package io.github.skitknasctrl.mathchallange1

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Keeps a local copy of the knowledge questions in sync with the server's questions.php:
 * - the first time (or after a format change) everything is fetched: GET questions.php,
 * - after that only changes: GET questions.php?since=<server_time from the previous answer>;
 *   retired and expired questions in that answer are removed locally,
 * - images (image_url) are downloaded into a folder; a question with an image is only used once its image is there,
 * - questions whose aktuell_till has passed are never used, even if they were downloaded earlier.
 *
 * Only uses org.json and java.io, so it runs (and is tested) outside Android too.
 * request(path) does a GET against the API with the key; download(url, file) saves an image.
 */
class QuestionSync(
    dir: File,
    private val request: (String) -> Pair<Int, String>,
    private val download: (String, File) -> Boolean,
) {
    companion object {
        const val FORMAT = 2
    }

    private val cacheFile = File(dir, "questions.json")
    private val stateFile = File(dir, "questions_state.json")
    private val imageDir = File(dir, "quest_img")
    private var since: String? = null

    var lastSyncMillis = 0L
        private set

    init {
        imageDir.mkdirs()
        val state = try { JSONObject(stateFile.readText()) } catch (e: Exception) { JSONObject() }
        if (state.optInt("format") == FORMAT) {
            since = if (state.isNull("since")) null else state.optString("since").ifEmpty { null }
            lastSyncMillis = state.optLong("last_sync")
        } else {
            cacheFile.delete() // written by an older app version in another format
        }
    }

    fun imageFile(url: String): File {
        val hex = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(imageDir, hex)
    }

    /** The bank from the local copy. */
    fun load(today: String = today()): QuestionBank = toBank(readCache(), today)

    /** One sync round against the server. Returns the new bank, throws with a short message on failure. */
    fun sync(nowMillis: Long, today: String = today()): QuestionBank {
        val full = since == null
        val path = if (full) "questions.php" else "questions.php?since=" + URLEncoder.encode(since, "UTF-8")
        val (code, text) = request(path)
        if (code == 400 && !full) {
            // The server did not accept our sync point: start over with a full download next time.
            since = null
            writeState()
        }
        if (code != 200) throw IllegalStateException("HTTP $code ${errorOf(text)}")
        val r = try { JSONObject(text) } catch (e: Exception) { throw IllegalStateException("ogiltigt svar från servern") }
        if (!r.optBoolean("ok")) throw IllegalStateException(errorOf(text))

        val byId = LinkedHashMap<Int, JSONObject>()
        if (!full) {
            val old = readCache().optJSONArray("questions") ?: JSONArray()
            for (i in 0 until old.length()) old.optJSONObject(i)?.let { byId[it.optInt("id")] = it }
        }
        val qs = r.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until qs.length()) {
            val q = qs.optJSONObject(i) ?: continue
            val id = q.optInt("id", -1)
            if (id < 0) continue
            if (q.optString("status", "active") == "active" && !expired(q, today)) byId[id] = q else byId.remove(id)
        }
        byId.values.removeAll { expired(it, today) }

        // Download missing images (also retries images that failed last time) and remove unused ones.
        val wanted = HashSet<String>()
        for (q in byId.values) {
            val url = imageUrl(q) ?: continue
            val f = imageFile(url)
            wanted.add(f.name)
            if (!f.exists()) download(url, f)
        }
        imageDir.listFiles()?.forEach { if (it.name !in wanted) it.delete() }

        val out = JSONObject().put("questions", JSONArray(byId.values))
        writeAtomic(cacheFile, out.toString())
        since = r.optString("server_time").ifEmpty { null }
        lastSyncMillis = nowMillis
        writeState()
        return toBank(out, today)
    }

    private fun writeState() {
        writeAtomic(stateFile, JSONObject().put("format", FORMAT).put("since", since ?: JSONObject.NULL).put("last_sync", lastSyncMillis).toString())
    }

    private fun readCache(): JSONObject = try {
        if (cacheFile.exists()) JSONObject(cacheFile.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    private fun str(q: JSONObject, key: String): String? = if (q.isNull(key)) null else q.optString(key).ifEmpty { null }

    private fun expired(q: JSONObject, today: String): Boolean {
        val until = str(q, "aktuell_till") ?: return false
        return until.take(10) < today
    }

    private fun imageUrl(q: JSONObject): String? = str(q, "image_url")?.takeIf { it.startsWith("https://") }

    private fun toBank(cache: JSONObject, today: String): QuestionBank {
        val names = HashMap<String, String>()
        val list = ArrayList<BankQuestion>()
        val arr = cache.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val q = arr.optJSONObject(i) ?: continue
            val subject = q.optString("subject")
            str(q, "subject_name")?.let { names[subject] = it }
            val image = imageUrl(q)
            if (image != null && !imageFile(image).exists()) continue
            val wrongArr = q.optJSONArray("wrong") ?: JSONArray()
            val wrong = (0 until wrongArr.length()).map { wrongArr.optString(it) }.filter { it.isNotEmpty() }
            val question = BankQuestion(
                id = q.optInt("id"), subject = subject,
                gradeMin = q.optInt("grade_min", 1), gradeMax = q.optInt("grade_max", 12),
                difficulty = q.optInt("difficulty", 3), text = q.optString("question_text"), answer = q.optString("answer"),
                wrong = wrong, explanation = str(q, "explanation"), imageUrl = image, until = str(q, "aktuell_till")?.take(10),
            )
            if (question.text.isNotEmpty() && question.answer.isNotEmpty() && !question.expired(today)) list.add(question)
        }
        return QuestionBank(list, names)
    }

    private fun errorOf(text: String): String = try { JSONObject(text).optString("error", text.take(60)) } catch (e: Exception) { text.take(60) }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
