package com.jlindemann.science.quiz

import com.jlindemann.science.R
import com.jlindemann.science.ai.data.Isotope

/** An isotope together with the label the quiz shows for it. */
private data class Nuclide(val label: String, val isotope: Isotope) {
    val neutrons: Int? get() = isotope.neutrons
    val stable: Boolean get() = isotope.stable
    val halfLifeSeconds: Double? get() = isotope.halfLifeSeconds
    val decayType: String? get() = isotope.decayType
}

/**
 * Every usable isotope in the corpus, labelled as symbol-mass.
 *
 * The authored `iso_N` names cannot be shown as-is. 571 of 3111 are the full English element name
 * ("Actinium-203"), which would put English in the middle of a Swedish quiz; a few are reversed
 * ("127-Sb"); and ten names appear twice, which would make an answer ambiguous. Rebuilding the
 * label from the element's symbol and [Isotope.nucleons] fixes all three at once and is the
 * standard notation anyway.
 *
 * `Isotope.massNumber` is digit-filtered out of the mass string and is not the mass number, so
 * [Isotope.nucleons] (`iso_A_N`) is used. Rows whose Z/N/A disagree are dropped.
 */
private fun isotopePool(ctx: GeneratorContext): List<Nuclide> =
    ctx.store.elements
        .flatMap { element ->
            element.isotopes.mapNotNull { iso ->
                val protons = iso.protons ?: return@mapNotNull null
                val neutrons = iso.neutrons ?: return@mapNotNull null
                val nucleons = iso.nucleons ?: return@mapNotNull null
                if (protons + neutrons != nucleons) return@mapNotNull null
                if (protons != element.atomicNumber) return@mapNotNull null
                Nuclide("${element.symbol}-$nucleons", iso)
            }
        }
        // A label that resolves to two records would make the question unanswerable.
        .groupBy { it.label }
        .values
        .filter { it.size == 1 }
        .map { it.single() }

/** "How many neutrons does C-14 have?" */
class IsotopeNeutronGenerator(
    override val categoryKey: String,
    override val baseXp: Int
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val pool = isotopePool(ctx)
        if (pool.size < 4) return emptyList()

        val questions = ArrayList<QuizQuestion>(count)
        val used = HashSet<String>()
        var attempts = count * 8

        while (questions.size < count && attempts-- > 0) {
            val nuclide = pool.random(ctx.random)
            if (!used.add(nuclide.label)) continue
            val correct = nuclide.neutrons ?: continue

            // Near misses: a plausible question is one where counting actually matters.
            val wrongs = ((correct - 4)..(correct + 4))
                .filter { it >= 0 && it != correct }
                .shuffled(ctx.random)
                .take(3)
            if (wrongs.size < 3) continue

            questions.add(
                QuizQuestion(
                    question = ctx.string(R.string.question_isotope_neutrons, nuclide.label),
                    correctAnswer = correct.toString(),
                    alternatives = (wrongs + correct).map { it.toString() }.shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }
}

/** "Which of these isotopes is stable?" — one stable nuclide against three that decay. */
class IsotopeStabilityGenerator(
    override val categoryKey: String,
    override val baseXp: Int
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val pool = isotopePool(ctx)
        val stable = pool.filter { it.stable }
        val unstable = pool.filter { !it.stable }
        if (stable.isEmpty() || unstable.size < 3) return emptyList()

        val questions = ArrayList<QuizQuestion>(count)
        val asked = HashSet<String>()
        var attempts = count * 10

        while (questions.size < count && attempts-- > 0) {
            val correct = stable.random(ctx.random)
            val others = unstable.shuffled(ctx.random).take(3)
            val options = (others + correct).map { it.label }
            if (options.distinct().size != options.size) continue
            if (!asked.add(options.sorted().joinToString("|"))) continue

            questions.add(
                QuizQuestion(
                    question = ctx.string(R.string.question_isotope_stable),
                    correctAnswer = correct.label,
                    alternatives = options.shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }
}

/** "Which of these isotopes has the longest half-life?" */
class IsotopeHalfLifeGenerator(
    override val categoryKey: String,
    override val baseXp: Int
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val pool = isotopePool(ctx).filter { !it.stable && (it.halfLifeSeconds ?: 0.0) > 0.0 }
        if (pool.size < 4) return emptyList()

        val questions = ArrayList<QuizQuestion>(count)
        val asked = HashSet<String>()
        var attempts = count * 12

        while (questions.size < count && attempts-- > 0) {
            val draw = pool.shuffled(ctx.random).take(4)
            val longest = ctx.random.nextBoolean()
            val ordered = draw.sortedByDescending { it.halfLifeSeconds!! }
                .let { if (longest) it else it.reversed() }

            // Half-lives span forty orders of magnitude, so a factor of two is a wide enough gap
            // to be fair while still leaving most draws usable.
            val winner = ordered[0].halfLifeSeconds!!
            val runnerUp = ordered[1].halfLifeSeconds!!
            val ratio = if (longest) winner / runnerUp else runnerUp / winner
            if (ratio < 2.0) continue

            val options = draw.map { it.label }
            if (options.distinct().size != options.size) continue

            val text = ctx.string(
                if (longest) R.string.question_isotope_longest_half_life
                else R.string.question_isotope_shortest_half_life
            )
            if (!asked.add(text + "|" + options.sorted().joinToString("|"))) continue

            questions.add(
                QuizQuestion(text, ordered[0].label, options.shuffled(ctx.random), baseXp)
            )
        }
        return questions
    }
}

/** "How does C-14 decay?" */
class IsotopeDecayGenerator(
    override val categoryKey: String,
    override val baseXp: Int
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val pool = isotopePool(ctx).mapNotNull { nuclide ->
            DecayMode.of(nuclide.decayType)?.let { nuclide to it }
        }
        if (pool.isEmpty()) return emptyList()

        val questions = ArrayList<QuizQuestion>(count)
        val used = HashSet<String>()
        var attempts = count * 10

        while (questions.size < count && attempts-- > 0) {
            val (nuclide, mode) = pool.random(ctx.random)
            if (!used.add(nuclide.label)) continue

            // Checked against every option already chosen, not just the correct one: beta-plus and
            // electron capture must not meet even when neither of them is the answer.
            val wrongs = ArrayList<DecayMode>(3)
            for (candidate in DecayMode.values().toList().shuffled(ctx.random)) {
                if (wrongs.size == 3) break
                if (candidate == mode) continue
                if ((wrongs + mode).any { DecayMode.confusable(it, candidate) }) continue
                wrongs.add(candidate)
            }
            if (wrongs.size < 3) continue

            val options = (wrongs + mode).map { ctx.string(it.labelRes) }
            if (options.distinct().size != options.size) continue

            questions.add(
                QuizQuestion(
                    question = ctx.string(R.string.question_isotope_decay, nuclide.label),
                    correctAnswer = ctx.string(mode.labelRes),
                    alternatives = options.shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }
}
