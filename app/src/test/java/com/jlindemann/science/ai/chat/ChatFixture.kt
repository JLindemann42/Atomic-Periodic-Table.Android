package com.jlindemann.science.ai.chat

import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.ai.core.TestStrings
import com.jlindemann.science.ai.data.MapElementSource
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.retrieval.RetrievalService
import kotlin.random.Random

/**
 * The real assistant over the real data, for tests that hold a conversation.
 *
 * Every shipped language's element table is loaded, as the app does, so a Swedish or Chinese name
 * resolves while the chat is in English. Strings fall back to English the way Android resources do.
 */
object ChatFixture {

    val available: Boolean by lazy { TestAssets.available() && TestStrings.available }

    val service: RetrievalService by lazy {
        val source = MapElementSource(
            TestAssets.availableLanguages().associateWith { TestAssets.elementTable(it) }, TestAssets::text
        )
        RetrievalService.clear()
        RetrievalService.get(source) ?: error("element assets not readable")
    }

    private val strings = HashMap<String, TestStrings>()

    fun strings(language: String = "en"): TestStrings =
        synchronized(strings) { strings.getOrPut(language) { TestStrings(language, fallBackToEnglish = true) } }

    fun chat(
        seed: Int? = null,
        language: String = "en",
        entitlements: Entitlements = Entitlements.FULL,
        onXp: (Int) -> Unit = {}
    ) =
        ChatOrchestrator(
            retrieval = service,
            stringsFor = ::strings,
            entitlements = { entitlements },
            language = language,
            random = seed?.let { Random(it) } ?: Random.Default,
            onXp = onXp
        )

    val noData: String get() = strings().get(com.jlindemann.science.R.string.ai_no_data)
}
