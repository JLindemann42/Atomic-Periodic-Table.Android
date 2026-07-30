---
title: NLU
parent: AI Agent
nav_order: 3
---

# Natural-language understanding

Package: `ai/nlu/` — five files, roughly 3,500 lines, the bulk of which is
multilingual vocabulary.

The job of this layer is to turn free text into a `QueryPlan`: which elements,
which fields, which operators, which intent, and how confident.

## `Lexicon.kt`

Around 1,490 lines of hand-maintained vocabulary across twelve languages. This
is the single largest cost of adding a language to the assistant.

| Group | Contents |
|:--|:--|
| `GREATER` / `LESS` | Comparator words — "more than", "över", "más de" |
| `MOST` / `LEAST` | Superlative direction |
| `THAN_WORDS` | Comparison connectives |
| `AVERAGE` / `SUM` / `COUNT` / `MEDIAN` | Aggregation triggers |
| `SERIES_WORDS` | Family names → `SeriesId` (noble gases, halogens, lanthanides) |
| `CATEGORY_WORDS` | Element category vocabulary |
| `ISOTOPE_WORDS`, `SAFETY_WORDS`, `SPECTRUM_WORDS` | Topic markers |
| `COMMON_COMPOUNDS` | Formula shortcuts |
| `DATASET_GLOSS` | Foreign dataset names → English search terms |
| `DEFER_TO_PERSONALITY` | Deny-list: greetings, small talk, quiz interactions |
| `NARRATIVE_INTENTS` | Question shapes the structured engine will not attempt |
| `UNBACKED_CONCEPTS` | Concepts with no backing field — reactivity, similarity |

The last three are as important as the first ones. They are how the engine knows
what *not* to answer. A question about why an element is reactive has no field
behind it, so it is routed to the legacy personality layer rather than answered
with a number that would look authoritative and mean nothing.

## `EntityResolver.kt`

566 lines resolving element mentions across languages —
`resolveAll(query, limit): List<ElementMatch>`.

Element names collide badly across twelve languages, and most of this file is
guards against specific false positives:

**Short-symbol capitalisation.** A bare `"In"` is indium only when capitalised
as a symbol; lowercase `"in"` is an English preposition.

**Foreign-name floor.** A match on a name from a language other than the active
one needs a higher score, so a stray cognate does not outrank the obvious
reading.

**Han absorption.** Chinese element names are short and embed inside longer
words; matches are rejected when absorbed by a longer token.

**Syncope and inflection.** Suffix-tolerant matching for languages that inflect
element names, without letting the tolerance swallow a different element.

Aliases come from every `elements_{lang}.json` at once — built once per process
by `RetrievalService` — so a German element name resolves even when the app is
in English.

## `FieldResolver.kt`

`resolveAll(query, limit): List<FieldMatch>` — which property is being asked
about.

The alias source is the clever part: **the app's own localised field labels**.
`FieldRegistry` carries a `labelRes` per field, and `FieldResolver` reads the
translated string for the active language and uses it as a query alias.

Translating a field's UI label therefore teaches the assistant that word for
free. There is no separate NLU translation pass, and no way for the label and
the alias to drift apart.

On top of that it layers colloquial forms ("how heavy" → atomic mass), cognates,
the canonical field id, and per-language suffix-inflection tolerance.

## `OperatorExtractor.kt`

`extract(rawQuery): Operators` — pulls the structural parts out of the question:

- Comparators and their thresholds ("above 15 g/cm³")
- Subset filters ("among the transition metals")
- Aggregation ("average", "how many")
- Superlative direction and top-N ("the three densest")
- Target unit ("in Fahrenheit")
- Ordinals ("the second heaviest")
- Ranges ("between 1000 and 2000 K")

**Postpositional languages are handled explicitly.** Hindi, Urdu and Chinese put
the number before the comparator rather than after it, so a naive
"comparator then number" scan finds nothing. The extractor scans in both
directions per language.

## `ClauseSplitter.kt`

`split(query): List<String>?` — splits compound questions so both halves get
answered.

The restraint matters more than the splitting. It only splits when the tail
genuinely reads as its own question. "Density of gold and how does it compare to
lead" splits; "elements with high density and low melting point" is one question
with two conditions and must not.

## `QueryPlanner.kt`

~1,640 lines, the largest file in the subsystem. It orchestrates everything above
into a `QueryPlan` through a long **ordered cascade of guards**. Order is
load-bearing — an earlier guard claiming a query prevents a later, wronger
interpretation.

Roughly, in order:

1. Defer-to-personality check (greetings, small talk, quiz answers)
2. Out-of-range element numbers ("element 250")
3. Narrative and etymology declines
4. Explanation and definition routing to the dataset layer
5. Slot inheritance from `DialogueState` — this is what makes "and its boiling
   point?" work
6. Comparative / superlative / aggregate / filter-list branching
7. Dataset fallback through `HybridRetriever`

Below the confidence `threshold`, or on `Intent.UNKNOWN`, the planner returns a
plan the engine discards — and the query falls through to the legacy router.

## Multi-turn state

`ai/core/DialogueState.kt` carries `focusElement`, `recentElements`,
`lastFieldIds`, `lastPlan`, `lastResultKeys` and `lastTargetUnit` between turns.
`noteAnswer` records what was just answered; the planner's slot-inheritance step
reads it.

This is what lets a follow-up omit both the element and the property, and why
asking about a second element then saying "and its density?" resolves against the
new focus rather than the original one.

## Adding a language to the NLU

The app-level localisation steps are in
[Localisation](../development/localization#adding-a-language). For the assistant
specifically:

1. Add the operator, aggregation, series and category vocabulary to every group
   in `Lexicon.kt`.
2. Add the language's element names to the `EntityResolver` alias table (this is
   automatic if `elements_{lang}.json` exists — the aliases are built from the
   file).
3. Check whether the language is postpositional; if so, verify
   `OperatorExtractor` handles its ordering.
4. Check tokenisation: `ai/retrieval/Tokenizer.kt` has script-specific handling
   for Han and Arabic scripts, and `TextMatching` has Latin-only diacritic
   stripping. A new script may need work there — see [Retrieval](retrieval).
5. Translate the field labels in `strings.xml`. That gives you field aliases for
   free.
