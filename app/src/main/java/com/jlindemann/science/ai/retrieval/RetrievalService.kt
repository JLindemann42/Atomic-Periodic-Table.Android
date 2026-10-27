package com.jlindemann.science.ai.retrieval

import android.content.res.AssetManager
import com.jlindemann.science.ai.data.AssetElementSource
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.ElementKey
import com.jlindemann.science.ai.data.ElementSource
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.nlu.LanguageDetector
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the agent's index and keeps it warm across activities.
 *
 * Building costs one ~1 MB JSON parse plus tokenizing about 780 short documents — well under the
 * time the panel already spends showing its spinner, and it happens inside the existing IO block.
 * Everything is cached process-wide, so opening the chat panel a second time is free.
 */
class RetrievalService private constructor(
    private val source: ElementSource,
    val store: KnowledgeStore,
    val datasets: DatasetIndex
) {

    private val indexes = ConcurrentHashMap<String, Bm25Index>()
    private val resolvers = ConcurrentHashMap<String, EntityResolver>()

    /** Display overlay for a language, falling back to the base language. */
    fun localized(language: String): LocalizedView? =
        KnowledgeStore.overlay(source, language) ?: KnowledgeStore.overlay(source, KnowledgeStore.BASE_LANGUAGE)

    /** The retriever for a language, built on first use. */
    fun retriever(language: String): HybridRetriever {
        val view = localized(language)
        val index = indexes.getOrPut(language) {
            Bm25Index(HybridRetriever.buildCorpus(store, datasets, view), language)
        }
        return HybridRetriever(store, datasets, index, resolver(language), view)
    }

    /** The entity resolver for a language, built on first use. */
    fun resolver(language: String): EntityResolver =
        resolvers.getOrPut(language) { EntityResolver(sharedAliases(), language) }

    /** The trigram model (roadmap 1.4), or null when the asset is missing. */
    private val languageDetector: LanguageDetector? by lazy { LanguageDetector.parse(source.text("trigrams.tsv")) }

    /**
     * The language to answer [message] in, starting from [active]: by its letters, with element
     * names as the tie-breaker, and by element names alone when there is no model.
     */
    fun detectLanguage(message: String, active: String): String {
        val byNames = { m: String, a: String -> resolver(a).detectLanguage(m, a) }
        return languageDetector?.detect(message, active, byNames) ?: byNames(message, active)
    }

    private fun sharedAliases(): Map<String, List<ElementAlias>> {
        aliases?.let { return it }
        synchronized(this) {
            aliases?.let { return it }
            // Names and symbols from every shipped language, so a Swedish or Chinese name
            // resolves even while the chat is running in English.
            val tables = HashMap<String, Map<ElementKey, Triple<String, String, String>>>()
            for (language in source.availableLanguages()) {
                val table = source.load(language) ?: continue
                tables[language] = table.rows.mapValues { (_, row) ->
                    Triple(
                        row["element"]?.toString().orEmpty(),
                        row["short"]?.toString().orEmpty(),
                        row["element_atomic_number"]?.toString().orEmpty()
                    )
                }
            }
            val built = EntityResolver.buildAliases(tables)
            aliases = built
            return built
        }
    }

    @Volatile
    private var aliases: Map<String, List<ElementAlias>>? = null

    companion object {
        @Volatile
        private var instance: RetrievalService? = null
        private val lock = Any()

        /**
         * Build or reuse the shared service. Returns null when the element assets cannot be read,
         * so callers keep working without retrieval rather than crashing.
         */
        fun get(assets: AssetManager): RetrievalService? = get(AssetElementSource(assets))

        fun get(source: ElementSource): RetrievalService? {
            instance?.let { return it }
            synchronized(lock) {
                instance?.let { return it }
                val store = KnowledgeStore.get(source) ?: return null
                val service = RetrievalService(source, store, DatasetIndex.build())
                instance = service
                return service
            }
        }

        fun clear() {
            synchronized(lock) { instance = null }
            KnowledgeStore.clear()
        }
    }
}
