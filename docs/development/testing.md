---
title: Testing
parent: Developer Guide
nav_order: 9
---

# Testing

## Running

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:testDebugUnitTest --tests "com.jlindemann.science.ai.core.AiEngineTest"
```

No device or emulator required — the JVM tests are genuinely fast.

## What is covered

44 test classes. The distribution is lopsided and worth being upfront about:

| Area | Classes | State |
|:--|--:|:--|
| `ai/` | 38 | Thorough |
| `quiz/` | 4 | Good |
| `utils/` | 1 | `ProPlusTimeUtil` only |
| Everything else | 0 | Untested |
| `androidTest/` | 1 | Stock generated example |

### The AI engine tests

```
ai/
├── AIAgentManagerTest, AiPublicSurfaceTest, LocalKnowledgeManagerTest
├── core/      AiEngineTest, AnswerComposerTest, EntitlementTest,
│              PropertyCoverageTest, StringCoverageTest, StringFormatTest,
│              ComplexQueryTest, CompoundQuestionTest, IsotopeComparisonTest,
│              AlloyAndMultiAspectTest, RichAnswerTest, EngineCoverageTest,
│              AbundanceUnitsTest
├── corpus/    CorpusTest, CorpusCoverageTest, CorpusSmokeTest,
│              AnswerQualityTest, QuestionCorpus, CorpusHarness
├── retrieval/ Bm25IndexTest, EntityResolverTest, TokenizerTest,
│              WordBoundaryTest
├── data/      FieldRegistryTest, ValueParserTest, UnitConverterTest,
│              KnowledgeStoreTest, IsotopeParserTest, ChemistryMathTest,
│              SeriesCanonTest, RealCorpusTest
└── cards/     CardFoundationsTest, CardReachabilityTest
```

The `corpus/` tests are the distinctive ones: a corpus of real questions with
expected answers, run end-to-end through the engine, asserting not just that a
question routes to the right intent but that the answer is *correct and well
formed*. See [The corpus](../ai/corpus).

`CardReachabilityTest` asserts that every `ChatCardKind` is actually reachable
from some query — a guard against adding a card type that nothing can ever
trigger.

`PropertyCoverageTest` and `StringCoverageTest` assert that every field in
`FieldRegistry` is answerable and has a resolvable label in every language.
These catch the common failure of adding a field and forgetting a translation.

## The seam that makes it testable

Two decisions let a subsystem this large be unit-tested on the JVM:

**`StringProvider`.** The engine never touches `Context` for localisation. It
takes a `StringProvider`, with `AndroidStrings` in production and `FakeStrings`
in tests (`ai/core/StringProvider.kt`, `ai/core/TestStrings.kt`).

**Plain Kotlin maps instead of `org.json`.** Under JVM unit tests, `org.json` is
a stub whose methods throw. The engine's data layer works in
`Map<String, Any?>`, and exactly one class — `ai/data/AssetElementSource.kt` —
touches JSON at all.

The same constraint is why `ChatActionCodec` and `ChatCardCodec` serialise with a
control-character record format rather than JSON.

`ai/data/TestAssets.kt` provides fixture data.

## What is not covered

Stated plainly so you know where you are working without a net:

- **Preferences** — all 21 wrapper classes
- **Sync** — `ProgressSyncManager`'s merge logic, `NotesSyncManager`'s gate.
  This is the highest-value gap: the merge is intricate and a bug there loses or
  corrupts user progress.
- **Auth** — `AuthManager`
- **Billing** — `BillingManager`
- **Data loading** — `ElementDataLoader` directly (though the `ai/data` tests
  parse the same asset files, so schema breakage does get caught)
- **All UI** — every activity, fragment, adapter and custom view
- **Widgets** — all five providers

`app/src/androidTest/` contains only the generated `ExampleInstrumentedTest`.
There is no Espresso or UI test coverage.

## Adding tests

**For the AI engine**, follow the existing pattern: construct the engine with
`FakeStrings` and fixture assets, no Android dependencies. If you add a field to
`FieldRegistry`, `PropertyCoverageTest` and `StringCoverageTest` will fail until
it is answerable and labelled in every language — that is the intended behaviour.

**For the quiz generators**, `quiz/CurriculumWiringTest` is the model: assert the
generator produces well-formed questions with plausible distractors across a
range of elements.

**For sync logic**, the merge functions in `ProgressSyncManager` are mostly pure
given their inputs. Extracting the merge into a testable function and covering it
would be a genuinely valuable contribution.

**For UI**, there is no existing harness. Adding Robolectric or Espresso is a
larger decision than a single test.

## CI

There is no CI workflow in the repository — no `.github/workflows`. Tests are
run locally.
