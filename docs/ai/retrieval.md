---
title: Retrieval
parent: AI Agent
nav_order: 5
---

# Retrieval

Package: `ai/retrieval/` — six files.

Retrieval is the **fallback**, not the primary path. Most questions are answered
by the structured engine reading a specific field. Retrieval handles what the
planner cannot resolve to a field: dataset lookups, definitions, and
loosely-phrased questions.

## There are no embeddings

The most important thing to know about this layer, because a lot of stale
material said otherwise.

The assistant was originally built around on-device semantic search — a corpus of
passages, embedded offline with a sentence transformer, matched by cosine
similarity at query time.

**It never worked.** The query embedder returned a hash-seeded pseudo-random
vector rather than a real embedding. Cosine scores against the precomputed
passage vectors hovered near zero and never cleared the 0.65 threshold gating
retrieval. The path looked central and was inert.

It was replaced by Okapi BM25. There is no `.tflite` model in the repository, no
TensorFlow or ML dependency in `app/build.gradle`, and no embedding artifacts
remain. The generated `data/*.jsonl` / `*.npy` files and the `scripts/README.md`
section describing a `prepare_corpus.py` / `build_embeddings.py` pipeline — for
scripts that did not exist — have been removed. See
[Project history](../development/history#the-abandoned-embedding-experiment).

An earlier round of the same cleanup deleted 42 MB of per-language
`assets/data/**` passages after it was found that every passage file was
byte-identical to the `description` field already in `elements_{lang}.json`.

## `Bm25Index.kt`

Standard Okapi BM25.

```kotlin
class Bm25Index(
    documents: List<Document>,   // id, title, body
    language: String,
    k1: Double = 1.2,
    b: Double = 0.6
)
```

`b = 0.6` rather than the usual 0.75 — length normalisation is dialled back
because the corpus mixes one-line dataset rows with full element descriptions,
and stronger normalisation over-rewards the short ones.

| Method | Purpose |
|:--|:--|
| `search` | Raw BM25 scores |
| `searchNormalized` | Divided by the top hit — ordinal only, for threshold comparison |
| `mentionsUnknownTerm` | Is there a term the corpus has never seen? |
| `sharesTermWithTitle` | Does the query touch a document title? |

The last two support declining. A query containing a term nowhere in the corpus
is more likely off-topic than poorly matched, and the engine would rather say so.

## `Tokenizer.kt`

Multilingual tokenisation, and the file where several hard-won bug fixes live.

**Han unigrams and bigrams.** Chinese is written without spaces. Rather than
shipping a segmentation dictionary, the tokeniser emits both individual
characters and adjacent pairs, letting BM25's scoring pick out the meaningful
compounds.

**Arabic letter-variant folding.** Urdu and Arabic orthography admit several
encodings of the same letter; these are folded together.

**Script-aware diacritic stripping.** This one is load-bearing:

> Combining marks are stripped **only after Latin base letters.**

Stripping all combining marks correctly folds Latin diacritics. Applied to
Devanagari it destroys vowel signs, which are combining marks carrying meaning —
so Hindi queries normalised into different words and matching failed *silently*,
returning no results rather than erroring.

Normalisation round-trips NFKC → NFD → NFC, needed because Urdu Arabic-script
words decompose to a different character count than they compose to. German ß
folds to ss.

## `TextMatching.kt`

Shared primitives: `normalizeForLookup`, `splitQueryTokens`, `containsWord` /
`containsToken`, `levenshtein`, `similarity`.

These were extracted from the original `AIAgentManager` and kept
behaviour-identical — `WordBoundaryTest` asserts this by reflection, so the
legacy keyword router and the structured engine cannot drift apart on what
counts as a word match.

## `EntityResolver.kt`

Cross-language element-name and symbol resolution. Documented in [NLU](nlu) since
it feeds the planner, but it also contributes exact matches to the retrieval
fusion below.

## `HybridRetriever.kt`

Fuses exact structured matches with lexical BM25 hits.

```kotlin
object RetrievalWeights {
    const val STRUCTURED = 1.0
    const val LEXICAL    = 0.55
    const val FUZZY      = 0.25
    const val MIN_ANSWERABLE = 0.30
}
```

An exact entity match outweighs any lexical hit; lexical hits accumulate onto an
existing structured match rather than competing with it. `best()` returns the top
hit only if it clears `MIN_ANSWERABLE`, otherwise nothing — the engine declines
rather than returning its least-bad guess.

`buildCorpus()` assembles roughly **780 documents at runtime** from
`KnowledgeStore` (118 elements) plus every `DatasetIndex` row. Nothing is shipped
as a prebuilt index — it is constructed from the same live data the rest of the
engine reads, so it can never fall out of sync with it.

## `RetrievalService.kt`

Process-wide singleton (`get(assets)`). Caches the `Bm25Index` and
`EntityResolver` per language, and builds the shared alias table once from every
`elements_{lang}.json`.

That last part is what makes cross-language resolution work: a German element
name resolves even when the app is running in English, because all twelve name
sets are in the alias table regardless of the active language.

The cache is invalidated when the active language changes — `AIAgentManager`
tracks `cachedEngineLanguage` and `cachedEnginePolicy` and rebuilds the engine
when either moves.

## Why lexical retrieval is the right call here

Worth stating, since "no embeddings" reads as a limitation:

The corpus is small (~780 documents), highly structured, and full of proper
nouns, symbols and numbers — exactly the content BM25 handles well and dense
retrieval handles poorly. The questions are mostly literal. And the alternative
costs a model in the APK, inference latency on every query, and a class of
failure where a plausible-but-wrong passage scores highly.

Retrieval is the fallback for a system that resolves most questions to an exact
field lookup. BM25 is a good fit for that role.
