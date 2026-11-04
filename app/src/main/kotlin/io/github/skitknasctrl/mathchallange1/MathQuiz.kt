package io.github.skitknasctrl.mathchallange1

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class Question(val text: String, val answer: Int, val options: List<Int>)

/**
 * Mental arithmetic with six answer options.
 * Difficulty 1: + and − up to 10. 2: up to 20, and ×. 3: up to 50, and ×.
 * 4–5: larger numbers, × and ÷ (division always has a whole-number answer).
 */
object MathQuiz {
    fun generate(difficulty: Int, rnd: Random): Question {
        val d = difficulty.coerceIn(1, 5)
        val limit = when (d) { 1 -> 10; 2 -> 20; 3 -> 50; 4 -> 100; else -> 200 }
        val operations = when (d) { 1 -> 2; 2, 3 -> 3; else -> 4 }
        val (text, answer) = when (rnd.nextInt(operations)) {
            0 -> {
                val a = rnd.nextInt(1, limit + 1); val b = rnd.nextInt(1, limit + 1)
                "$a + $b" to a + b
            }
            1 -> {
                val a = rnd.nextInt(1, limit + 1); val b = rnd.nextInt(1, limit + 1)
                "${max(a, b)} − ${min(a, b)}" to abs(a - b)
            }
            2 -> {
                val (xMax, yMax) = when (d) { 2 -> 10 to 10; 3 -> 12 to 12; 4 -> 20 to 12; else -> 25 to 15 }
                val x = rnd.nextInt(2, xMax + 1); val y = rnd.nextInt(2, yMax + 1)
                "$x × $y" to x * y
            }
            else -> {
                val (xMax, yMax) = if (d == 4) 12 to 12 else 20 to 15
                val x = rnd.nextInt(2, xMax + 1); val y = rnd.nextInt(2, yMax + 1)
                "${x * y} ÷ $y" to x
            }
        }
        val choices = mutableSetOf(answer)
        val spread = max(6, answer / 4)
        while (choices.size < 6) {
            val candidate = answer + rnd.nextInt(-spread, spread + 1)
            if (candidate >= 0) choices.add(candidate)
        }
        return Question(text, answer, choices.shuffled(rnd))
    }
}
