# Implementation Plan - AI Agent Fixes

Address reported issues with the AI agent including repetitive/irrelevant answers, incorrect element identification (Argon/Is collision), and inconsistent property handling.

## Proposed Changes

### AI Agent Core Logic

#### [AIAgentManager.kt](file:///C:/Users/jonat/OneDrive/Dokument/TARDIS_Github/Atomic-Periodic-Table.Android/app/src/main/java/com/jlindemann/science/ai/AIAgentManager.kt)

- **Fix Argon Collision**: Update `findCrossLanguageElementKey` and `findMultipleElements` to include the same "common word" protection used in `findElementKeyByQuery`. This prevents Swedish "Är" (is) from being normalized to "ar" and matched as Argon.
- **Improve "More" Handling**: Add "mer", "more", "berätta mer", etc., to `isAffirmative` keywords. This ensures that clicking a "More" suggestion chip or typing "Mer" triggers `provideNewInformation` for the current element.
- **Enhanced Property Fallback**: Update the `else` block in `handleElementContextQuery`. Instead of defaulting to `provideNewInformation` (which gives random properties), it will now:
    1. Try to find the requested property via the RAG agent (`ragAgent?.query`).
    2. If RAG fails, return a "no data" message instead of random properties.
- **Concept vs Property Priority**: Reorder branches in `generateResponse` so that specific property queries (like isotopes) are checked before general concept definitions if a `targetElementKey` is present.
- **Expand Localized Keywords**: Add missing Swedish keywords for properties:
    - `supraledning`, `supraledare`, `supraledande` -> Electrical/Magnetic properties.
    - `värmekapacitet`, `specifik värme` -> Thermal properties.
    - `radioaktivitet`, `radioaktivt` -> Radioactivity.

---

### Local Knowledge Retrieval

#### [LocalKnowledgeManager.kt](file:///C:/Users/jonat/OneDrive/Dokument/TARDIS_Github/Atomic-Periodic-Table.Android/app/src/main/java/com/jlindemann/science/ai/LocalKnowledgeManager.kt)

- **Fix Aggressive Scoring**: Update `scoreMatch` to avoid matching short strings (like "me") as substrings inside other words (like "mer") without word boundaries.
    - For candidates with length $\le 2$, require an exact match or word boundaries.

---

## Verification Plan

### Automated Tests
- I will add new test cases to `AIAgentManagerTest.kt` (or create a new test file) to verify:
    - "Mer" is recognized as affirmative.
    - "Är" is NOT identified as Argon in a sentence.
    - `LocalKnowledgeManager` does not match "mer" to "Electron mass" (symbol "me").
- Run tests using:
    ```bash
    ./gradlew testDebugUnitTest --tests com.jlindemann.science.ai.*
    ```

### Manual Verification
- I will use `adb shell input text` to simulate the reported queries on a running device (if available) or verify via logs.
- Queries to verify:
    - "Tyngsta grundämnet?" followed by "Mer" (should give more Oganesson info, not electron mass).
    - "Är väte radioaktivt" (should NOT mention Argon).
    - "Väte supraledningpunkt" (should give superconductivity info or a proper "no data" message, not density).
    - "Isotoper för väte" (should give hydrogen isotopes, possibly with definition).
