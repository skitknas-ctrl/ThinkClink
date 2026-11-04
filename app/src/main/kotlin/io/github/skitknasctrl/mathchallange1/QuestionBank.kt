package io.github.skitknasctrl.mathchallange1

import kotlin.math.abs
import kotlin.random.Random

/** A knowledge question from the server database (see server/schema.sql, table questions). */
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
)

/** The questions the app has downloaded, plus subject names. Immutable; replaced as a whole after a sync. */
class QuestionBank(val questions: List<BankQuestion> = emptyList(), val subjectNames: Map<String, String> = emptyMap()) {

    fun countForGrade(grade: Int) = questions.count { grade in it.gradeMin..it.gradeMax }

    /**
     * Picks a question for the grade with a difficulty as close as possible to the wanted one,
     * skipping questions already asked in this game. Null if there is none left.
     */
    fun pick(grade: Int, difficulty: Int, asked: Set<Int>, rnd: Random): Question? {
        val candidates = questions.filter { grade in it.gradeMin..it.gradeMax && it.id !in asked && it.wrong.isNotEmpty() }
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
            difficulty = q.difficulty,
            bankId = q.id,
            explanation = q.explanation,
        )
    }
}
