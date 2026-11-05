package io.github.skitknasctrl.thinkclink

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * A multiple-choice question as shown in the game.
 * bankId is the database id for knowledge questions and null for math questions made by the app.
 * extraSeconds is added to the answer time (equations and other multi-step tasks).
 */
class Question(
    val text: String,
    val options: List<String>,
    val correct: Int,
    val subject: String,
    val subjectName: String,
    val difficulty: Int,
    val bankId: Int? = null,
    val explanation: String? = null,
    val imageUrl: String? = null,
    val extraSeconds: Float = 0f,
) {
    val answerText: String get() = options[correct]
}

/**
 * Mental arithmetic for school years 1–12 (10–12 = upper secondary), with six answer options.
 *
 * Each task type has the school year where it belongs, based on grade-level fluency expectations
 * (US Common Core: grade 1 add/subtract within 10, grade 2 within 20 and 100, grade 3 tables to 10×10,
 * grade 4 within 1000 and tables to 12×12, grade 5 division facts, decimals and fractions; grade 6–7
 * percent, negative numbers, powers and one-step equations px + q = r) and German "Kopfrechnen"/basic
 * tests for Klasse 7–9 (fractions, percent, roots, linear and simple quadratic equations).
 *
 * The difficulty 1–5 within the year decides how far back the task types reach (1–3: this year's
 * and last year's, 4–5: only this year's) and how big the numbers are.
 */
object MathQuiz {
    private class Made(val text: String, val answer: String, val wrong: List<String>)
    private class Task(val grade: Int, val extra: Float, val make: (Int, Random) -> Made)

    fun generate(grade: Int, difficulty: Int, rnd: Random): Question {
        val g = grade.coerceIn(1, 12)
        val d = difficulty.coerceIn(1, 5)
        val lo = max(1, if (d <= 3) g - 1 else g)
        var pool = tasks.filter { it.grade in lo..g }
        if (pool.isEmpty()) pool = tasks.filter { it.grade <= g }
        val t = pool.random(rnd)
        val m = t.make(d, rnd)
        val wrong = m.wrong.filter { it != m.answer }.distinct().take(5)
        val options = (wrong + m.answer).shuffled(rnd)
        return Question(m.text, options, options.indexOf(m.answer), "matte", T.s("math"), d, extraSeconds = t.extra)
    }

    // ---- Helpers ----

    private fun r(rnd: Random, a: Int, b: Int) = rnd.nextInt(a, b + 1)
    private fun n(v: Int) = if (v < 0) "−${-v}" else "$v"
    private fun p(v: Int) = if (v < 0) "(−${-v})" else "$v"
    /** "+ 5" or "− 5", for terms after the first. */
    private fun pm(v: Int) = if (v < 0) "− ${-v}" else "+ $v"
    private val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private fun sup(v: Int) = v.toString().map { SUP[it - '0'] }.joinToString("")

    private fun dec(hundredths: Int): String {
        val s = String.format(T.locale, "%.2f", hundredths / 100.0)
        val sep = if (s.contains(',')) ',' else '.'
        return s.trimEnd('0').trimEnd(sep)
    }

    /** Wrong integer answers: typical mistakes first, then nearby numbers in random order. */
    private fun ints(ans: Int, rnd: Random, negatives: Boolean = false, mistakes: List<Int> = emptyList()): List<String> {
        val spread = max(4, abs(ans) / 4)
        val near = (listOf(ans + 1, ans - 1, ans + 2, ans - 2, ans + 10, ans - 10) + List(8) { ans + r(rnd, -spread, spread) }).shuffled(rnd)
        val fallback = (3..40).flatMap { listOf(ans + it, ans - it) }
        return (mistakes + near + fallback).filter { it != ans && (negatives || it >= 0) }.distinct().map { n(it) }
    }

    private fun intQ(text: String, ans: Int, rnd: Random, negatives: Boolean = false, mistakes: List<Int> = emptyList()) =
        Made(text, n(ans), ints(ans, rnd, negatives, mistakes))

    private fun decQ(text: String, ansH: Int, rnd: Random, mistakesH: List<Int> = emptyList()): Made {
        val near = listOf(ansH + 10, ansH - 10, ansH + 1, ansH - 1, ansH * 10, ansH / 10, ansH + 100, ansH - 100, ansH + 5, ansH - 5).shuffled(rnd)
        return Made(text, dec(ansH), (mistakesH + near).filter { it != ansH && it > 0 }.map { dec(it) })
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)
    private fun frac(num: Int, den: Int): String { val g = gcd(num, den); return if (den / g == 1) "${num / g}" else "${num / g}/${den / g}" }

    // ---- Task types, in school-year order ----

    private val tasks: List<Task> = listOf(
        // Year 1: add and subtract within 10 (20 at higher difficulty)
        Task(1, 0f) { d, rnd -> val m = if (d <= 2) 10 else 20; val a = r(rnd, 1, m - 1); val b = r(rnd, 1, m - a); intQ("$a + $b = ?", a + b, rnd) },
        Task(1, 0f) { d, rnd -> val m = if (d <= 2) 10 else 20; val a = r(rnd, 3, m); val b = r(rnd, 1, a - 1); intQ("$a − $b = ?", a - b, rnd) },
        // Year 2: within 20 across ten, tens to two-digit numbers
        Task(2, 0f) { d, rnd -> val a = r(rnd, 5, 9) + if (d >= 4) 10 else 0; val b = r(rnd, 4, 9); intQ("$a + $b = ?", a + b, rnd) },
        Task(2, 0f) { d, rnd -> val a = r(rnd, 11, 18) + if (d >= 4) 10 else 0; val b = r(rnd, 3, 9); intQ("$a − $b = ?", a - b, rnd) },
        Task(2, 0f) { d, rnd ->
            val a = r(rnd, 11, if (d >= 4) 159 else 89); val t = r(rnd, 1, 9) * 10
            if (a > t && rnd.nextBoolean()) intQ("$a − $t = ?", a - t, rnd) else intQ("$a + $t = ?", a + t, rnd)
        },
        // Year 3: two-digit addition/subtraction, multiplication and division tables to 10
        Task(3, 1f) { d, rnd ->
            val a = r(rnd, 15, 89); val b = r(rnd, 12, if (d >= 4) 89 else 49)
            if (rnd.nextBoolean()) intQ("$a + $b = ?", a + b, rnd, mistakes = listOf(a + b - 10, a + b + 10))
            else { val hi = max(a, b); val lo = minOf(a, b); intQ("$hi − $lo = ?", hi - lo, rnd, mistakes = listOf(hi - lo + 10)) }
        },
        Task(3, 0f) { d, rnd -> val m = if (d >= 4) 12 else 10; val x = r(rnd, 2, m); val y = r(rnd, 2, m); intQ("$x × $y = ?", x * y, rnd, mistakes = listOf(x * y + x, x * y - y, x + y)) },
        Task(3, 0f) { d, rnd -> val m = if (d >= 4) 12 else 10; val x = r(rnd, 2, m); val y = r(rnd, 2, m); intQ("${x * y} ÷ $y = ?", x, rnd) },
        // Year 4: within 1000, tables to 12, × and ÷ by 10 and 100
        Task(4, 2f) { d, rnd ->
            val a = if (d <= 2) r(rnd, 1, 9) * 100 + r(rnd, 1, 9) * 10 else r(rnd, 120, 899)
            val b = if (d <= 2) r(rnd, 1, 9) * 100 + r(rnd, 1, 9) * 10 else r(rnd, 98, 499)
            if (rnd.nextBoolean() && a > b) intQ("$a − $b = ?", a - b, rnd, mistakes = listOf(a - b + 100, a - b - 10))
            else intQ("$a + $b = ?", a + b, rnd, mistakes = listOf(a + b - 100, a + b + 10))
        },
        Task(4, 0f) { d, rnd ->
            val a = r(rnd, 2, if (d >= 4) 999 else 99)
            when (r(rnd, 0, 2)) {
                0 -> intQ("$a × 10 = ?", a * 10, rnd, mistakes = listOf(a * 100, a))
                1 -> intQ("$a × 100 = ?", a * 100, rnd, mistakes = listOf(a * 10, a * 1000))
                else -> intQ("${a * 100} ÷ 100 = ?", a, rnd, mistakes = listOf(a * 10))
            }
        },
        Task(4, 0f) { _, rnd -> val x = r(rnd, 6, 12); val y = r(rnd, 6, 12); intQ("$x × $y = ?", x * y, rnd, mistakes = listOf(x * y + x, x * y - y)) },
        // Year 5: two-digit × one-digit, division, fraction of a number, decimals
        Task(5, 2f) { d, rnd -> val a = r(rnd, 12, if (d >= 4) 99 else 49); val b = r(rnd, 3, 9); intQ("$a × $b = ?", a * b, rnd, mistakes = listOf(a * b + 10, a * b - b)) },
        Task(5, 2f) { d, rnd -> val b = r(rnd, 3, if (d >= 4) 15 else 9); val q = r(rnd, 11, if (d >= 4) 30 else 20); intQ("${b * q} ÷ $b = ?", q, rnd) },
        Task(5, 1f) { d, rnd ->
            val den = listOf(2, 3, 4, 5, 10).random(rnd); val num = r(rnd, 1, den - 1); val whole = den * r(rnd, 2, if (d >= 4) 20 else 10)
            intQ(T.f("m.of", "$num/$den", whole) + " = ?", whole / den * num, rnd, mistakes = listOf(whole / den, whole * num))
        },
        Task(5, 1f) { _, rnd -> val a = r(rnd, 1, 9) * 10; val b = r(rnd, 1, 99); decQ("${dec(a)} + ${dec(b)} = ?", a + b, rnd, mistakesH = listOf(a / 10 + b)) },
        // Year 6: percent, negative numbers, squares, order of operations, decimal multiplication
        Task(6, 1f) { d, rnd ->
            val pct = (if (d <= 3) listOf(10, 20, 25, 50, 75) else listOf(5, 15, 30, 40, 60)).random(rnd); val base = r(rnd, 1, 20) * 20
            intQ(T.f("m.pct", pct, base) + " = ?", base * pct / 100, rnd, mistakes = listOf(base / pct, base - base * pct / 100))
        },
        Task(6, 0f) { d, rnd ->
            // at least one negative number, otherwise it is year-1 arithmetic
            val a = r(rnd, -9, 9).let { if (it == 0) 3 else it }; val b = if (a > 0) -r(rnd, 1, 9) else r(rnd, -9, 9).let { if (it == 0) 4 else it }
            if (d >= 4 && rnd.nextBoolean()) intQ("${n(a)} − ${p(b)} = ?", a - b, rnd, true, listOf(a + b))
            else intQ("${n(a)} + ${p(b)} = ?", a + b, rnd, true, listOf(a - b, -(a + b)))
        },
        Task(6, 0f) { d, rnd -> val x = r(rnd, 2, if (d >= 4) 20 else 12); intQ("$x² = ?", x * x, rnd, mistakes = listOf(2 * x, x * x + x)) },
        Task(6, 1f) { d, rnd ->
            val a = r(rnd, 2, 9); val b = r(rnd, 2, 9); val c = r(rnd, 2, 9)
            when (if (d >= 4) r(rnd, 0, 2) else r(rnd, 0, 1)) {
                0 -> intQ("$a + $b × $c = ?", a + b * c, rnd, mistakes = listOf((a + b) * c))
                1 -> intQ("($a + $b) × $c = ?", (a + b) * c, rnd, mistakes = listOf(a + b * c))
                else -> { val e = r(rnd, 2, 5); intQ("$a × $b − $c × $e = ?", a * b - c * e, rnd, true, listOf((a * b - c) * e)) }
            }
        },
        Task(6, 1f) { _, rnd -> val x = listOf(50, 25, 150, 250, 20, 75).random(rnd); val y = r(rnd, 1, 10) * 4; decQ("${dec(x)} × $y = ?", x * y, rnd, mistakesH = listOf(x * y / 10, x * y * 10)) },
        // Year 7: negative multiplication, powers, one-step equations, any percent
        Task(7, 0f) { _, rnd ->
            val a = r(rnd, 2, 12) * (if (rnd.nextBoolean()) -1 else 1); val b = r(rnd, 2, 12) * (if (rnd.nextBoolean()) -1 else 1)
            if (rnd.nextBoolean()) intQ("${n(a)} × ${p(b)} = ?", a * b, rnd, true, listOf(-a * b))
            else intQ("${n(a * b)} ÷ ${p(b)} = ?", a, rnd, true, listOf(-a))
        },
        Task(7, 1f) { d, rnd ->
            val (b, e) = when (r(rnd, 0, if (d >= 4) 3 else 2)) { 0 -> 2 to r(rnd, 3, 10); 1 -> 10 to r(rnd, 2, 6); 2 -> 3 to r(rnd, 2, 4); else -> r(rnd, 4, 6) to 3 }
            var v = 1; repeat(e) { v *= b }
            intQ("$b${sup(e)} = ?", v, rnd, mistakes = listOf(b * e, v / b))
        },
        Task(7, 3f) { d, rnd ->
            val x = r(rnd, 2, if (d >= 4) 25 else 12)
            if (rnd.nextBoolean()) { val a = r(rnd, 3, 30); intQ("x + $a = ${x + a}    x = ?", x, rnd, mistakes = listOf(2 * x + a)) }
            else { val a = r(rnd, 2, 9); intQ("${a}x = ${a * x}    x = ?", x, rnd, mistakes = listOf(a * x - a)) }
        },
        Task(7, 2f) { _, rnd -> val pct = r(rnd, 1, 19) * 5; val base = r(rnd, 1, 10) * 20; intQ(T.f("m.pct", pct, base) + " = ?", base * pct / 100, rnd, mistakes = listOf(base - base * pct / 100, pct)) },
        // Year 8: two-step equations, square roots, percent change, adding fractions
        Task(8, 4f) { d, rnd ->
            val x = r(rnd, if (d >= 4) -6 else 1, 12).let { if (it == 0) 5 else it }; val a = r(rnd, 2, 9); val b = r(rnd, 1, 20)
            intQ("${a}x − $b = ${n(a * x - b)}    x = ?", x, rnd, true, listOf((a * x - 2 * b) / a, x + 1))
        },
        Task(8, 0f) { d, rnd -> val x = r(rnd, if (d >= 3) 6 else 2, if (d >= 4) 25 else 15); intQ("√${x * x} = ?", x, rnd, mistakes = listOf(x * x / 2)) },
        Task(8, 3f) { _, rnd ->
            val base = r(rnd, 2, 20) * 20; val pct = listOf(10, 20, 25, 50, 5, 15).random(rnd); val ch = base * pct / 100
            if (rnd.nextBoolean()) intQ(T.f("m.inc", base, pct) + " = ?", base + ch, rnd, mistakes = listOf(ch, base + pct))
            else intQ(T.f("m.dec", base, pct) + " = ?", base - ch, rnd, mistakes = listOf(ch, base - pct))
        },
        Task(8, 3f) { _, rnd ->
            val (b, dd) = listOf(2 to 3, 2 to 5, 3 to 4, 2 to 4, 3 to 6, 4 to 5, 5 to 10, 3 to 5).random(rnd)
            val a = r(rnd, 1, b - 1); val c = r(rnd, 1, dd - 1)
            val ans = frac(a * dd + c * b, b * dd)
            Made("$a/$b + $c/$dd = ?", ans, listOf(frac(a + c, b + dd), frac(a + c, b * dd), frac(a * dd + c * b + 1, b * dd), frac(a * c, b * dd), frac(a * dd + c * b, b * dd * 2), frac(a * dd + c * b - 1, b * dd)))
        },
        // Year 9: equations with x on both sides, evaluating expressions, power rules, scientific notation
        Task(9, 5f) { d, rnd ->
            val x = r(rnd, if (d >= 4) -5 else 1, 10).let { if (it == 0) 2 else it }; val a = r(rnd, 4, 9); val c = r(rnd, 1, a - 1); val b = r(rnd, -12, 12)
            val rhs = a * x + b - c * x
            intQ("${a}x ${pm(b)} = ${if (c == 1) "" else "$c"}x ${pm(rhs)}    x = ?", x, rnd, true, listOf((rhs + b) / (a - c), (rhs - b) / (a + c)))
        },
        Task(9, 3f) { d, rnd ->
            val x = r(rnd, if (d >= 4) -4 else 2, 6).let { if (it == 0) 3 else it }; val k = r(rnd, 2, 7)
            intQ("x² − ${k}x    " + T.f("m.forx", x), x * x - k * x, rnd, true, listOf(2 * x - k * x, x * x + k * x))
        },
        Task(9, 2f) { _, rnd ->
            val b = r(rnd, 2, 7); val m = r(rnd, 2, 6); val k = r(rnd, 2, 5)
            when (r(rnd, 0, 2)) {
                0 -> intQ("$b${sup(m)} · $b${sup(k)} = ${b}ˣ    x = ?", m + k, rnd, mistakes = listOf(m * k))
                1 -> intQ("($b${sup(m)})${sup(k)} = ${b}ˣ    x = ?", m * k, rnd, mistakes = listOf(m + k))
                else -> intQ("$b${sup(m + k)} ÷ $b${sup(k)} = ${b}ˣ    x = ?", m, rnd, mistakes = listOf((m + k) / k))
            }
        },
        Task(9, 2f) { _, rnd ->
            val digits = r(rnd, 11, 99); val e = r(rnd, 2, 5)
            var v = digits; repeat(e - 1) { v *= 10 }
            intQ("${dec(digits * 10)} · 10${sup(e)} = ?", v, rnd, mistakes = listOf(v * 10, v / 10, digits * e))
        },
        // Upper secondary 1: quadratic equations, logarithms, function values
        Task(10, 3f) { _, rnd ->
            val x = r(rnd, 2, 15)
            if (rnd.nextBoolean()) intQ("x² = ${x * x},  x > 0    x = ?", x, rnd, mistakes = listOf(x * x / 2))
            else intQ("x² − ${x}x = 0,  x ≠ 0    x = ?", x, rnd, mistakes = listOf(-x, x * x))
        },
        Task(10, 2f) { _, rnd ->
            if (rnd.nextBoolean()) { val e = r(rnd, 2, 10); intQ("log₂ ${1 shl e} = ?", e, rnd, mistakes = listOf((1 shl e) / 2)) }
            else { val e = r(rnd, 1, 6); var v = 1; repeat(e) { v *= 10 }; intQ("lg $v = ?", e, rnd, mistakes = listOf(v / 10)) }
        },
        Task(10, 3f) { _, rnd ->
            val a = r(rnd, 2, 5); val b = r(rnd, 1, 9); val c = r(rnd, -4, 5).let { if (it == 0) 3 else it }
            intQ("f(x) = ${a}x² − $b    f(${n(c)}) = ?", a * c * c - b, rnd, true, listOf((a * c) * (a * c) - b, a * c * 2 - b))
        },
        // Upper secondary 2: exponential equations, derivatives
        Task(11, 3f) { _, rnd ->
            val b = listOf(2, 3, 5).random(rnd); val e = r(rnd, 2, if (b == 2) 9 else 4); var v = 1; repeat(e) { v *= b }
            intQ("${b}ˣ = $v    x = ?", e, rnd, mistakes = listOf(v / b))
        },
        Task(11, 4f) { _, rnd ->
            val a = r(rnd, 1, 5); val k = r(rnd, 2, 4); val c = r(rnd, 1, if (k == 4) 2 else 3)
            var v = 1; repeat(k - 1) { v *= c }
            val coef = if (a == 1) "" else "$a"
            intQ("f(x) = ${coef}x${sup(k)}    f′($c) = ?", a * k * v, rnd, mistakes = listOf(a * v * c, a * k * v * c))
        },
        // Upper secondary 3: sums of sequences, simple integrals
        Task(12, 4f) { _, rnd ->
            val step = r(rnd, 1, 3); val count = r(rnd, 5, 10); val last = step * count
            intQ("$step + ${2 * step} + ${3 * step} + … + $last = ?", step * count * (count + 1) / 2, rnd, mistakes = listOf(step * count * count / 2, last * count))
        },
        Task(12, 4f) { _, rnd ->
            val b = r(rnd, 2, 6)
            if (rnd.nextBoolean()) intQ("∫₀${sup(b)} 2x dx = ?", b * b, rnd, mistakes = listOf(2 * b, b * b * 2))
            else intQ("∫₀${sup(b)} 3x² dx = ?", b * b * b, rnd, mistakes = listOf(3 * b * b, b * b))
        },
    )
}
