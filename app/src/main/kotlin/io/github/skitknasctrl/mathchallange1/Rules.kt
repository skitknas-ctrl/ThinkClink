package io.github.skitknasctrl.mathchallange1

/** One adjustable value in the developer settings (Testinställningar). */
class Setting(val key: String, val label: String, val default: Int, val min: Int, val max: Int)

object SettingsSpec {
    val all = listOf(
        Setting("startMf", "Start-MF (Y)", 120, 50, 300),
        Setting("levelSeconds", "Nivålängd, sekunder (X)", 120, 20, 600),
        Setting("mfInterval", "MF sänks var … sekund (Z)", 5, 1, 60),
        Setting("mfStep", "MF-sänkning per steg", 1, 0, 20),
        Setting("basketSpeed", "Korgfart nivå 1, spelpunkter/s", 70, 20, 400),
        Setting("minGapMs", "Minsta tid mellan landningar, ms", 350, 100, 2000),
        Setting("fallMs", "Myntets falltid vid start, ms", 2500, 500, 8000),
        Setting("fallFasterPct", "Falltiden kortas per nivå, %", 5, 0, 30),
        Setting("specialFromLevel", "Sedlar och stenar från nivå", 3, 1, 50),
        Setting("billEvery", "Sedelchans var … fallna mynt", 200, 10, 2000),
        Setting("billChance", "Sedelchans, %", 33, 0, 100),
        Setting("billValue", "Sedelns värde, mynt", 100, 1, 10000),
        Setting("gemEvery", "Stenchans var … fallna mynt", 100, 10, 2000),
        Setting("gemChance", "Stenchans, %", 50, 0, 100),
        Setting("startLives", "Liv vid start", 3, 1, 9),
        Setting("maxExtraLives", "Max extra liv i ägo", 3, 0, 9),
        Setting("speedPricePct", "Fartpris, % av nivåns mynt", 60, 0, 500),
        Setting("answerSeconds", "Svarstid, sekunder", 10, 2, 60),
        Setting("refundPct", "Max återbetalning vid snabbt svar, %", 30, 0, 100),
        Setting("mathEasyPct", "Mattebelöning lätt, % av nivåns mynt", 5, 0, 200),
        Setting("mathMediumPct", "Mattebelöning medel, % av nivåns mynt", 10, 0, 200),
        Setting("mathHardPct", "Mattebelöning svår, % av nivåns mynt", 15, 0, 200),
        Setting("mathStart", "Mattesvårighet, startnivå (1–5)", 1, 1, 5),
        Setting("sound", "Ljud (1 = på, 0 = av)", 1, 0, 1),
        Setting("showMf", "Visa MF under spel (1 = ja, 0 = nej)", 1, 0, 1),
    )
}

/** Immutable set of setting values, clamped to their allowed ranges. */
class Rules(values: Map<String, Int> = emptyMap()) {
    private val v: Map<String, Int> =
        SettingsSpec.all.associate { it.key to (values[it.key] ?: it.default).coerceIn(it.min, it.max) }

    operator fun get(key: String): Int = v[key] ?: error("Unknown setting $key")

    val startMf get() = get("startMf").toFloat()
    val levelSeconds get() = get("levelSeconds").toFloat()
    val mfInterval get() = get("mfInterval").toFloat()
    val mfStep get() = get("mfStep").toFloat()
    val basketSpeed get() = get("basketSpeed").toFloat()
    val minGap get() = get("minGapMs") / 1000f
    val fallStart get() = get("fallMs") / 1000f
    val fallFaster get() = get("fallFasterPct") / 100f
    val specialFromLevel get() = get("specialFromLevel")
    val billEvery get() = get("billEvery")
    val billChance get() = get("billChance")
    val billValue get() = get("billValue")
    val gemEvery get() = get("gemEvery")
    val gemChance get() = get("gemChance")
    val startLives get() = get("startLives")
    val maxExtraLives get() = get("maxExtraLives")
    val speedPricePct get() = get("speedPricePct")
    val answerSeconds get() = get("answerSeconds").toFloat()
    val refundPct get() = get("refundPct")
    val mathStart get() = get("mathStart")
    val sound get() = get("sound") == 1
    val showMf get() = get("showMf") == 1

    /** Reward percentage for math-for-money: 0 easy, 1 medium, 2 hard. */
    fun mathPct(choice: Int) = when (choice) { 0 -> get("mathEasyPct"); 1 -> get("mathMediumPct"); else -> get("mathHardPct") }
}
