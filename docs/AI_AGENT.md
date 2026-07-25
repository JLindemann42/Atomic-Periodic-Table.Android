# AI Agent — Technical Documentation

The AI Agent is a fully on-device chemistry assistant built into the Atomic Periodic Table app.  
It requires **no cloud API**, works offline, and answers questions in the user's language.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [File Reference](#file-reference)
3. [What the structured engine adds](#what-the-structured-engine-adds)
4. [AIAgentManager](#aiagentmanager)
5. [AIPersonality](#aipersonality)
6. [AIRateLimiter](#airatelimiter)
7. [AILearningManager](#ailearningmanager)
8. [MolarMassCalculator](#molarmassecalculator)
9. [LocalKnowledgeManager](#localknowledgemanager)
10. [Retrieval (HybridRetriever)](#retrieval-hybridretriever)
11. [ChatHistoryManager](#chathistorymanager)
12. [Data Models](#data-models)
13. [Language Support](#language-support)
14. [Chat History & Persistence](#chat-history--persistence)
15. [Rate Limiting & Tiers](#rate-limiting--tiers)
16. [Adding a New Language](#adding-a-new-language)
17. [Adding New Knowledge](#adding-new-knowledge)
18. [Testing](#testing)

---

## Architecture Overview

The agent has two layers. A **structured engine** turns a question into an executable query over a
typed index of the app's data; anything it cannot express falls through to the original
**keyword handlers**, which are unchanged.

```
User Input
    │
    ▼
AIAgentManager.generateResponse()
    │
    ├── Language auto-detection  (detectResponseLanguage)
    ├── Rate limit check         (AIRateLimiter)
    ├── Active quiz answer?      (handleQuizAnswer)
    │
    ├── STRUCTURED ENGINE        (AiEngine) ──────────────────────────────┐
    │     1. Tokenize             (Tokenizer: Han bigrams, Arabic, …)     │
    │     2. Resolve entities     (EntityResolver, 12 languages)          │
    │     3. Resolve fields       (FieldResolver, from localized labels)  │
    │     4. Extract operators    (OperatorExtractor)                     │
    │     5. Plan                 (QueryPlanner) ── low confidence ───────┤
    │     6. Execute              (QueryExecutor over KnowledgeStore)     │
    │     7. Compose + cite       (AnswerComposer → ChatAction chips)     │
    │                                                                    │
    ├── KEYWORD HANDLERS  ◄──────────────── deferred queries ────────────┘
    │       ├── Trends, molar mass, superlative, safety, formula, …
    │       ├── Element detail   (handleElementContextQuery)
    │       └── General          (LocalKnowledgeManager, AIPersonality)
    │
    └── Fallback retrieval       (HybridRetriever: BM25 over live app data)

Chat persistence (PRO, signed-in users only)
    └── ChatHistoryManager → Firebase Firestore
```

The engine claims a query **only** when it has positive evidence it can do better: an operator
(threshold, superlative, aggregation, unit request), several elements to compare, a slot inherited
from the previous turn, or a resolved field whose value is genuinely absent. Everything else
returns null and behaves exactly as it did before.

---

## File Reference

| File | Role |
|------|------|
| `AIAgentManager.kt` | Entry point. Rate limiting, language detection, engine bridge, legacy handlers. |
| `AIPersonality.kt` | Localised response strings and formatting helpers. |
| `AIRateLimiter.kt` | Daily message quota per subscription tier. |
| `AILearningManager.kt` | Tracks user interests to personalise responses. |
| `MolarMassCalculator.kt` | Parses chemical formulas and computes molar mass. |
| `LocalKnowledgeManager.kt` | Resolves queries against constants, equations, ions, geology, indicators, and dictionary. |
| `ChatHistoryManager.kt` | Saves and loads chat sessions to/from Firebase Firestore. |

### `ai/data/` — the typed index

| File | Role |
|------|------|
| `ElementSource.kt` | Boundary that keeps `org.json` out of the testable core. `MapElementSource` backs unit tests. |
| `AssetElementSource.kt` | The only class in the engine touching `org.json`. |
| `ValueParser.kt` | Turns unit-suffixed strings (`"2743 (K)"`, `"160-350 (MPa)"`, `"8.0 × 10^4"`) into typed values. |
| `Quantity.kt` | `Quantity` and the `FieldValue` sealed type, including the explicit `Missing` case. |
| `FieldRegistry.kt` | ~80 queryable fields: JSON keys, units, categories, localized labels, deep-link targets. |
| `SeriesCanon.kt` | Canonicalises the 15 `element_group` spellings into 11 series. |
| `IsotopeParser.kt` | Reads the 42-slot isotope block and normalises 47 half-life unit spellings. |
| `UnitConverter.kt` | Multiplicative and affine unit conversion. |
| `KnowledgeStore.kt` | The index itself, plus per-field coverage counts. |
| `DatasetIndex.kt` | Every non-element dataset (dictionary, equations, constants, Poisson, geology, ions, indicators, electrode series, solubility, units) in one searchable form. |

### `ai/retrieval/` — search

| File | Role |
|------|------|
| `Tokenizer.kt` | Multilingual tokenisation. Han unigrams + bigrams, Arabic folding, Devanagari-safe. |
| `Bm25Index.kt` | Okapi BM25 over a corpus built at runtime from live app data. |
| `EntityResolver.kt` | Which elements a query names, with the short-symbol collision guard. |
| `TextMatching.kt` | Shared `levenshtein` / `hasKeyword` / normalisation helpers. |
| `HybridRetriever.kt` | Fuses exact entity matches with BM25. |
| `RetrievalService.kt` | Builds and caches the index process-wide. |

### `ai/nlu/`, `ai/core/`, `ai/exec/`, `ai/compose/`

| File | Role |
|------|------|
| `nlu/Lexicon.kt` | Operator vocabulary across 12 languages: comparators, superlatives (max and min kept separate), aggregations, subsets, units. |
| `nlu/FieldResolver.kt` | Query text → field id, using the app's existing localized labels as free aliases. |
| `nlu/OperatorExtractor.kt` | Pulls thresholds, subsets, aggregations, top-N and unit requests out of a query. |
| `nlu/QueryPlanner.kt` | Builds a `QueryPlan`, or declines. |
| `core/QueryPlan.kt` | `Intent`, `Filter`, `Aggregation` and the plan itself. |
| `core/ExecutionResult.kt` | Result types, including `NoData` with coverage. |
| `core/DialogueState.kt` | Multi-turn memory driven by slot-emptiness inheritance. |
| `core/StringProvider.kt` | Localization seam so the engine never needs a `Context` in tests. |
| `core/AiEngine.kt` | Plan → execute → compose. |
| `exec/QueryExecutor.kt` | Runs a plan across all 118 elements. |
| `compose/AnswerComposer.kt` | Renders results within the chat renderer's `###` / `**bold**` limits, with citations. |
| `compose/ChatAction.kt` | Tappable source chips and their codec. |
| `compose/DeepLinkNavigator.kt` | Opens the screen a citation points at. |

All files live under `app/src/main/java/com/jlindemann/science/ai/`.

---

## What the structured engine adds

| Capability | Example |
|---|---|
| Filter across the table | *"which transition metals melt above 2000 °C"* |
| Rank a subset | *"top 5 densest nonmetals"* |
| Aggregate | *"average electronegativity of the halogens"* |
| Count | *"how many noble gases are there"* |
| Convert units | *"melting point of gold in Fahrenheit"* |
| Address a banked field | *"second ionization energy of gold"* |
| Follow up | *"and its density?"* — works in all 12 languages with no pronoun lists |
| Say it has no data | *"vickers hardness of helium"* → says so, and reports the field is recorded for 45 of 118 elements |

Every answer names its sources and offers a chip that opens the screen the figure came from.

---

## AIAgentManager

`class AIAgentManager(context: Context?)`

The central class. Instantiate once per screen and hold it for the lifetime of the AI panel.

### Initialisation

```kotlin
val agent = AIAgentManager(context)
// Call once before the first message:
lifecycleScope.launch { agent.initialize() }
```

`initialize()` reads the persisted language from `SharedPreferences` (key `ai_agent_language` in
`ai_agent_settings`), falls back to the device locale, then loads element data and the
cross-language element map.

### Key public API

| Method | Description |
|--------|-------------|
| `suspend initialize()` | Load element data and cross-language map. Must be called before `generateResponse`. |
| `suspend generateResponse(userMessage, contextElement?)` | Returns a `ChatMessage` with the AI reply. |
| `suspend setLanguage(language: String)` | Switch active language. Persists to prefs. |
| `getActiveLanguage(): String` | Returns the current language code (e.g. `"sv"`). |
| `setConversationHistory(messages)` | Restore a previous session's messages for context. |
| `addToConversationHistory(message)` | Append a single message to in-memory history. |
| `clearConversation()` | Reset context (element, history, quiz state). |

### Language auto-detection

Every call to `generateResponse` runs `detectResponseLanguage(query)` before routing.  
It scores each word in the query against `localizedElementLanguageMap` (populated for all
supported languages at startup). If a non-active language scores higher, the agent silently
switches language, reloads element data, and persists the new language.

Example: user types **"Väte"** (Swedish for Hydrogen) while the app is set to English — the agent
detects Swedish, switches, and replies in Swedish.

### Element lookup

1. **Cross-language map** (`localizedElementMap`): built from all `elements_{lang}.json` files.
   Maps `normalizedName → englishKey`. Accent-insensitive (`normalizeForLookup` strips combining
   marks via NFD normalization).
2. **Fuzzy fallback**: Levenshtein distance against element names/symbols in the active-language
   JSON, with length-adaptive thresholds.

### Query routing (priority order)

1. Quiz answer collection (if a quiz is active)
2. Comparison (≥ 2 elements found → `handleComparison`)
3. Element lookup → element-context query
4. Specific query types: quiz, trends, molar mass, superlative, safety, formula
5. Series / block queries
6. General greeting / random fact
7. Fallback: `handleElementQuery` → `LocalKnowledgeManager`

### Companion object constants

```kotlin
companion object {
    private const val PREFS_NAME    = "ai_agent_settings"
    private const val PREF_LANGUAGE = "ai_agent_language"
}
```

---

## AIPersonality

`object AIPersonality`

Stateless singleton. All methods return localised strings by creating a
`ConfigurationContext` for the requested language, then reading from `strings.xml`.

| Method | Description |
|--------|-------------|
| `getGreeting(context, language)` | Friendly opening message. |
| `getEncouragement(context, language)` | Short positive acknowledgement. |
| `getNoDataResponse(context, language, query)` | "I couldn't find…" fallback. |
| `formatElementResponse(...)` | Single-property reply: "Hydrogen's boiling point is −253 °C". |
| `formatComprehensiveResponse(...)` | Multi-property overview card. |
| `getRandomFact(context, language)` | Returns a random chemistry fact. |

All strings are defined in `res/values/strings.xml` (and language-specific overrides, e.g.
`res/values-sv-rSE/strings.xml`).

---

## AIRateLimiter

`class AIRateLimiter(context: Context)`

Enforces daily message quotas stored in `ai_rate_limit_prefs` `SharedPreferences`.  
The key format is `msg_count_{year}_{month}_{day}` — quotas reset automatically each calendar day.

| Tier | Daily limit |
|------|-------------|
| Free | 30 messages |
| PRO | 200 messages |
| PRO+ | Unlimited |

```kotlin
if (rateLimiter.canSendMessage()) {
    rateLimiter.incrementMessageCount()
    // … generate response
}
val remaining = rateLimiter.getRemainingMessages()
```

---

## AILearningManager

`class AILearningManager(context: Context)`

Persists anonymous usage patterns in `ai_learning_prefs` `SharedPreferences`.  
**No user-provided text is stored** — only element and property names.

### Tracked data

| Key | Type | Description |
|-----|------|-------------|
| `element_interests` | `Map<String, Int>` | How many times each element was asked about. |
| `property_interests` | `Map<String, Int>` | How many times each property was asked about. |
| `technical_score` | `Float` [0–1] | Estimated preference for technical vs. general replies. |
| `session_count` | `Int` | Total number of sessions. |

### Key methods

| Method | Description |
|--------|-------------|
| `trackElementInterest(name)` | Increment count for an element. |
| `trackPropertyInterest(name)` | Increment count for a property. |
| `getPersonalizedGreeting()` | Returns a greeting mentioning the user's most-queried element, if any. |
| `incrementSession()` | Call once when a new AI session starts. |
| `saveData()` | Flush current state to SharedPreferences. |

---

## MolarMassCalculator

`class MolarMassCalculator(elementData: JSONObject?)`

Parses a chemical formula string and returns the molar mass in g/mol.

```kotlin
val calculator = MolarMassCalculator(elementData)
val mass = calculator.calculate("H2O")   // → 18.015
val mass = calculator.calculate("NaCl")  // → 58.44
```

**Parsing rules:**
- Respects case: `N` = Nitrogen, `Nh` = Nihonium.
- Supports nested groups: `Ca(OH)2`, `Al2(SO4)3`.
- Strips whitespace and common suffixes (e.g. `(s)`, `(l)`, `(aq)`).
- Returns `null` if the formula cannot be parsed or contains unknown symbols.

---

## LocalKnowledgeManager

`class LocalKnowledgeManager(context: Context)`

Resolves queries against the app's non-element data via lazy-loaded model lists.

### Data sources

| Source | Model class | Example queries |
|--------|-------------|-----------------|
| Physical constants | `Constants` / `ConstantsModel` | "speed of light", "Planck constant" |
| Equations | `Equation` / `EquationModel` | "ideal gas law", "Ohm's law formula" |
| Chemistry dictionary | `Dictionary` / `DictionaryModel` | "what is oxidation", "define electronegativity" |
| Indicators | `Indicator` / `IndicatorModel` | "pH indicator", "litmus" |
| Ions | `Ion` / `IonModel` | "sulfate ion", "ammonium" |
| Poisson values | `Poisson` / `PoissonModel` | "Poisson ratio for steel" |
| Geology | `Geology` / `GeologyModel` | "granite", "igneous rock" |

### Usage

```kotlin
val result: LocalKnowledgeManager.QueryResult? = localKnowledgeManager.resolve(query, activeLanguage)
if (result != null) {
    // result.response  — formatted answer string
    // result.topic     — category (e.g. "equations")
    // result.isTechnical — hint for response style
}
```

`resolve()` tries each resolver in priority order and returns the first non-null match.

---

## Retrieval (HybridRetriever)

`class HybridRetriever(store, datasets, index, entities, localized)`

Used as the fallback when neither the structured engine nor a keyword handler claims a query.

**This replaced an embedding-based RAG that could never work.** Its query embedder returned a
hash-seeded pseudo-random vector, so cosine similarity against the precomputed passage vectors
hovered near zero and could not clear the 0.65 threshold gating it; no `embed.tflite` model was
ever shipped either. The 42 MB of `assets/data/**` supporting it has been deleted — every
per-language `passages.jsonl` was byte-identical to the `description` field already present in
`elements_{lang}.json`.

### Corpus

Built **at runtime** from live app data, so it cannot drift from what the app displays and costs
nothing in the APK. Roughly 780 documents:

| Prefix | Source | Count |
|---|---|---|
| `element:` | `elements_{lang}.json` | 118 |
| `dictionary:` | `DictionaryModel` | 127 |
| `ion:` | `IonModel` | 118 |
| `equation:` | `EquationModel` | 77 |
| `constant:` | `ConstantsModel` | 59 |
| `poisson:` | `PoissonModel` | 52 |
| `geology:` | `GeologyModel` | 25 |
| `solubility:` | `SolubilityData` | 13 |
| `electrode:` | `SeriesModel` | 18 |
| `indicator:` | `IndicatorModel` | 4 |
| `unit:` | `UnitCatalog` | ~60 |

### Scoring

Okapi BM25 with `k1 = 1.2`, `b = 0.6`. `b` sits below the usual 0.75 because the corpus is
deliberately heterogeneous — a three-token Poisson row beside a two-hundred-token element
description — and a lower `b` reduces the short-document bias.

There is **no stopword list**. BM25's IDF already drives terms that appear in most documents
toward zero weight, which is more robust than maintaining stopwords in twelve languages.

Final score fuses three signals, weighted in `RetrievalWeights`:

```
score = 1.00 x structured + 0.55 x lexical + 0.25 x fuzzy
```

`best()` returns null below `MIN_ANSWERABLE`, so a weak match is never presented as an answer.

### Tokenisation

One entry point is used for both indexing and querying; any asymmetry would silently destroy
recall.

- **Han (zh)** — emits every character *and* every adjacent bigram, so `过渡金属` yields
  `过, 渡, 金, 属, 过渡, 渡金, 金属`. This is the standard dictionary-free CJK technique and needs
  no dependency. Note that zh element names are single characters that recur inside ordinary words
  (`金` is gold, but also opens `金属`, "metal"), so `EntityResolver` only treats a single Han
  character as an element mention when it is not absorbed into a longer Han run.
- **Arabic script (ur)** — strips harakat and ZWNJ, folds `ي→ی`, `ك→ک`, `أإآ→ا`.
- **Devanagari (hi)** — whitespace split with vowel signs preserved. Matras are combining marks
  but, unlike Latin accents, are **not** optional: folding them changes the word.
- **Latin** — one symmetric suffix fold on tokens longer than five characters.

## ChatHistoryManager

`object ChatHistoryManager`

Persists chat sessions to **Firebase Firestore** for signed-in Google users.  
Anonymous users get no persistence — history is in-memory only.

### Firestore layout

Sessions are stored as an **array field on the user document**, not as a subcollection, and the
array is trimmed to the 20 most recent sessions on every write.

```
users/{uid}
  └── chats[]  (newest 20)
        ├── id         : String
        ├── title      : String
  ├── timestamp  : Long (epoch ms)
  ├── language   : String  (e.g. "sv")
  └── messages[] : Array
        ├── id         : String (UUID)
        ├── text       : String
        ├── isFromUser : Boolean
        └── timestamp  : Long
```

### Key methods

| Method | Description |
|--------|-------------|
| `saveChatSession(session, onComplete)` | Upsert a session. Uses `session.id` as document ID if set. |
| `loadChatHistory(onLoaded)` | Load all sessions for the current user, ordered by recency. |
| `loadLatestChatSession(onLoaded)` | Load the most recent session. Note it reads the whole `chats` array and takes the first entry; there is no server-side limit. |

```kotlin
// Save
ChatHistoryManager.saveChatSession(session) { success, newId -> }

// Restore on sign-in
ChatHistoryManager.loadLatestChatSession { session ->
    session?.let { aiAgent.setConversationHistory(it.messages) }
}
```

---

## Data Models

### ChatMessage

```kotlin
data class ChatMessage(
    val id: String,          // UUID
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long      // epoch ms
) : Parcelable
```

### ChatSession

```kotlin
@Parcelize
data class ChatSession(
    val id: String = "",
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val language: String = "en"  // BCP-47 language tag
) : Parcelable
```

---

## Language Support

### Supported language codes

| Code | Language |
|------|----------|
| `af` | Afrikaans |
| `de` | German |
| `en` | English |
| `es` | Spanish |
| `fil` | Filipino |
| `fr` | French |
| `hi` | Hindi |
| `it` | Italian |
| `pt` | Portuguese |
| `sv` | Swedish |
| `ur` | Urdu |
| `zh` | Chinese |

Language availability is determined at runtime by `ElementDataLoader.getAvailableLanguages(assets)`,
which lists files matching `elements_*.json` in the assets root.

### How language switching works

1. User changes language in settings **or** the agent auto-detects a language from element names.
2. `AIAgentManager.setLanguage(code)` is called.
3. The new code is written to `SharedPreferences` (`ai_agent_language`).
4. `getElementDataByLanguage(code)` loads `assets/elements_{code}.json` (falls back to `en`).
5. `AIPersonality` methods create a `ConfigurationContext` with the new locale so all UI strings
   (property labels, section headers) are also in the correct language. Note this deliberately
   does **not** call `Locale.setDefault` — the chat language is auto-detected per message, and
   making it the process default would leak into every formatter in the app.
6. The cached `AiEngine` is discarded so its localized field aliases and BM25 index are rebuilt
   for the new language.

### Auto-detection

When the user types a message, `detectResponseLanguage(query)` scores every word against
`localizedElementLanguageMap` (built from all `elements_*.json` files). The language with the
highest cumulative score wins. On ties, the current active language is preferred to avoid
spurious switches.

---

## Chat History & Persistence

Chat history is only stored for users signed in with a Google account (Firebase Auth).

```
Sign-in detected
    └── ChatHistoryManager.loadLatestChatSession()
            └── Restore messages into AIAgentManager
                └── Apply session language via setLanguage()

User sends a message
    └── ChatHistoryManager.saveChatSession(currentSession)

Language changed
    └── Persist to SharedPreferences + save current session with new language
```

Offline / anonymous users: conversation history lives only in the `conversationHistory`
`MutableList` inside `AIAgentManager` for the duration of the session.

---

## Rate Limiting & Tiers

```
Free  → 30 messages / day
PRO   → 200 messages / day
PRO+  → Unlimited
```

Counts are stored per-day in `SharedPreferences` under the key
`msg_count_{year}_{month}_{day}` — they reset automatically on calendar rollover.

The limit check happens at the **start** of `generateResponse`, before any processing:

```kotlin
if (rateLimiter?.canSendMessage() == false) {
    return ChatMessage(text = "You've reached your daily limit of X messages…")
}
rateLimiter?.incrementMessageCount()
```

---

## Adding a New Language

1. Create `app/src/main/assets/elements_{code}.json`.  
   Each entry must follow the existing schema:
   ```json
   "hydrogen": {
     "element": "Waterstof",
     "short": "H",
     "element_atomic_number": "1",
     ...
   }
   ```
   The top-level key is always the English element name (lowercase). The `"element"` field holds
   the localised display name.

2. Optionally add `res/values-{code}/strings.xml` for translated UI strings used by
   `AIPersonality` (greeting, property labels, etc.).

3. Nothing else to add. The retrieval corpus is built at runtime from the element file and the
   in-app datasets, so there are no per-language passage or embedding files to maintain.

4. No code changes needed — `ElementDataLoader.getAvailableLanguages()` discovers the file
   automatically, and `loadCrossLanguageElementMap()` picks it up on next launch.

---

## Adding New Knowledge

### New element properties

1. Add the key to the relevant `elements_*.json` entries.
2. Declare it in `ai/data/FieldRegistry.kt` with its unit, category, an existing localized
   `labelRes`, and a deep-link target. That alone makes it filterable, rankable, comparable and
   citable — no handler changes required.
3. Run `python scripts/check_field_registry.py` to confirm the key exists in the real data and
   that nothing is left unreachable.

### New non-element topics (constants, equations, etc.)

Add rows to the corresponding model class (e.g. `EquationModel`) and ensure
`LocalKnowledgeManager.resolveEquation` (or the appropriate resolver) can score and return
the new entries.

### New searchable content

Nothing to generate. `HybridRetriever.buildCorpus` assembles the BM25 corpus at runtime from the
element table and every dataset in `DatasetIndex`, so new rows in a model class become searchable
as soon as they are added.

---

## Testing

The engine is designed to be testable on the JVM without Robolectric. Two seams make that work:

- **`ElementSource`** keeps `org.json` — a throwing stub in unit tests — on the Android side.
  Everything downstream operates on plain Kotlin collections.
- **`StringProvider`** replaces `Context` for localization, so executors and the composer can be
  driven by `FakeStrings` or by `TestStrings`, which reads the real `values/strings.xml`.

`RealCorpusTest` runs the whole index over all 118 shipped elements and asserts invariants that
fixtures cannot cover: every element resolves to a series, no sentinel value survives parsing, all
melting points land on one scale, and per-field coverage matches the data.

Run everything with:

```
./gradlew testDebugUnitTest
python scripts/check_field_registry.py
```

### Known data defects

`RealCorpusTest` documents three defects in `elements_*.json` as known-bad rather than working
around them silently:

- `lanthanum` and `cerium` record their **boiling** points in the melting-point fields (3737 K and
  3716 K); both therefore outrank tungsten in a "highest melting point" query.
- `gold` has 40 isotope entries and never marks Au-197 stable, so it is absent from the 69
  elements that report a stable isotope.

Update the assertions in `RealCorpusTest` when the data is corrected.
