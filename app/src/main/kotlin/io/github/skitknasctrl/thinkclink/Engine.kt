package io.github.skitknasctrl.thinkclink

import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class Mode { MENU, PLAYING, LOST, PAUSED, SHOP, QUESTION, GAME_OVER }
enum class Kind { COIN, BILL, GEM }
enum class Sfx { COIN, BILL, GEM, LIFE, BUY, WRONG, LEVEL }
enum class Purpose { SPEED, MONEY, LIFE, GATE }

/**
 * A falling object. Times are level time in seconds; the object lands on the basket rim at release + fall.
 * mf and dist are what the coin was planned with (for the statistics).
 */
class Drop(
    val kind: Kind, val x0: Float, val release: Float, val fall: Float,
    val sway: Float = 0f, val phase: Float = 0f, val mf: Float = 0f, val dist: Float = 0f,
) {
    val land: Float get() = release + fall
    /** 0 falling, 1 caught, 2 missed. */
    var state = 0
    fun x(t: Float): Float = if (sway == 0f) x0 else x0 + sway * sin(phase + (t - release) * Engine.SWAY_SPEED)
}

class Label(val x: Float, val y: Float, val text: String, val color: Int) { var age = 0f }

interface GameListener {
    fun sound(s: Sfx)
    /** Persist the game so it can be continued; null clears the saved game. */
    fun saveProgress(state: Map<String, Int>?)
    fun newRecord(high: Int)
    /** One finished level as JSON for the statistics API (server/api/stats.php). */
    fun statsReady(json: String)
}

/** Statistics for one played level, including the shop visit after it. Sent when the next level starts. */
class LevelRecord(
    val level: Int, val attempt: Int, val speedLevel: Int, val sizeLevel: Int, val mfStart: Float,
    val livesStart: Int, val coinsPlanned: Int, val billsPlanned: Int, val gemsPlanned: Int,
) {
    var outcome = "abandoned"
    var livesLost = 0
    var livesBought = 0
    var speedBought = 0
    var sizeBought = 0
    var coinsCaught = 0
    var billsCaught = 0
    var gemsCaught = 0
    var groundUsed = 0
    var slowUsed = 0
    var balanceEnd = -1
    var playSeconds = 0f
    val misses = ArrayList<Map<String, Any?>>()
    val answers = ArrayList<Map<String, Any?>>()

    fun toMap(balance: Int): Map<String, Any?> = mapOf(
        "level" to level, "attempt" to attempt.coerceIn(1, 255), "outcome" to outcome,
        "speed_level" to speedLevel, "size_level" to sizeLevel, "mf_start_effective" to mfStart,
        "lives_start" to livesStart, "lives_lost" to livesLost.coerceAtMost(255), "lives_bought" to livesBought.coerceAtMost(255),
        "speed_bought" to speedBought, "size_bought" to sizeBought,
        "coins_planned" to coinsPlanned, "coins_caught" to coinsCaught,
        "bills_planned" to billsPlanned, "bills_caught" to billsCaught, "gems_planned" to gemsPlanned, "gems_caught" to gemsCaught,
        "ground_used" to groundUsed.coerceAtMost(255), "slow_used" to slowUsed.coerceAtMost(255),
        "balance_end" to max(0, if (balanceEnd >= 0) balanceEnd else balance), "play_seconds" to playSeconds,
    )
}

/**
 * All game rules and state, free of Android classes. See DESIGN.md for the rules.
 * The view calls tick() every frame, setInput() from the edge zones and press() for buttons.
 */
class Engine(var rules: Rules, var high: Int, private val listener: GameListener, private val rnd: Random = Random.Default) {
    companion object {
        const val W = 360f
        const val BASE_WIDTH = 50f
        const val SIZE_STEPS = 4
        const val MAX_SPEED_LEVEL = 30
        const val BILL_SWAY = 26f
        const val SWAY_SPEED = 3.9f
        const val SPECIAL_SLOWDOWN = 3f
        const val POWER_SECONDS = 10f
        const val BASKET_HEIGHT = 38f
        /** After a missed coin: this long with the playfield frozen, then a sad face for SAD_SECONDS, then the shop. */
        const val LOST_DELAY = 0.5f
        const val SAD_SECONDS = 0.5f
        /** Coins stand still this long after returning to the level from the shop. */
        const val FREEZE_SECONDS = 3f
        const val QUESTIONS_PER_VISIT = 3
        const val LIVES_PER_VISIT = 2
        /** Extra answer time for knowledge questions: one second per this many characters of question text. */
        const val READING_CHARS_PER_SECOND = 20
        val SAVE_KEYS = listOf("level", "finished", "balance", "lives", "gems", "speed", "size", "caught", "fallCount", "shop", "priceBase", "attempt")
    }

    var mode = Mode.MENU
    /** Playfield geometry in game points, set by the renderer for the current orientation. */
    var spawnY = 72f
    var groundY = 628f
    val rim: Float get() = groundY - BASKET_HEIGHT
    val ground: Float get() = groundY
    /** UI language, also used to pick knowledge questions. */
    var language = "en"

    /** Set by the app: downloaded knowledge questions and the app version for the statistics. */
    var bank = QuestionBank()
    var appVersion = "dev"
    var sessionId: String = UUID.randomUUID().toString()
        private set
    private val asked = HashSet<Int>()
    private var record: LevelRecord? = null
    private var lastStartedLevel = 0
    var attempt = 0
        private set

    // Progress through the game
    var level = 1
    var finishedLevel = 0
    var balance = 0
    var lives = 3
    var gems = 0
    var speedLevel = 0
    var sizeLevel = 0
    var caught = 0
    var fallCount = 0

    // The running level
    var levelTime = 0f
    var levelCoins = 0
    var levelObjects = 0
    var caughtThisLevel = 0
    val schedule = ArrayList<Drop>()
    private var next = 0
    val active = ArrayList<Drop>()
    val labels = ArrayList<Label>()
    var banner = 0f

    // Basket, input and power-ups
    var basketX = W / 2
    var moveDir = 0
    var heldLeft = false
    var heldRight = false
    var facing = 1
    var travelled = 0f
    var clock = 0f
    /** Seconds left of the safety net, which catches everything that falls. */
    var netLeft = 0f
    var slowLeft = 0f
    /** Seconds left of the pause after returning from the shop: coins stand still, the basket can move. */
    var freezeLeft = 0f
    /** Time since a life was lost (Mode.LOST). */
    var lostTime = 0f
    private var lostCount = 0

    // Shop
    var midLevelShop = false
    var entryCapital = 0
    var livesBought = 0
    var moneyMathLeft = 0
    var lifeMathLeft = 0
    var lifeMathDone = false
    var mathMenu = false
    var priceBase = 1
    var gateCorrect = 0

    // Question
    var question: Question? = null
    var purpose = Purpose.SPEED
    var stake = 0
    var reward = 0
    var timeLeft = 0f
    var timeTotal = 1f
    var note = ""

    // ---- Derived values ----

    fun basketWidth() = BASE_WIDTH * 1.2f.pow(sizeLevel)
    fun mfAt(t: Float) = max(10f, rules.startMf - floor(t / rules.mfInterval) * rules.mfStep)
    /** Basket speed factor per level: start MF ÷ MF at half time (120/108 with the defaults). */
    fun stepFactor() = rules.startMf / mfAt(rules.levelSeconds / 2f)
    fun refSpeed(lv: Int) = rules.basketSpeed * stepFactor().pow(lv - 1)
    fun playerSpeed() = rules.basketSpeed * stepFactor().pow(speedLevel)
    /** MF as the player experiences it, i.e. corrected for bought (or not bought) basket speed. */
    fun effectiveMf() = mfAt(levelTime) * playerSpeed() / refSpeed(level)
    fun fallTime(lv: Int, t: Float) = rules.fallStart * (1f - rules.fallFaster).pow(lv - 1 + t / rules.levelSeconds)
    /** One life in play plus the reserve. */
    fun maxLives() = 1 + rules.maxExtraLives
    fun mathDifficulty() = min(5, rules.mathStart + (level - 1) / 3)
    fun speedPrice() = max(1, (priceBase * rules.speedPricePct / 100f).roundToInt())
    fun sizePrice() = 3 * (sizeLevel + 1)
    fun lifePrice() = max(1, ceil(entryCapital / (if (livesBought == 0) 2f else 4f)).toInt())
    fun mathReward(choice: Int) = max(1, (priceBase * rules.mathPct(choice) / 100f).roundToInt())
    fun canBuySpeed() = speedLevel < MAX_SPEED_LEVEL && balance >= speedPrice()
    fun canBuySize() = sizeLevel < SIZE_STEPS && gems >= sizePrice()
    fun canBuyLife() = livesBought < LIVES_PER_VISIT && lives < maxLives() && balance >= lifePrice()
    fun canMathLife() = midLevelShop && !lifeMathDone && lifeMathLeft > 0 && lives < maxLives()
    fun gateRemaining() = max(0, rules.gateQuestions - gateCorrect)
    fun timeRemaining() = max(0f, rules.levelSeconds - levelTime)
    /** Level number to show: the finished one while in the shop between levels. */
    fun shownLevel() = if (!midLevelShop && (mode == Mode.SHOP || mode == Mode.QUESTION)) finishedLevel else level
    fun y(d: Drop) = spawnY + (levelTime - d.release) / d.fall * (rim - spawnY)
    /** The bought speed as a letter: A, B, … Z, AA, AB … */
    fun speedLetter(step: Int = speedLevel): String {
        var n = step; val sb = StringBuilder()
        do { sb.insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }

    // ---- Game flow ----

    fun newGame() {
        closeRecord()
        sessionId = UUID.randomUUID().toString()
        asked.clear(); lastStartedLevel = 0; attempt = 0
        level = 1; finishedLevel = 0; balance = 0; lives = min(rules.startLives, maxLives()); gems = 0
        speedLevel = 0; sizeLevel = 0; caught = 0; fallCount = 0; basketX = W / 2; priceBase = 1
        startLevel()
    }

    fun snapshot(inShop: Boolean): Map<String, Int> = mapOf(
        "level" to level, "finished" to finishedLevel, "balance" to balance, "lives" to lives, "gems" to gems,
        "speed" to speedLevel, "size" to sizeLevel, "caught" to caught, "fallCount" to fallCount,
        "shop" to if (inShop) 1 else 0, "priceBase" to priceBase, "attempt" to attempt,
    )

    /** Continues a saved game. session is the statistics id of that game, if it was saved. */
    fun continueGame(s: Map<String, Int>, session: String? = null) {
        closeRecord()
        sessionId = session ?: UUID.randomUUID().toString()
        asked.clear()
        level = s["level"] ?: 1; finishedLevel = s["finished"] ?: (level - 1)
        balance = s["balance"] ?: 0; lives = (s["lives"] ?: rules.startLives).coerceIn(1, maxLives()); gems = s["gems"] ?: 0
        speedLevel = (s["speed"] ?: 0).coerceIn(0, MAX_SPEED_LEVEL); sizeLevel = (s["size"] ?: 0).coerceIn(0, SIZE_STEPS)
        caught = s["caught"] ?: 0; fallCount = s["fallCount"] ?: 0; priceBase = s["priceBase"] ?: 1
        basketX = W / 2
        if ((s["shop"] ?: 0) == 1) {
            lastStartedLevel = 0; attempt = 0
            levelCoins = priceBase
            openShop(false)
            note = T.s("n.welcome")
        } else {
            lastStartedLevel = level; attempt = s["attempt"] ?: 0
            startLevel()
        }
    }

    private fun startLevel() {
        closeRecord()
        attempt = if (level == lastStartedLevel) attempt + 1 else 1
        lastStartedLevel = level
        listener.saveProgress(snapshot(false))
        levelTime = 0f; caughtThisLevel = 0; next = 0
        active.clear(); labels.clear()
        netLeft = 0f; slowLeft = 0f; freezeLeft = 0f
        setInput(false, false)
        val bw = basketWidth()
        basketX = basketX.coerceIn(bw / 2, W - bw / 2)
        generate()
        record = LevelRecord(
            level, attempt, speedLevel, sizeLevel, effectiveMf(), lives,
            schedule.count { it.kind == Kind.COIN }, schedule.count { it.kind == Kind.BILL }, schedule.count { it.kind == Kind.GEM },
        )
        banner = 2.5f; note = ""; question = null
        mode = Mode.PLAYING
    }

    /** Plans the whole level in advance so that every coin lands at exactly the current MF. */
    private fun generate() {
        schedule.clear()
        val v = refSpeed(level)
        val lo = BASE_WIDTH / 2
        val hi = W - lo
        var t = fallTime(level, 0f)
        var px = basketX
        val specials = ArrayList<Drop>()
        var coins = 0
        while (true) {
            // The next coin must already be visible when the previous one lands, otherwise the player
            // cannot know where to go. So the trip may take at most one fall time at the current MF.
            val mf = mfAt(t) / 100f
            val reach = fallTime(level, t + fallTime(level, t)) * v / mf
            val from = max(lo, px - reach)
            val to = min(hi, px + reach)
            val nx = from + rnd.nextFloat() * (to - from)
            val dt = max(mf * abs(nx - px) / v, rules.minGap)
            val land = t + dt
            if (land > rules.levelSeconds) break
            val fall = fallTime(level, land)
            val release = land - fall
            schedule.add(Drop(Kind.COIN, nx, release, fall, mf = mf * 100f, dist = abs(nx - px)))
            coins++
            if (level >= rules.specialFromLevel) {
                fallCount++
                if (fallCount % rules.billEvery == 0 && rnd.nextInt(100) < rules.billChance) specials.add(special(Kind.BILL, release))
                if (fallCount % rules.gemEvery == 0 && rnd.nextInt(100) < rules.gemChance) specials.add(special(Kind.GEM, release + 0.6f))
            }
            t = land; px = nx
        }
        levelCoins = coins
        schedule.addAll(specials)
        schedule.sortBy { it.release }
        levelObjects = schedule.size
    }

    private fun special(kind: Kind, release: Float): Drop {
        val sway = if (kind == Kind.BILL) BILL_SWAY else 0f
        val lo = BASE_WIDTH / 2 + sway
        val hi = W - lo
        return Drop(kind, lo + rnd.nextFloat() * (hi - lo), release, SPECIAL_SLOWDOWN * fallTime(level, release), sway, rnd.nextFloat() * 6.28f)
    }

    fun setInput(left: Boolean, right: Boolean) {
        heldLeft = left; heldRight = right
        moveDir = (if (right) 1 else 0) - (if (left) 1 else 0)
    }

    fun lifecyclePause() {
        if (mode == Mode.PLAYING) { mode = Mode.PAUSED; setInput(false, false) }
    }

    fun tick(dt: Float) {
        clock += dt
        for (l in labels) l.age += dt
        labels.removeAll { it.age > 0.9f }
        when (mode) {
            Mode.PLAYING -> play(dt)
            Mode.LOST -> { lostTime += dt; if (lostTime >= LOST_DELAY + SAD_SECONDS) afterLost() }
            Mode.QUESTION -> { timeLeft = max(0f, timeLeft - dt); if (timeLeft <= 0f) answer(null) }
            else -> {}
        }
    }

    private fun play(dt: Float) {
        banner = max(0f, banner - dt)
        record?.let { it.playSeconds += dt }
        val bw = basketWidth()
        if (moveDir != 0) {
            val nx = (basketX + moveDir * playerSpeed() * dt).coerceIn(bw / 2, W - bw / 2)
            travelled += abs(nx - basketX)
            basketX = nx
            facing = moveDir
        }
        if (freezeLeft > 0f) { freezeLeft = max(0f, freezeLeft - dt); return }
        val timeScale = if (slowLeft > 0f) 0.5f else 1f
        slowLeft = max(0f, slowLeft - dt)
        netLeft = max(0f, netLeft - dt)
        levelTime += dt * timeScale

        while (next < schedule.size && schedule[next].release <= levelTime) active.add(schedule[next++])

        var lost = 0
        for (d in active) {
            if (d.state != 0 || levelTime < d.land) continue
            val x = d.x(d.land)
            val fromBasket = abs(x - basketX)
            if (fromBasket <= bw / 2 || netLeft > 0f) {
                d.state = 1; collect(d, x)
            } else {
                d.state = 2
                if (d.kind == Kind.COIN) { lost++; recordMiss(d, fromBasket - bw / 2, bw) }
            }
        }
        active.removeAll { it.state == 1 || (it.state == 2 && y(it) > ground + 16f) }

        if (lost > 0) {
            lives = max(0, lives - lost)
            listener.sound(Sfx.LIFE)
            lostCount = lost; lostTime = 0f
            setInput(false, false)
            mode = Mode.LOST
            return
        }
        if (levelTime >= rules.levelSeconds && next >= schedule.size && active.none { it.state == 0 }) levelDone()
    }

    /** After the sad face: the shop, or game over when there are no lives left. */
    private fun afterLost() {
        active.removeAll { it.state == 2 } // the missed coin should not hang in the air during the pause
        if (lives == 0) { gameOver(); return }
        openShop(true)
        note = if (lostCount == 1) T.s("n.lost1") else T.f("n.lostN", lostCount)
    }

    private fun recordMiss(d: Drop, missBy: Float, bw: Float) {
        val r = record ?: return
        r.livesLost++
        if (r.misses.size >= 1000) return
        r.misses.add(mapOf(
            "level_time" to levelTime, "mf_planned" to d.mf, "mf_effective" to d.mf * playerSpeed() / refSpeed(level),
            "distance" to d.dist.roundToInt(), "miss_by" to missBy, "basket_width" to bw,
            "slow_active" to if (slowLeft > 0f) 1 else 0,
        ))
    }

    private fun collect(d: Drop, x: Float) {
        caught++; caughtThisLevel++
        when (d.kind) {
            Kind.COIN -> {
                balance++; record?.let { it.coinsCaught++ }
                listener.sound(Sfx.COIN); labels.add(Label(x, rim - 12f, "+1", Col.GOLD))
            }
            Kind.BILL -> {
                balance += rules.billValue; record?.let { it.billsCaught++ }
                listener.sound(Sfx.BILL); labels.add(Label(x, rim - 12f, "+${rules.billValue}", rgb(140, 230, 140)))
            }
            Kind.GEM -> {
                gems++; record?.let { it.gemsCaught++ }
                listener.sound(Sfx.GEM); labels.add(Label(x, rim - 12f, "+1 sten", rgb(120, 220, 255)))
            }
        }
    }

    private fun levelDone() {
        listener.sound(Sfx.LEVEL)
        record?.let { it.outcome = "completed"; it.balanceEnd = balance }
        finishedLevel = level
        note = T.f("n.caught", caughtThisLevel, levelObjects)
        level++
        openShop(false)
    }

    private fun openShop(mid: Boolean) {
        midLevelShop = mid
        entryCapital = balance
        livesBought = 0
        mathMenu = false
        setInput(false, false)
        priceBase = max(1, levelCoins)
        if (mid) {
            lifeMathLeft = QUESTIONS_PER_VISIT; lifeMathDone = false
        } else {
            moneyMathLeft = QUESTIONS_PER_VISIT; gateCorrect = 0
            listener.saveProgress(snapshot(true))
        }
        mode = Mode.SHOP
    }

    private fun gameOver() {
        record?.outcome = "game_over"
        closeRecord()
        finishedLevel = level
        mode = Mode.GAME_OVER
        setInput(false, false)
        if (caught > high) { high = caught; listener.newRecord(high) }
        listener.saveProgress(null)
    }

    /** Hands the open level record to the app for upload. */
    private fun closeRecord() {
        val r = record ?: return
        record = null
        val payload = mapOf(
            "session" to mapOf(
                "uuid" to sessionId, "app_version" to appVersion, "settings" to rules.asMap(),
                "world_height" to (rim - spawnY).roundToInt().coerceIn(100, 5000), "grade" to rules.grade,
            ),
            "level" to r.toMap(balance),
            "misses" to r.misses,
            "answers" to r.answers,
        )
        listener.statsReady(Json.encode(payload))
    }

    // ---- Buttons ----

    fun press(id: String, arg: Int = 0) {
        when (id) {
            "new" -> newGame()
            "pause" -> if (mode == Mode.PLAYING) { mode = Mode.PAUSED; setInput(false, false) }
            "resume" -> if (mode == Mode.PAUSED) mode = Mode.PLAYING
            "menu" -> { if (mode == Mode.PAUSED) closeRecord(); mode = Mode.MENU }
            "speed" -> buySpeed()
            "size" -> buySize()
            "life" -> buyLife()
            "mathMenu" -> if (moneyMathLeft > 0) mathMenu = true
            "mathBack" -> mathMenu = false
            "mathMoney" -> mathMoney(arg)
            "mathLife" -> mathLife()
            "leave" -> leaveShop()
            "answer" -> answer(arg)
            "net" -> useNet()
            "slow" -> useSlow()
        }
    }

    /** Asks a question: a knowledge question from the bank (if the setting and the bank allow) or math. */
    private fun ask(p: Purpose, difficulty: Int) {
        purpose = p
        val d = difficulty.coerceIn(1, 5)
        val fromBank = rules.knowledgePct > 0 && rnd.nextInt(100) < rules.knowledgePct
        val q = (if (fromBank) bank.pick(rules.grade, d, asked, rnd, language = language) else null) ?: MathQuiz.generate(d, rnd)
        q.bankId?.let { asked.add(it) }
        question = q
        val reading = if (q.bankId != null) (q.text.length / READING_CHARS_PER_SECOND).toFloat() else 0f
        timeTotal = rules.answerSeconds + reading; timeLeft = timeTotal
        mode = Mode.QUESTION
    }

    private fun buySpeed() {
        if (mode != Mode.SHOP || !canBuySpeed()) return
        stake = speedPrice(); balance -= stake
        ask(Purpose.SPEED, mathDifficulty())
    }

    private fun buySize() {
        if (mode != Mode.SHOP || !canBuySize()) return
        gems -= sizePrice(); sizeLevel++
        record?.let { it.sizeBought++ }
        val bw = basketWidth(); basketX = basketX.coerceIn(bw / 2, W - bw / 2)
        listener.sound(Sfx.BUY)
        note = T.f("n.bigger", sizeLevel + 1, SIZE_STEPS + 1)
    }

    private fun buyLife() {
        if (mode != Mode.SHOP || !canBuyLife()) return
        balance -= lifePrice(); lives++; livesBought++
        record?.let { it.livesBought++ }
        listener.sound(Sfx.BUY)
        note = T.f("n.life", lives)
    }

    private fun mathMoney(choice: Int) {
        if (mode != Mode.SHOP || midLevelShop || moneyMathLeft <= 0) return
        moneyMathLeft--
        mathMenu = false
        reward = mathReward(choice)
        ask(Purpose.MONEY, mathDifficulty() + choice - 1)
    }

    private fun mathLife() {
        if (mode != Mode.SHOP || !canMathLife()) return
        lifeMathLeft--
        ask(Purpose.LIFE, mathDifficulty())
    }

    private fun leaveShop() {
        if (mode != Mode.SHOP) return
        when {
            midLevelShop -> { mode = Mode.PLAYING; note = ""; freezeLeft = FREEZE_SECONDS }
            gateRemaining() > 0 -> ask(Purpose.GATE, mathDifficulty())
            else -> startLevel()
        }
    }

    private fun answer(index: Int?) {
        val q = question ?: return
        val right = index != null && index == q.correct
        record?.let { r ->
            if (r.answers.size < 200) r.answers.add(mapOf(
                "purpose" to purpose.name.lowercase(), "question_id" to q.bankId, "subject_code" to q.subject,
                "difficulty" to q.difficulty, "question_text" to if (q.bankId == null) q.text else null,
                "correct" to if (right) 1 else 0, "timed_out" to if (index == null) 1 else 0,
                "answer_ms" to ((timeTotal - timeLeft) * 1000).roundToInt(), "time_limit_ms" to (timeTotal * 1000).roundToInt(),
            ))
        }
        val wrong = if (index == null) T.f("n.timeout", q.answerText) else T.f("n.wrong", q.answerText)
        val extra = q.explanation?.let { " $it" } ?: ""
        question = null
        mode = Mode.SHOP
        when (purpose) {
            Purpose.SPEED -> if (right) {
                val refund = (stake * rules.refundPct / 100f * (timeLeft / timeTotal)).roundToInt()
                balance += refund; speedLevel++
                record?.let { it.speedBought++ }
                listener.sound(Sfx.BUY)
                note = T.f("n.faster", speedLetter(), refund)
            } else {
                listener.sound(Sfx.WRONG)
                note = wrong + T.f("n.lostStake", stake) + extra
            }
            Purpose.MONEY -> if (right) {
                balance += reward
                listener.sound(Sfx.BUY)
                note = T.f("n.right", reward) + extra
            } else {
                listener.sound(Sfx.WRONG)
                note = wrong + extra
            }
            Purpose.LIFE -> if (right) {
                lives = min(maxLives(), lives + 1); lifeMathDone = true
                record?.let { it.livesBought++ }
                listener.sound(Sfx.BUY)
                note = T.s("n.rightLife") + extra
            } else {
                listener.sound(Sfx.WRONG)
                note = (if (lifeMathLeft > 0) wrong + T.s("n.again") else wrong) + extra
            }
            Purpose.GATE -> if (right) {
                gateCorrect++
                listener.sound(Sfx.BUY)
                if (gateRemaining() > 0) ask(Purpose.GATE, mathDifficulty()) else startLevel()
            } else {
                listener.sound(Sfx.WRONG)
                note = wrong + T.s("n.gateWrong") + extra
            }
        }
    }

    private fun useNet() {
        if (mode != Mode.PLAYING || gems < 1 || netLeft > 0f) return
        gems--; netLeft = POWER_SECONDS
        record?.let { it.groundUsed++ }
        listener.sound(Sfx.BUY)
    }

    private fun useSlow() {
        if (mode != Mode.PLAYING || gems < 1 || slowLeft > 0f) return
        gems--; slowLeft = POWER_SECONDS
        record?.let { it.slowUsed++ }
        listener.sound(Sfx.BUY)
    }
}
