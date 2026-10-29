package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.cards.ChatCard
import com.jlindemann.science.ai.cards.ChatCardPolicy
import com.jlindemann.science.ai.compose.ChatAction
import com.jlindemann.science.ai.core.AiEngine
import com.jlindemann.science.ai.core.DeclineReason
import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldValue
import com.jlindemann.science.ai.data.ReactivityScale
import com.jlindemann.science.ai.retrieval.FuzzyMatch
import com.jlindemann.science.ai.retrieval.RetrievalService
import com.jlindemann.science.ai.retrieval.TextMatching
import com.jlindemann.science.quiz.GeneratorContext
import java.util.Locale
import kotlin.random.Random

/** Why a turn produced the reply it did, for analytics and for tests. */
enum class ReplySource {
    /** Answered from the app's own data by the engine. */
    ENGINE,

    /** A greeting, a thank-you, or a question about the app rather than about chemistry. */
    PERSONALITY,

    /** Two elements the reactivity heuristic can order, which no stored field can. */
    REACTIVITY,

    /** Nothing could be said honestly. */
    DECLINED
}

/**
 * One turn's reply.
 *
 * @property suggestions chips offered under the reply, to make the next question discoverable
 * @property declineReason on a [ReplySource.DECLINED] reply, why the engine said no
 */
data class ChatReply(
    val text: String,
    val source: ReplySource,
    val actions: List<ChatAction> = emptyList(),
    val card: ChatCard? = null,
    val suggestions: List<String> = emptyList(),
    val plan: QueryPlan? = null,
    val declineReason: DeclineReason? = null
)

/**
 * Owns one conversation: detects the language of each message, asks the engine, and handles
 * everything the engine deliberately declines.
 *
 * **This replaces the 2,664-line `AIAgentManager`.** That file was a keyword cascade the acceptance
 * corpus never touched — `CorpusHarness` wires [AiEngine] directly — so it had no coverage. What it
 * owed the user is defined by two corpus files, `must_defer.tsv` and `capabilities.tsv`: greetings,
 * "what can you do", small talk, and the handful of questions in this package that have an answer
 * but no field behind it. Ported back from the Windows `ChatOrchestrator.cs`, which proved the
 * point first.
 *
 * Platform-free: no Context, no Firebase, no storage. The Android side — rate limits, telemetry,
 * persistence — is `AiChatAgent`.
 *
 * Stateful by design: it holds the [DialogueState] that makes "and its density?" work. One
 * instance per conversation.
 */
class ChatOrchestrator(
    private val retrieval: RetrievalService,
    private val stringsFor: (String) -> StringProvider,
    private val entitlements: () -> Entitlements = { Entitlements.FULL },
    private val cardPolicy: () -> ChatCardPolicy = { ChatCardPolicy.DEFAULT },
    language: String = "en",
    /** Picks among the shipped facts and quiz questions. Seedable, so a test can name the reply. */
    private val random: Random = Random.Default,
    /** Receives the XP a right answer in a quiz run earns (roadmap 2.5); the app adds it to the user's. */
    private val onXp: (Int) -> Unit = {},
    /** The study coach's deck (roadmap 2.6), or null where there is none. Used only with PRO. */
    private val study: com.jlindemann.science.study.StudyAccess? = null
) {

    private val store = retrieval.store

    /**
     * Engines are per language and built on demand: each carries a field resolver built from that
     * language's labels, so switching language mid-thread means a different engine.
     */
    private val engines = HashMap<String, AiEngine>()

    /** What the cached engines were built with, so a purchase or the offline switch invalidates them. */
    private var builtWith: Pair<Entitlements, ChatCardPolicy> = entitlements() to cardPolicy()

    /** What the conversation remembers. Public so a host can persist and restore a thread. */
    val state: DialogueState = DialogueState().apply { activeLanguage = language }

    /** The quiz run in progress, if any (roadmap 2.5). Not persisted: a run belongs to one sitting. */
    var quizRun: QuizRunState? = null
        private set

    /** Which elements this user keeps asking about. The host saves and restores it. */
    val profile = InterestProfile()

    /** The language the last message was answered in. */
    val language: String get() = state.activeLanguage

    /**
     * Answer one message.
     *
     * **The order is the contract.** The engine is asked first and its decline is taken seriously:
     * everything below is for questions with no grounded answer, and reaching them when the engine
     * could have answered would replace a cited fact with a pleasantry.
     */
    fun ask(message: String): ChatReply {
        if (message.isBlank()) return decline("en", DeclineReason.NO_MATCH)

        // Per message, not per session: a thread can switch language and each turn is answered in
        // the language it was asked in.
        state.activeLanguage = retrieval.detectLanguage(message, state.activeLanguage)
        val language = state.activeLanguage
        val strings = stringsFor(language)
        val engine = engineFor(language)

        // A quiz question already on the table. Before the engine: the expected reply is an element
        // name, and the engine would answer it as a lookup rather than marking it right or wrong.
        quiz(message, strings)?.let { return it }

        engine.answer(message, state)?.let { answered ->
            // The element the answer was about, taken from the plan rather than the message so
            // "what about its density" counts towards the element in focus.
            profile.track(engine.lastPlan?.elementKeys?.firstOrNull() ?: state.focusElement)
            return ChatReply(
                answered.text, ReplySource.ENGINE, answered.actions, answered.card,
                suggestions(strings, engine.lastPlan), engine.lastPlan
            )
        }

        // Reactivity, a real chemistry question with a real answer and no field behind it.
        reactivity(message, strings)?.let { return it }
        // "Tell me more", before the overview: "tell me more about gold" names an element, and the
        // overview would answer it by repeating the opening the user has just read.
        moreInfo(message, strings, language)?.let { return it }
        // "Gold", "tell me about gold", "element 79" — must_defer.tsv requires the engine to
        // decline all three; this is what answers them.
        overview(message, strings, language)?.let { return it }
        reactions(message, strings)?.let { return it }
        similarity(message, strings)?.let { return it }
        // Usage, biological role and etymology live in the element's prose, not in a field.
        narrative(message, strings, language)?.let { return it }
        return personality(message, strings, language, engine.lastDecline)
    }

    /**
     * Put an element in focus without a turn — the chat opened from that element's page, so "what
     * is its density" is about it.
     */
    fun focusOn(elementKey: String?) {
        if (elementKey.isNullOrBlank()) return
        state.focusElement = elementKey.lowercase()
    }

    /** The chips to show before anything has been asked, or after a restored thread. */
    fun suggestions(): List<String> = suggestions(stringsFor(state.activeLanguage), null)

    /** Start a new thread, keeping the language the user is writing in. */
    fun reset() {
        state.clear()
        quizRun = null
    }

    // ---- Handlers ---------------------------------------------------------------------------

    /**
     * Two elements the reactivity heuristic can order. The planner declines these deliberately —
     * reactivity is derived, not stored — and the heuristic says so in the same breath.
     */
    private fun reactivity(message: String, strings: StringProvider): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)
        if (REACTIVITY_WORDS.none { TextMatching.containsWord(normalized, it) }) return null
        val named = retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 2)
        if (named.size < 2) return null
        val left = store.element(named[0].key) ?: return null
        val right = store.element(named[1].key) ?: return null
        val winner = ReactivityScale.moreReactive(left, right)
        val text = if (winner == null) {
            strings.get(R.string.ai_reactivity_similar, display(left), display(right))
        } else {
            strings.get(R.string.ai_reactivity_more, display(winner), display(if (winner === left) right else left))
        }
        // With chips, like every other reply; without them the row emptied after this answer.
        return ChatReply(text, ReplySource.REACTIVITY, suggestions = suggestions(strings, null))
    }

    /** Ask a quiz question, or judge the answer to the one outstanding. */
    private fun quiz(message: String, strings: StringProvider): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)

        // "Review", "what should I study": the study coach's due cards as a run (roadmap 2.6).
        if (QuizRun.asksForReview(normalized)) return review(strings)

        // A run in progress (roadmap 2.5): an option picks an answer, "stop" ends it, and anything
        // else is a new question, so the run is left and the message answered as usual.
        quizRun?.let { run ->
            if (QuizRun.stops(normalized)) {
                quizRun = null
                return ChatReply(
                    strings.get(R.string.ai_quiz_run_stopped, run.correct, run.index, run.xp),
                    ReplySource.PERSONALITY, suggestions = suggestions(strings, null)
                )
            }
            QuizRun.choice(normalized, run.current.alternatives)?.let { return answerRun(run, it, strings) }
            quizRun = null
        }

        val pending = state.pendingQuizAnswer

        // A short reply while a question is open is the answer to it — but only if it names an
        // element, or says the answer outright. Judging every short reply consumed unrelated
        // requests as wrong guesses: "tell me more" came back "Not quite! The answer was Vanadium".
        if (pending != null && ElementQuiz.looksLikeAnAnswer(normalized, pending) && !ElementQuiz.asks(normalized)) {
            val guess = retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 1)
            val namesTheAnswer = normalized.contains(pending, ignoreCase = true)
            if (guess.isEmpty() && !namesTheAnswer) return null

            val answer = store.element(pending)
            val verdict = ElementQuiz.judge(
                normalized, pending, guess.firstOrNull()?.key,
                answer?.let(::display) ?: pending, strings
            )
            state.pendingQuizAnswer = null
            return ChatReply(verdict, ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
        }

        if (!ElementQuiz.asks(normalized)) return null
        // A quiz with a scope, a count or a kind of question is a run; a bare "quiz me" is the
        // single question below.
        startRun(message, normalized, strings)?.let { return it }
        val question = ElementQuiz.ask(store.elements, random, strings)
            ?: return ChatReply(strings.get(R.string.ai_quiz_no_start), ReplySource.PERSONALITY,
                suggestions = suggestions(strings, null))
        state.pendingQuizAnswer = question.second.key
        // Deliberately not set as the focus: the user is being asked to name this element, and
        // putting it in focus would let the next question answer itself.
        return ChatReply(question.first, ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
    }

    /**
     * Start a run, or null when the request names no scope, count or kind of question. Its
     * questions come from the Learning Games generators, about the elements the request names.
     */
    private fun startRun(message: String, normalized: String, strings: StringProvider): ChatReply? {
        val request = QuizRun.request(normalized)
        val scope = quizScope(message)
        if (scope == null && !request.explicitCount && !QuizRun.namesATopic(request)) return null

        val context = GeneratorContext(
            store, retrieval.localized(state.activeLanguage), strings, "medium", random, scope
        )
        val questions = QuizRun.questions(context, request)
        if (questions.isEmpty()) {
            return ChatReply(strings.get(R.string.ai_quiz_run_none), ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
        }
        val run = QuizRunState(questions, scope)
        quizRun = run
        state.pendingQuizAnswer = null
        val intro = if (scope == null) strings.get(R.string.ai_quiz_run_start_all, questions.size)
        else strings.get(R.string.ai_quiz_run_start_scoped, questions.size, scopeLabel(scope, strings))
        return ChatReply("$intro\n\n${runQuestion(run, strings)}", ReplySource.PERSONALITY, suggestions = run.current.alternatives)
    }

    /**
     * The elements a quiz request is about: those its filters leave ("the halogens", "period 3"),
     * or those it names ("uranium"); null for the whole table. Read with the operator extractor
     * rather than the planner, which declines anything with a quiz word in it. The element in focus
     * does not narrow it: a quiz asked for in general is about the whole table.
     */
    private fun quizScope(message: String): Set<String>? {
        val filters = com.jlindemann.science.ai.nlu.OperatorExtractor.extract(message).subsetFilters
        if (filters.isNotEmpty()) {
            return store.elements.filter { element -> filters.all { it.matches(element, store) } }
                .map { it.key }.toSet().takeIf { it.isNotEmpty() }
        }
        return retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 4)
            .map { it.key }.toSet().takeIf { it.isNotEmpty() }
    }

    /** "gold", "lithium, sodium", or "8 elements". */
    private fun scopeLabel(scope: Set<String>, strings: StringProvider): String =
        if (scope.size <= 3) scope.mapNotNull(store::element).sortedBy { it.atomicNumber }.joinToString(", ") { display(it) }
        else strings.get(R.string.ai_quiz_run_scope_count, scope.size)

    private fun runQuestion(run: QuizRunState, strings: StringProvider): String =
        strings.get(R.string.ai_quiz_run_question, run.index + 1, run.questions.size, run.current.question)

    /** Whether the study coach is in play: there is a deck, and the user has PRO (roadmap 2.6). */
    private fun studying(): Boolean =
        study != null && entitlements().allows(com.jlindemann.science.ai.data.Tier.PRO)

    /**
     * The study coach's due cards as a run (roadmap 2.6): each card's skill asked again about its
     * element, with fresh options. PRO only; without it, what the coach would do.
     */
    private fun review(strings: StringProvider): ChatReply {
        val access = study
        if (access == null || !studying()) {
            return ChatReply(strings.get(R.string.ai_study_locked), ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
        }
        val questions = access.deck.due(access.now()).mapNotNull { key ->
            val (skill, element) = com.jlindemann.science.study.StudyDeck.parse(key) ?: return@mapNotNull null
            val generator = com.jlindemann.science.quiz.QuestionGenerators.forCategory(skill) ?: return@mapNotNull null
            val context = GeneratorContext(
                store, retrieval.localized(state.activeLanguage), strings, "medium", random, setOf(element)
            )
            generator.generate(context, 1).firstOrNull()
        }
        if (questions.isEmpty()) {
            return ChatReply(strings.get(R.string.ai_study_none), ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
        }
        val run = QuizRunState(questions, null)
        quizRun = run
        state.pendingQuizAnswer = null
        val intro = strings.get(R.string.ai_study_start, questions.size)
        return ChatReply("$intro\n\n${runQuestion(run, strings)}", ReplySource.PERSONALITY, suggestions = run.current.alternatives)
    }

    /** Judge a pick, award its XP, and ask the next question or close the run. */
    private fun answerRun(run: QuizRunState, pick: String, strings: StringProvider): ChatReply {
        val question = run.current
        val right = pick == question.correctAnswer
        // Every answer in a run reviews its card, whether the run is a review or a quiz (roadmap 2.6).
        study?.takeIf { studying() }?.let { access ->
            if (access.deck.record(question.category, question.subjectKey, right, access.now()) != null) access.save()
        }
        val verdict = if (right) {
            run.correct++
            run.xp += question.baseXp
            onXp(question.baseXp)
            strings.get(R.string.ai_quiz_run_right, question.baseXp)
        } else {
            strings.get(R.string.ai_quiz_run_wrong, question.correctAnswer)
        }
        run.index++
        if (run.finished) {
            quizRun = null
            val done = strings.get(R.string.ai_quiz_run_done, run.correct, run.questions.size, run.xp)
            return ChatReply("$verdict\n\n$done", ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
        }
        return ChatReply("$verdict\n\n${runQuestion(run, strings)}", ReplySource.PERSONALITY, suggestions = run.current.alternatives)
    }

    /** What an element does when it meets water, oxygen, an acid, or another element. */
    private fun reactions(message: String, strings: StringProvider): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)
        if (!ElementReactions.asks(normalized)) return null

        // Two at most: a reaction is between a pair, and a third name is usually the product.
        // In the order the question names them, so "sodium and chlorine" is not answered back as
        // "Chlorine and Sodium" — the resolver orders by score, not by position.
        val records = retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 2)
            .sortedBy { normalized.indexOf(TextMatching.normalizeForLookup(it.matched)) }
            .mapNotNull { store.element(it.key) }
            .toMutableList()
        // "What does it react with" after discussing an element is about that element.
        if (records.isEmpty()) state.focusElement?.let(store::element)?.let(records::add)

        val text = ElementReactions.answer(records, normalized, strings, ::display) ?: return null
        state.focusElement = records[0].key
        profile.track(records[0].key)
        return ChatReply(text, ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
    }

    /** What an element resembles — the elements sharing its group. */
    private fun similarity(message: String, strings: StringProvider): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)
        if (!ElementSimilarity.asks(normalized)) return null
        val key = retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 1).firstOrNull()?.key
            ?: state.focusElement
        // No element named and none in focus: say so rather than guessing one.
        val target = key?.let(store::element)
        val text = ElementSimilarity.answer(target, store.elements, strings, ::display)
        if (target != null) {
            state.focusElement = target.key
            profile.track(target.key)
        }
        return ChatReply(text, ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
    }

    /**
     * An element named with nothing asked about it. "Element 79" is covered too: the resolver
     * already reads it as gold.
     */
    private fun overview(message: String, strings: StringProvider, language: String): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)
        // A question that asks something specific is not a request for an overview.
        if (ElementNarrative.detect(normalized) != null) return null

        val named = retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 2)
        if (named.size != 1) return null            // none, or a comparison the engine owns
        val key = named[0].key
        // Only when the element is essentially the whole question. "Gold" and "tell me about gold"
        // qualify; a real question that happens to name gold does not.
        if (!readsAsAnOpening(normalized, named[0].matched)) return null
        val element = store.element(key) ?: return null

        // "Tell me everything about gold" asks for every section; anything else gets a selection,
        // varied so a second "gold" is not the same reply.
        val text = ElementOverview.of(
            element, display(element), language, strings, retrieval.localized(language),
            OverviewOptions(ElementOverview.asksForEverything(normalized), random, entitlements())
        ) ?: return null

        state.focusElement = key
        profile.track(key)
        // The overview states the atomic mass, so "tell me more" must not offer it as news.
        state.noteShared(key, listOf("atomic_mass"))
        return ChatReply(text, ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
    }

    /** Whether the element name is effectively the whole message — "tell me about this". */
    private fun readsAsAnOpening(normalized: String, surface: String): Boolean {
        val withoutElement = normalized.replace(TextMatching.normalizeForLookup(surface), " ")
        return TextMatching.splitQueryTokens(withoutElement).none {
            it.isNotBlank() && it !in OPENING_FRAMES && !ElementOverview.isFullWord(it)
        }
    }

    /**
     * "Tell me more" — the next thing about the element in focus that has not been said.
     *
     * The element comes from the message, or from the conversation. With neither there is nothing
     * to say more *about*, so this declines rather than picking one at random. The fact itself is
     * rendered by the engine, so it arrives with the units, the citation and the entitlement gate
     * of an ordinary question.
     */
    private fun moreInfo(message: String, strings: StringProvider, language: String): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)
        // "Yes" continues a tour that has already started; requiring that something has been said
        // is what stops a bare "sure" volunteering a fact about nothing in particular.
        val goOn = FuzzyMatch.isAffirmative(message) && state.sharedFieldIds.isNotEmpty()
        if (!ElementMoreInfo.asks(normalized) && !goOn) return null

        // An affirmative is never an element, so it is not resolved as one: "sure" is close enough
        // to "sulfur" for the fuzzy resolver to take it.
        val key = if (goOn) state.focusElement
        else retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 1).firstOrNull()?.key
            ?: state.focusElement
        key ?: return null
        val element = store.element(key) ?: return null

        val next = ElementMoreInfo.next(
            ElementMoreInfo.candidatesFor(element),
            { id -> state.hasShared(key, id) },
            state.sharedFieldIds.lastOrNull(),
            random
        )
        // Everything has been said. Say so, rather than repeating the density as though it were new.
        if (next == null) {
            var done = strings.get(R.string.ai_covered_all, display(element))
            fact(strings)?.let { done = done + "\n\n" + it }
            return ChatReply(strings.get(R.string.ai_encouragement) + " " + done, ReplySource.PERSONALITY,
                suggestions = suggestions(strings, null))
        }

        val answer = engineFor(language).fieldAnswerFor(key, next) ?: return null
        state.focusElement = key
        state.noteShared(key, listOf(next))
        profile.track(key)
        return ChatReply(answer.text, ReplySource.ENGINE, answer.actions, answer.card, suggestions(strings, null))
    }

    /**
     * What an element is used for, what it does in the body, where its name comes from — read out
     * of the element's own description. "What is it used for" after asking about gold is about gold.
     */
    private fun narrative(message: String, strings: StringProvider, language: String): ChatReply? {
        val normalized = TextMatching.normalizeForLookup(message)
        val topic = ElementNarrative.detect(normalized) ?: return null
        val key = retrieval.resolver(state.activeLanguage).resolveAll(message, limit = 1).firstOrNull()?.key
            ?: state.focusElement ?: return null
        val element = store.element(key) ?: return null

        val text = ElementNarrative.answer(
            topic,
            (element.value("description") as? FieldValue.Text)?.raw,
            display(element),
            language,
            strings,
            textOf(element, "discovered_by"),
            textOf(element, "year_discovered")
        ) ?: return null

        state.focusElement = key
        profile.track(key)
        return ChatReply(text, ReplySource.PERSONALITY, suggestions = suggestions(strings, null))
    }

    /**
     * A greeting, a thank-you, a fun fact, an answer about the app itself, or an honest decline.
     * Each branch is reachable only because the one above did not match, so a question matching
     * two is answered the same way every time.
     */
    private fun personality(
        message: String,
        strings: StringProvider,
        language: String,
        engineDecline: DeclineReason?
    ): ChatReply {
        val normalized = TextMatching.normalizeForLookup(message)
        val chips = suggestions(strings, null)

        if (mentions(normalized, GREETINGS)) {
            return ChatReply(personalGreeting(strings) ?: strings.get(R.string.ai_greeting), ReplySource.PERSONALITY,
                suggestions = chips)
        }
        if (mentions(normalized, THANKS)) {
            return ChatReply(strings.get(R.string.ai_encouragement), ReplySource.PERSONALITY, suggestions = chips)
        }
        // "Tell me something interesting." The words are broad, which is safe only because the
        // engine has already declined.
        if (mentions(normalized, FACT_WORDS)) {
            fact(strings)?.let { return ChatReply(it, ReplySource.PERSONALITY, suggestions = chips) }
        }
        // Questions about the app itself, after the greetings: "help me" is a capabilities keyword
        // and a greeting is the better answer to it.
        AppFeatures.resolve(normalized)?.let {
            return ChatReply(strings.get(it), ReplySource.PERSONALITY, suggestions = chips)
        }
        return decline(language, engineDecline ?: DeclineReason.NO_MATCH)
    }

    /** "I remember you were checking out Gold recently", in the reader's language, or null. */
    private fun personalGreeting(strings: StringProvider): String? {
        val key = profile.favourite ?: return null
        return strings.get(R.string.ai_personalized_greeting_fav, displayKey(key))
    }

    /**
     * One of the ten shipped facts, at random. Null when the array is missing, rather than an
     * English sentence a Swedish reader should not get.
     */
    private fun fact(strings: StringProvider): String? {
        // The shipped facts and the ones read off the data (roadmap 2.7), drawn from together.
        val shipped = strings.array(R.array.ai_facts)
        val derived = dataFacts.extremes()
        val total = shipped.size + derived.size
        if (total == 0) return null
        val pick = random.nextInt(total)
        return if (pick < shipped.size) shipped[pick]
        else dataFacts.render(derived[pick - shipped.size], strings, retrieval.localized(state.activeLanguage))
    }

    /** Facts read off the data (roadmap 2.7), built once per conversation. */
    private val dataFacts by lazy { com.jlindemann.science.ai.data.DataFacts(store) }

    /**
     * The honest failure. Still carries the suggestion chips, so a decline is a dead end for one
     * turn rather than for the conversation.
     */
    private fun decline(language: String, reason: DeclineReason): ChatReply {
        val strings = stringsFor(language)
        return ChatReply(
            strings.get(R.string.ai_no_data), ReplySource.DECLINED,
            suggestions = suggestions(strings, null), declineReason = reason
        )
    }

    /**
     * The chips offered under a reply, from the shipped string arrays so all twelve languages get
     * them. The router's hand-written list covered Swedish, German and English only.
     */
    private fun suggestions(strings: StringProvider, plan: QueryPlan?): List<String> {
        // Follow-ups built from the answer just given come first (roadmap 2.1), and only the ones
        // the engine would answer next.
        val engine = engineFor(strings.language)
        val followUps = FollowUps(store, { engine.wouldAnswer(it, state) }, ::displayKey)
        val fitted = plan?.let { followUps.after(it, strings, MAX_SUGGESTIONS) }.orEmpty()
        // Then the fixed chips. With an element in play they are about it; otherwise there is room
        // to advertise what the assistant can do beyond lookups.
        val subject = plan?.elementKeys?.firstOrNull() ?: state.focusElement
        val fixed = if (subject != null) {
            val name = displayKey(subject)
            strings.array(R.array.ai_suggest_element)
                .map { template -> runCatching { String.format(Locale.ROOT, template, name) }.getOrDefault(template) }
        } else {
            strings.array(R.array.ai_suggest_general) + strings.array(R.array.ai_suggest_tools).take(1)
        }
        // "Atomic mass of gold" is no follow-up to the atomic mass of gold.
        val fresh = fixed.asSequence().filterNot { chip -> plan != null && followUps.repeats(chip, plan) }
        return (fitted.asSequence() + fresh).distinct().take(MAX_SUGGESTIONS).toList()
    }

    /**
     * The engine for a language, rebuilt when the entitlements or the card policy change. A purchase
     * has to take effect on the next message rather than the next launch.
     */
    private fun engineFor(language: String): AiEngine {
        val current = entitlements() to cardPolicy()
        if (current != builtWith) {
            engines.clear()
            builtWith = current
        }
        return engines.getOrPut(language) {
            AiEngine(
                store, retrieval.datasets, retrieval.localized(language), stringsFor(language),
                retrieval.resolver(language), retrieval.retriever(language), current.first, current.second,
                retrieval.explanations(language)
            )
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /** An element's name in the reader's language. */
    private fun display(element: ElementRecord): String = displayKey(element.key)

    private fun displayKey(key: String): String =
        retrieval.localized(state.activeLanguage)?.name(key)?.takeIf { it.isNotEmpty() }
            ?: key.replaceFirstChar { it.uppercase() }

    /** A field as text, whether it is stored as prose or parsed as a number ("1771"). */
    private fun textOf(element: ElementRecord, fieldId: String): String? = when (val v = element.value(fieldId)) {
        is FieldValue.Text -> v.raw
        is FieldValue.Num -> v.quantity.display
        else -> null
    }

    private fun mentions(normalized: String, phrases: List<String>): Boolean = phrases.any { p ->
        if (' ' in p || p.any { it.code > 0x2000 }) normalized.contains(p) else TextMatching.containsWord(normalized, p)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 3

        /** Words that make a query a reactivity question. Folded, like every vocabulary here. */
        val REACTIVITY_WORDS = listOf(
            "reactive", "reactivity",
            "reaktiv", "reaktivitet",       // sv, de
            "reactivo", "reactividad",      // es
            "reactif", "reactivite",        // fr
            "reattivo", "reattivita",       // it
            "reativo", "reatividade",       // pt
            "reaktief",                     // af
            "reaktibo",                     // fil
            "क्रियाशील", "فعال", "活泼", "活性"
        )

        /**
         * Words that carry no question of their own — "tell me about *gold*". "Element" is here so
         * "element 79" reads as an opening: the resolver maps the number to gold and leaves the
         * bare word behind.
         */
        val OPENING_FRAMES = setOf(
            "tell", "me", "about", "what", "is", "a", "an", "the", "info", "information", "on",
            "element", "grundamne", "amne", "elemento", "elemente", "element79",
            "beratta", "om", "vad", "ar", "erzahl", "mir", "uber", "was", "ist", "parle", "moi", "de",
            "que", "es", "cuentame", "sobre", "dimmi", "cosa", "conte", "fale"
        )

        /** Greetings and thanks, folded. */
        val GREETINGS = listOf(
            "hello", "hi there", "hey", "good morning", "good afternoon", "good evening",
            "hej", "hejsan", "god morgon", "hallo", "guten morgen", "guten tag",
            "bonjour", "hola", "ciao", "ola", "namaste", "salam", "kumusta", "你好", "早上好"
        )

        val THANKS = listOf(
            "thanks", "thank you", "tack", "danke", "merci", "gracias", "grazie", "obrigado",
            "dankie", "salamat", "shukriya", "dhanyavaad", "谢谢", "多谢"
        )

        /** Words that make a message a request for a fact rather than a question. */
        val FACT_WORDS = listOf(
            "fact", "fun fact", "did you know", "interesting", "cool",
            "fakta", "kul fakta", "visste du", "intressant", "haftigt",
            "faktum", "interessant", "fait", "curiosite", "dato curioso", "curiosidad",
            "curiosita", "interessante", "fatti", "curiosidade", "curiosidades", "fatos",
            "तथ्य", "क्या आप जानते हैं", "दिलचस्प",
            "事实", "你知道吗", "有趣", "shishi", "haqiqat", "feit", "katotohanan",
            // The word each language's own "Tell me a fun fact" chip uses.
            "fakt", "anecdote", "دلچسپ", "kawili-wili", "kawili-wiling"
        )
    }
}
