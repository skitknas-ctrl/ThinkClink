package io.github.skitknasctrl.mathchallange1

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class Mode { MENU, PLAYING, PAUSED, SHOP, QUESTION, GAME_OVER }
enum class Kind { COIN, BILL, GEM }
enum class Sfx { COIN, BILL, GEM, LIFE, BUY, WRONG, LEVEL }
enum class Purpose { SPEED, MONEY, LIFE }

/** A falling object. Times are level time in seconds; the object lands on the basket rim at release + fall. */
class Drop(
    val kind: Kind, val x0: Float, val release: Float, val fall: Float,
    val sway: Float = 0f, val phase: Float = 0f,
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
        const val SPAWN_Y = 128f
        const val BILL_SWAY = 26f
        const val SWAY_SPEED = 3.9f
        const val SPECIAL_SLOWDOWN = 3f
        const val POWER_SECONDS = 10f
        const val QUESTIONS_PER_VISIT = 3
        const val LIVES_PER_VISIT = 2
        val SAVE_KEYS = listOf("level", "finished", "balance", "lives", "gems", "speed", "size", "caught", "fallCount", "shop", "priceBase")
    }

    var mode = Mode.MENU
    var worldHeight = 740f
    val rim: Float get() = worldHeight - 157f
    val ground: Float get() = worldHeight - 119f

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
    var groundX = 0f
    var groundLeft = 0f
    var slowLeft = 0f

    // Shop
    var midLevelShop = false
    var entryCapital = 0
    var livesBought = 0
    var moneyMathLeft = 0
    var lifeMathLeft = 0
    var lifeMathDone = false
    var mathMenu = false
    var priceBase = 1

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
    fun maxLives() = rules.startLives + rules.maxExtraLives
    fun mathDifficulty() = min(5, rules.mathStart + (level - 1) / 3)
    fun speedPrice() = max(1, (priceBase * rules.speedPricePct / 100f).roundToInt())
    fun sizePrice() = 3 * (sizeLevel + 1)
    fun lifePrice() = max(1, ceil(entryCapital / (if (livesBought == 0) 2f else 4f)).toInt())
    fun mathReward(choice: Int) = max(1, (priceBase * rules.mathPct(choice) / 100f).roundToInt())
    fun canBuySpeed() = speedLevel < MAX_SPEED_LEVEL && balance >= speedPrice()
    fun canBuySize() = sizeLevel < SIZE_STEPS && gems >= sizePrice()
    fun canBuyLife() = livesBought < LIVES_PER_VISIT && lives < maxLives() && balance >= lifePrice()
    fun canMathLife() = midLevelShop && !lifeMathDone && lifeMathLeft > 0 && lives < maxLives()
    fun timeRemaining() = max(0f, rules.levelSeconds - levelTime)
    /** Level number to show: the finished one while in the shop between levels. */
    fun shownLevel() = if (!midLevelShop && (mode == Mode.SHOP || mode == Mode.QUESTION)) finishedLevel else level
    fun y(d: Drop) = SPAWN_Y + (levelTime - d.release) / d.fall * (rim - SPAWN_Y)

    // ---- Game flow ----

    fun newGame() {
        level = 1; finishedLevel = 0; balance = 0; lives = rules.startLives; gems = 0
        speedLevel = 0; sizeLevel = 0; caught = 0; fallCount = 0; basketX = W / 2; priceBase = 1
        startLevel()
    }

    fun snapshot(inShop: Boolean): Map<String, Int> = mapOf(
        "level" to level, "finished" to finishedLevel, "balance" to balance, "lives" to lives, "gems" to gems,
        "speed" to speedLevel, "size" to sizeLevel, "caught" to caught, "fallCount" to fallCount,
        "shop" to if (inShop) 1 else 0, "priceBase" to priceBase,
    )

    fun continueGame(s: Map<String, Int>) {
        level = s["level"] ?: 1; finishedLevel = s["finished"] ?: (level - 1)
        balance = s["balance"] ?: 0; lives = s["lives"] ?: rules.startLives; gems = s["gems"] ?: 0
        speedLevel = (s["speed"] ?: 0).coerceIn(0, MAX_SPEED_LEVEL); sizeLevel = (s["size"] ?: 0).coerceIn(0, SIZE_STEPS)
        caught = s["caught"] ?: 0; fallCount = s["fallCount"] ?: 0; priceBase = s["priceBase"] ?: 1
        basketX = W / 2
        if ((s["shop"] ?: 0) == 1) {
            levelCoins = priceBase
            openShop(false)
            note = "Välkommen tillbaka!"
        } else startLevel()
    }

    private fun startLevel() {
        listener.saveProgress(snapshot(false))
        levelTime = 0f; caughtThisLevel = 0; next = 0
        active.clear(); labels.clear()
        groundLeft = 0f; slowLeft = 0f
        setInput(false, false)
        val bw = basketWidth()
        basketX = basketX.coerceIn(bw / 2, W - bw / 2)
        generate()
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
            schedule.add(Drop(Kind.COIN, nx, release, fall))
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
            Mode.QUESTION -> { timeLeft = max(0f, timeLeft - dt); if (timeLeft <= 0f) answer(null) }
            else -> {}
        }
    }

    private fun play(dt: Float) {
        banner = max(0f, banner - dt)
        val bw = basketWidth()
        if (moveDir != 0) {
            val nx = (basketX + moveDir * playerSpeed() * dt).coerceIn(bw / 2, W - bw / 2)
            travelled += abs(nx - basketX)
            basketX = nx
            facing = moveDir
        }
        val timeScale = if (slowLeft > 0f) 0.5f else 1f
        slowLeft = max(0f, slowLeft - dt)
        groundLeft = max(0f, groundLeft - dt)
        levelTime += dt * timeScale

        while (next < schedule.size && schedule[next].release <= levelTime) active.add(schedule[next++])

        var lost = 0
        for (d in active) {
            if (d.state != 0 || levelTime < d.land) continue
            val x = d.x(d.land)
            val inBasket = abs(x - basketX) <= bw / 2 || (groundLeft > 0f && abs(x - groundX) <= bw / 2)
            if (inBasket) { d.state = 1; collect(d, x) } else { d.state = 2; if (d.kind == Kind.COIN) lost++ }
        }
        active.removeAll { it.state == 1 || (it.state == 2 && y(it) > ground + 16f) }

        if (lost > 0) {
            lives = max(0, lives - lost)
            listener.sound(Sfx.LIFE)
            if (lives == 0) { gameOver(); return }
            openShop(true)
            note = if (lost == 1) "Du tappade ett liv." else "Du tappade $lost liv."
            return
        }
        if (levelTime >= rules.levelSeconds && next >= schedule.size && active.none { it.state == 0 }) levelDone()
    }

    private fun collect(d: Drop, x: Float) {
        caught++; caughtThisLevel++
        when (d.kind) {
            Kind.COIN -> { balance++; listener.sound(Sfx.COIN); labels.add(Label(x, rim - 12f, "+1", Col.GOLD)) }
            Kind.BILL -> { balance += rules.billValue; listener.sound(Sfx.BILL); labels.add(Label(x, rim - 12f, "+${rules.billValue}", rgb(140, 230, 140))) }
            Kind.GEM -> { gems++; listener.sound(Sfx.GEM); labels.add(Label(x, rim - 12f, "+1 sten", rgb(120, 220, 255))) }
        }
    }

    private fun levelDone() {
        listener.sound(Sfx.LEVEL)
        finishedLevel = level
        note = "Fångat $caughtThisLevel av $levelObjects."
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
            moneyMathLeft = QUESTIONS_PER_VISIT
            listener.saveProgress(snapshot(true))
        }
        mode = Mode.SHOP
    }

    private fun gameOver() {
        finishedLevel = level
        mode = Mode.GAME_OVER
        setInput(false, false)
        if (caught > high) { high = caught; listener.newRecord(high) }
        listener.saveProgress(null)
    }

    // ---- Buttons ----

    fun press(id: String, arg: Int = 0) {
        when (id) {
            "new" -> newGame()
            "pause" -> if (mode == Mode.PLAYING) { mode = Mode.PAUSED; setInput(false, false) }
            "resume" -> if (mode == Mode.PAUSED) mode = Mode.PLAYING
            "menu" -> mode = Mode.MENU
            "speed" -> buySpeed()
            "size" -> buySize()
            "life" -> buyLife()
            "mathMenu" -> if (moneyMathLeft > 0) mathMenu = true
            "mathBack" -> mathMenu = false
            "mathMoney" -> mathMoney(arg)
            "mathLife" -> mathLife()
            "leave" -> leaveShop()
            "answer" -> answer(arg)
            "ground" -> useGround()
            "slow" -> useSlow()
        }
    }

    private fun ask(p: Purpose, difficulty: Int) {
        purpose = p
        question = MathQuiz.generate(difficulty, rnd)
        timeTotal = rules.answerSeconds; timeLeft = timeTotal
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
        val bw = basketWidth(); basketX = basketX.coerceIn(bw / 2, W - bw / 2)
        listener.sound(Sfx.BUY)
        note = "Större korg! Storlek ${sizeLevel + 1} av ${SIZE_STEPS + 1}."
    }

    private fun buyLife() {
        if (mode != Mode.SHOP || !canBuyLife()) return
        balance -= lifePrice(); lives++; livesBought++
        listener.sound(Sfx.BUY)
        note = "Ett liv till! Du har $lives liv."
    }

    private fun mathMoney(choice: Int) {
        if (mode != Mode.SHOP || midLevelShop || moneyMathLeft <= 0) return
        moneyMathLeft--
        mathMenu = false
        reward = mathReward(choice)
        ask(Purpose.MONEY, (mathDifficulty() + choice - 1).coerceIn(1, 5))
    }

    private fun mathLife() {
        if (mode != Mode.SHOP || !canMathLife()) return
        lifeMathLeft--
        ask(Purpose.LIFE, mathDifficulty())
    }

    private fun leaveShop() {
        if (mode != Mode.SHOP) return
        if (midLevelShop) { mode = Mode.PLAYING; note = "" } else startLevel()
    }

    private fun answer(value: Int?) {
        val q = question ?: return
        val right = value == q.answer
        val wrong = if (value == null) "Tiden tog slut. Rätt svar: ${q.answer}." else "Fel! Rätt svar: ${q.answer}."
        when (purpose) {
            Purpose.SPEED -> if (right) {
                val refund = (stake * rules.refundPct / 100f * (timeLeft / timeTotal)).roundToInt()
                balance += refund; speedLevel++
                listener.sound(Sfx.BUY)
                note = "Snabbare korg! Ny dragare: ${Draggers.name(speedLevel + 1)}. Tillbaka: $refund mynt."
            } else {
                listener.sound(Sfx.WRONG)
                note = "$wrong Insatsen på $stake mynt är förlorad."
            }
            Purpose.MONEY -> if (right) {
                balance += reward
                listener.sound(Sfx.BUY)
                note = "Rätt! +$reward mynt."
            } else {
                listener.sound(Sfx.WRONG)
                note = wrong
            }
            Purpose.LIFE -> if (right) {
                lives = min(maxLives(), lives + 1); lifeMathDone = true
                listener.sound(Sfx.BUY)
                note = "Rätt! Ett liv till."
            } else {
                listener.sound(Sfx.WRONG)
                note = if (lifeMathLeft > 0) "$wrong Försök igen." else wrong
            }
        }
        question = null
        mode = Mode.SHOP
    }

    private fun useGround() {
        if (mode != Mode.PLAYING || gems < 1 || groundLeft > 0f) return
        gems--; groundX = basketX; groundLeft = POWER_SECONDS
        listener.sound(Sfx.BUY)
    }

    private fun useSlow() {
        if (mode != Mode.PLAYING || gems < 1 || slowLeft > 0f) return
        gems--; slowLeft = POWER_SECONDS
        listener.sound(Sfx.BUY)
    }
}
