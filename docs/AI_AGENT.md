# AI Agent — Technical Documentation

The AI Agent is a fully on-device chemistry assistant built into the Atomic Periodic Table app.  
It requires **no cloud API**, works offline, and answers questions in the user's language.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [File Reference](#file-reference)
3. [AIAgentManager](#aiagentmanager)
4. [AIPersonality](#aipersonality)
5. [AIRateLimiter](#airatelimiter)
6. [AILearningManager](#ailearningmanager)
7. [MolarMassCalculator](#molarmassecalculator)
8. [LocalKnowledgeManager](#localknowledgemanager)
9. [TfliteRagAgent](#tfliteragagent)
10. [ChatHistoryManager](#chathistorymanager)
11. [Data Models](#data-models)
12. [Language Support](#language-support)
13. [Chat History & Persistence](#chat-history--persistence)
14. [Rate Limiting & Tiers](#rate-limiting--tiers)
15. [Adding a New Language](#adding-a-new-language)
16. [Adding New Knowledge](#adding-new-knowledge)

---

## Architecture Overview

```
User Input
    │
    ▼
AIAgentManager.generateResponse()
    │
    ├── Language auto-detection  (detectResponseLanguage)
    ├── Rate limit check         (AIRateLimiter)
    ├── Comparison query?        (findMultipleElements → handleComparison)
    ├── Element lookup           (findElementByQuery → localizedElementMap)
    │       └── assets/elements_{lang}.json
    ├── Query routing
    │       ├── Quiz             (handleQuizQuery)
    │       ├── Trends           (handleTrendsQuery)
    │       ├── Molar mass       (MolarMassCalculator)
    │       ├── Superlative      (handleSuperlativeQuery)
    │       ├── Safety           (handleSafetyQuery)
    │       ├── Formula          (handleFormulaQuery)
    │       ├── Element detail   (handleElementContextQuery)
    │       ├── Series / Block   (handleSeriesQuery / handleBlockQuery)
    │       └── General          (LocalKnowledgeManager, AIPersonality)
    └── Response formatting      (AIPersonality)

Chat persistence (signed-in users only)
    └── ChatHistoryManager → Firebase Firestore
```

---

## File Reference

| File | Role |
|------|------|
| `AIAgentManager.kt` | Core orchestrator. Routing, element lookup, language detection. |
| `AIPersonality.kt` | Localised response strings and formatting helpers. |
| `AIRateLimiter.kt` | Daily message quota per subscription tier. |
| `AILearningManager.kt` | Tracks user interests to personalise responses. |
| `MolarMassCalculator.kt` | Parses chemical formulas and computes molar mass. |
| `LocalKnowledgeManager.kt` | Resolves queries against constants, equations, ions, geology, indicators, and dictionary. |
| `TfliteRagAgent.kt` | Lightweight on-device RAG (retrieval-augmented generation) using precomputed embeddings. |
| `ChatHistoryManager.kt` | Saves and loads chat sessions to/from Firebase Firestore. |

All files live in `app/src/main/java/com/jlindemann/science/ai/`.

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
| Free | 10 messages |
| PRO | 50 messages |
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

## TfliteRagAgent

`class TfliteRagAgent(context: Context, language: String? = null)`

Lightweight retrieval-augmented generation (RAG) that runs entirely on-device.  
Loads precomputed embeddings and passage texts from `assets/data/` (or `assets/data/{lang}/`).

### Asset layout

```
assets/
└── data/
    ├── embeddings.json      # float[][] – one vector per passage
    ├── meta.json            # [{id, title}, …]
    ├── passages.json        # [{id, title, text}, …]
    ├── embed.tflite         # optional – real embedding model
    └── {lang}/
        ├── embeddings.json  # language-specific override
        ├── passages.json
        └── embed.tflite
```

### Query

```kotlin
val results: List<TfliteRagAgent.SearchResult> = ragAgent.query("ionisation energy", topK = 3)
// SearchResult(id, title, text, score)
```

Cosine similarity (dot product on normalised vectors) is used for ranking.  
If no `embed.tflite` is present, a keyword-hash stub embedder is used — sufficient for keyword
retrieval but not semantic similarity. Drop a real TFLite sentence-encoder model into
`assets/data/` to enable true semantic search.

### Language switching

```kotlin
ragAgent.setLanguage("sv")
ragAgent.supportsLanguage("sv")  // true if both embeddings and passages loaded
```

---

## ChatHistoryManager

`object ChatHistoryManager`

Persists chat sessions to **Firebase Firestore** for signed-in Google users.  
Anonymous users get no persistence — history is in-memory only.

### Firestore path

```
users/{uid}/chats/{sessionId}
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
| `loadLatestChatSession(onLoaded)` | Load only the most recent session (efficient — uses `limit(1)`). |

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
   (property labels, section headers) are also in the correct language.
6. `TfliteRagAgent.setLanguage(code)` loads language-specific embeddings and passages from
   `assets/data/{code}/` if available.

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
Free  → 10 messages / day
PRO   → 50 messages / day
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

3. Optionally add `assets/data/{code}/passages.json` and `assets/data/{code}/embeddings.json`
   for language-specific RAG content.

4. No code changes needed — `ElementDataLoader.getAvailableLanguages()` discovers the file
   automatically, and `loadCrossLanguageElementMap()` picks it up on next launch.

---

## Adding New Knowledge

### New element properties

Add the property key to the relevant `elements_*.json` entries and update
`handleElementContextQuery` / `handleComparison` in `AIAgentManager.kt` to handle the new key.

### New non-element topics (constants, equations, etc.)

Add rows to the corresponding model class (e.g. `EquationModel`) and ensure
`LocalKnowledgeManager.resolveEquation` (or the appropriate resolver) can score and return
the new entries.

### New RAG passages

Add entries to `assets/data/passages.json` (and corresponding vectors to `embeddings.json`).  
Re-run `scripts/build_embeddings.py` to regenerate the embedding file if you have a model.
