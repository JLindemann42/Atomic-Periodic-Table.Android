---
title: Extending the engine
parent: AI Agent
nav_order: 9
---

# Extending the engine

Concrete checklists. Each ends with the test that will tell you whether you got
it right.

## Make a new element field answerable

The registry is the single point of extension — adding a spec gets you query
resolution, unit conversion, deep linking and tier gating at once.

1. **Add the data** to all twelve `assets/elements_*.json` files. Use a script
   (see [Data Pipeline](../data-pipeline)); use `"---"` where no authoritative
   value exists.

2. **Register the field** in `ai/data/FieldRegistry.kt`:

   ```kotlin
   add(spec("thermal_conductivity", "thermal_conductivity",
            FieldKind.NUMERIC, FieldCategory.THERMO,
            R.string.thermal_conductivity_colon,
            Dimension.THERMAL_CONDUCTIVITY, "W/(m·K)",
            allowsRange = false,
            tier = Tier.FREE))
   ```

   `allowsRange = true` only if `"2.1–2.4"` is legitimate for this field —
   otherwise a dash is a data error and parsing it as a range corrupts
   comparisons silently.

3. **Add the label** to `values/strings.xml` and every
   `values-{locale}/strings.xml`. This is not just display: `FieldResolver`
   derives the field's query aliases from the localised label, so the
   translation *is* the NLU vocabulary.

4. **Check the parser** handles the value format. If it uses a form
   `ai/data/ValueParser.kt` does not know — a new bracket convention, an unusual
   unit — extend the parser and add a `ValueParserTest` case.

5. **Add a unit dimension** to `ai/data/UnitConverter.kt` if the field's
   dimension is new. Remember affine conversions (offset + scale) do not belong
   in the multiplicative table.

6. **If the field is localised**, add it to the `LocalizedView` overlay list in
   `KnowledgeStore`. Only seven fields are localised today; miss this and the
   English value shows in every language.

7. **Run the tests.** `PropertyCoverageTest` asserts the field is answerable;
   `StringCoverageTest` asserts it has a resolvable label in every language.
   Both failing is the expected first result.

## Add a dataset

For a new reference table you want queryable.

1. Add the model pair in `model/` (`X.kt` + `XModel.kt`), following the existing
   convention.
2. Flatten it into `ai/data/DatasetIndex.build()` as `DatasetRow`s. It is picked
   up by `HybridRetriever.buildCorpus()` automatically.
3. Add a `DeepLinkTarget` in `ai/compose/ChatAction.kt` and map it to the
   activity in `ai/compose/DeepLinkNavigator.kt`, so citations open the table.
4. Add the table's name to `Lexicon.DATASET_GLOSS` for each language, so a
   foreign-language question can find it.

Dataset content stays English — see the limitation note in
[Data layer](data-layer#datasetindexkt).

## Add a language

App-level steps are in
[Localisation](../development/localization#adding-a-language). For the assistant:

1. **`ai/nlu/Lexicon.kt`** — the bulk of the work. Every vocabulary group needs
   the new language: comparators, superlatives, aggregations, series names,
   category words, isotope/safety/spectrum markers, the defer-to-personality
   deny-list, and `DATASET_GLOSS`.

2. **Element aliases** come free once `assets/elements_{lang}.json` exists —
   `RetrievalService` builds the alias table from the files it finds.

3. **Word order.** If the language is postpositional (number before comparator,
   as in Hindi, Urdu and Chinese), verify `OperatorExtractor` handles it. The
   bidirectional scan is already there; confirm it fires.

4. **Script handling.** Check `ai/retrieval/Tokenizer.kt`:
   - Space-free script? It may need the Han-style unigram+bigram treatment.
   - Combining marks that carry meaning? `TextMatching.normalizeForLookup`
     strips them only after Latin base letters — verify the new script is not
     caught by that, since the failure mode is *silent* (queries normalise to
     different words and simply match nothing).
   - Multiple encodings of the same letter? It may need folding like Arabic.

5. **Field labels** in `strings.xml` give you field aliases for free.

6. **Add corpus entries** in `ai/corpus/QuestionCorpus.kt` so answer quality in
   the new language is actually measured — see [The corpus](corpus).

## Add an intent

The heaviest change, touching every layer.

1. Add the value to `Intent` in `ai/core/QueryPlan.kt`.
2. Add a branch in `ai/nlu/QueryPlanner.plan()`. **Placement in the cascade
   matters** — an earlier guard claiming the query prevents your branch running
   at all. Put it after the more specific intents and before the general ones.
3. Add an `ExecutionResult` variant in `ai/core/ExecutionResult.kt`.
4. Add the execution method in `ai/exec/QueryExecutor.kt`. Check entitlements
   *before* reading any value.
5. Add rendering in `ai/compose/AnswerComposer.composeBody()`. Only `###`
   headings and `**bold**` are supported by the chat renderer.
6. Add citations via `citationsFor()`.
7. Optionally map a card in `CardSelector`.
8. Add tests — an `AiEngineTest` case, and corpus entries for answer quality.

## Add a chat card

See [Chat cards](cards#adding-a-card).

## Things that will trip you up

**Confidence gating.** A new branch producing a low-confidence plan will be
discarded silently and the query will fall through to the legacy router. If your
intent never fires, check the threshold before checking your logic.

**Guard ordering in `QueryPlanner`.** The cascade is ~1,640 lines and order is
load-bearing. Your branch may never be reached.

**`org.json` throws in tests.** Do not add JSON parsing outside
`AssetElementSource`.

**Localisation coverage tests are strict, on purpose.** Adding a field without
translating its label fails the build. That is the mechanism working.

**Two engines.** If a query is handled by the legacy keyword router in
`AIAgentManager` rather than the structured engine, changes to the structured
engine will not affect it. Check `Lexicon.DEFER_TO_PERSONALITY` and
`UNBACKED_CONCEPTS` first.
