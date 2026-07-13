package com.jlindemann.science.ai

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.sqrt

/**
 * Lightweight on-device RAG agent.
 * Loads precomputed embeddings + passages from app assets (data/ or data/{lang}/)
 * and runs a linear nearest-neighbour search. Ready for real TFLite embedding once
 * an embed.tflite model is placed in assets/data/{lang}/ or assets/data/.
 */
class TfliteRagAgent(private val context: Context, private var language: String? = null) {

    companion object {
        private const val TAG = "TfliteRagAgent"
    }

    private var embeddings: Array<FloatArray>? = null
    private var meta: List<Map<String, Any>> = emptyList()
    private var passages: List<Map<String, String>> = emptyList()

    private var embeddingsLoadedLang: String? = null
    private var passagesLoadedLang: String? = null

    private var embedModelBuffer: java.nio.MappedByteBuffer? = null
    private var embedModelPresent: Boolean = false

    init {
        try {
            loadLanguageAssets(language)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load agent data: ${e.message}")
        }
    }

    fun setLanguage(lang: String?) {
        loadLanguageAssets(lang)
    }

    fun supportsLanguage(lang: String?): Boolean {
        if (lang == null) return false
        return embeddingsLoadedLang == lang && passagesLoadedLang == lang
    }

    fun query(queryText: String, topK: Int = 3): List<SearchResult> {
        val qEmbedding = if (embedModelPresent) embedQueryWithModel(queryText) else embedQueryStub(queryText)
        val emb = embeddings ?: return emptyList()
        val results = emb.indices.map { i ->
            val score = dot(qEmbedding, emb[i])
            val id    = meta.getOrNull(i)?.get("id") as? String ?: passages.getOrNull(i)?.get("id") ?: i.toString()
            val title = meta.getOrNull(i)?.get("title") as? String ?: passages.getOrNull(i)?.get("title") ?: ""
            val text  = passages.getOrNull(i)?.get("text") ?: ""
            SearchResult(id, title, text, score)
        }
        return results.sortedByDescending { it.score }.take(topK)
    }

    data class SearchResult(val id: String, val title: String, val text: String, val score: Float)

    // --- Private loading helpers ---

    private fun loadLanguageAssets(lang: String?) {
        language = lang
        loadEmbeddingsFromAssets(lang)
        loadPassagesFromAssets(lang)
        try { loadEmbedModel(lang) } catch (e: Exception) {
            Log.i(TAG, "No embed model found for $lang: ${e.message}")
        }
    }

    private fun loadEmbedModel(lang: String?) {
        val candidates = buildList {
            lang?.let { add("data/$it/embed.tflite") }
            add("data/embed.tflite")
        }
        for (path in candidates) {
            try {
                context.assets.openFd(path).use { afd ->
                    val channel = java.io.FileInputStream(afd.fileDescriptor).channel
                    embedModelBuffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
                    embedModelPresent = true
                    Log.i(TAG, "Loaded embed model from assets/$path")
                }
                return
            } catch (_: Exception) { }
        }
    }

    private fun loadEmbeddingsFromAssets(lang: String?) {
        val am = context.assets
        val metaCandidates = buildList {
            lang?.let { add("data/$it/embeddings_meta.json") }
            add("data/embeddings_meta.json")
        }
        for (path in metaCandidates) {
            try {
                am.open(path).use { stream ->
                    val arr = JSONArray(stream.bufferedReader().use { it.readText() })
                    meta = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        mapOf("id" to o.getString("id"), "title" to o.optString("title"), "index" to o.optInt("index"))
                    }
                    if (lang != null && path.startsWith("data/$lang/")) embeddingsLoadedLang = lang
                }
                break
            } catch (_: Exception) { }
        }

        val embCandidates = buildList {
            lang?.let { add("data/$it/embeddings.json") }
            add("data/embeddings.json")
        }
        for (path in embCandidates) {
            try {
                am.open(path).use { stream ->
                    val arr = JSONArray(stream.bufferedReader().use { it.readText() })
                    val n = arr.length(); if (n == 0) return
                    val d = arr.getJSONArray(0).length()
                    embeddings = Array(n) { i ->
                        val row = arr.getJSONArray(i)
                        FloatArray(d) { j -> row.getDouble(j).toFloat() }
                    }
                    if (lang != null && path.startsWith("data/$lang/")) embeddingsLoadedLang = lang
                }
                break
            } catch (_: Exception) { }
        }
    }

    private fun loadPassagesFromAssets(lang: String?) {
        val candidates = buildList {
            lang?.let { add("data/$it/passages.jsonl"); add("data/passages_$it.jsonl") }
            add("data/passages.jsonl")
        }
        for (path in candidates) {
            try {
                context.assets.open(path).use { stream ->
                    val lines = BufferedReader(InputStreamReader(stream)).readLines().filter { it.isNotBlank() }
                    passages = lines.map { line ->
                        val obj = JSONObject(line)
                        mapOf("id" to obj.optString("id"), "title" to obj.optString("title"), "text" to obj.optString("text"))
                    }
                    if (lang != null && (path.startsWith("data/$lang/") || path.contains("passages_$lang"))) passagesLoadedLang = lang
                }
                return
            } catch (_: Exception) { }
        }
        passages = emptyList()
    }

    // --- Embedding helpers ---

    private fun embedQueryWithModel(query: String): FloatArray {
        // TODO: replace with real TFLite Interpreter call using embedModelBuffer
        return embedQueryStub(query)
    }

    private fun embedQueryStub(query: String): FloatArray {
        val dim = embeddings?.getOrNull(0)?.size ?: 384
        val out = FloatArray(dim)
        var r = query.hashCode()
        for (i in 0 until dim) {
            r = (r * 1664525 + 1013904223) and 0xffffffff.toInt()
            out[i] = ((r % 1000) / 1000.0f) - 0.5f
        }
        val n = norm(out)
        if (n > 0f) for (i in out.indices) out[i] = out[i] / n
        return out
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0.0f
        for (i in 0 until minOf(a.size, b.size)) s += a[i] * b[i]
        return s
    }

    private fun norm(a: FloatArray): Float {
        var s = 0.0f
        for (v in a) s += v * v
        return sqrt(s)
    }
}
