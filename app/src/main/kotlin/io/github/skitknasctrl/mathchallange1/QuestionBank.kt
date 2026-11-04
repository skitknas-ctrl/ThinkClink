package io.github.skitknasctrl.mathchallange1

import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * A knowledge question from the server database (table questions).
 * until is the last day the question may be used (yyyy-MM-dd, from aktuell_till), null = no end date.
 * imageUrl is shown above the question; the app only uses the question once the image is downloaded.
 */
class BankQuestion(
    val id: Int,
    val subject: String,
    val gradeMin: Int,
    val gradeMax: Int,
    val difficulty: Int,
    val text: String,
    val answer: String,
    val wrong: List<String>,
    val explanation: String?,
    val imageUrl: String? = null,
    val until: String? = null,
) {
    fun expired(today: String) = until != null && until < today
}

fun today(): String = LocalDate.now().toString()

/** The questions the app has downloaded, plus subject names. Immutable; replaced as a whole after a sync. */
class QuestionBank(val questions: List<BankQuestion> = emptyList(), val subjectNames: Map<String, String> = emptyMap()) {

    fun countForGrade(grade: Int, today: String = today()) =
        questions.count { grade in it.gradeMin..it.gradeMax && !it.expired(today) }

    /**
     * Picks a question for the grade with a difficulty as close as possible to the wanted one,
     * skipping questions already asked in this game and questions past their last day. Null if none is left.
     */
    fun pick(grade: Int, difficulty: Int, asked: Set<Int>, rnd: Random, today: String = today()): Question? {
        val candidates = questions.filter {
            grade in it.gradeMin..it.gradeMax && it.id !in asked && it.wrong.isNotEmpty() && !it.expired(today)
        }
        if (candidates.isEmpty()) return null
        val best = candidates.minOf { abs(it.difficulty - difficulty) }
        val q = candidates.filter { abs(it.difficulty - difficulty) == best }.random(rnd)
        val options = (q.wrong.take(5) + q.answer).shuffled(rnd)
        return Question(
            text = q.text,
            options = options,
            correct = options.indexOf(q.answer),
            subject = q.subject,
            subjectName = subjectNames[q.subject] ?: q.subject,
            difficulty = q.difficulty.coerceIn(1, 5),
            bankId = q.id,
            explanation = q.explanation,
            imageUrl = q.imageUrl,
        )
    }
}
