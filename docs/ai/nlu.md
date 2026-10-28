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

### Compounds claim their words first (roadmap 1.2)

`scanCompounds(query)` finds compounds named in words and returns them with the
query's remainder — the query with their words blanked out. `resolveAll` only
looks for elements in that remainder, so "sodium chloride" is no longer sodium
(and "density of sodium chloride" no longer sodium's density), while "water's
oxygen" is still oxygen.

Salts are not listed. They are composed from an element name in any of the
twelve languages, an anion name from `Lexicon.ANION_NAMES`, and the shape each
language writes them in: cation then anion ("sodium chloride"), anion, connector,
cation ("cloruro de sodio", "chlorure d'argent"), one word ("natriumklorid",
"Calciumcarbonat"), or a Han anion prefix before the cation's character (氯化钠,
硫酸铜). An optional Roman numeral gives the charge ("iron(III) chloride"). The
formula follows from the charges — Ca(OH)2, Al2(SO4)3, (NH4)2SO4. A metal without
one common charge (`Lexicon.CATION_CHARGES`; iron and tin are absent) still
claims its words but has no formula. Molecules and household names come from
`Lexicon.COMMON_COMPOUNDS`.

`QueryPlanner` answers a compound's formula, molar mass, composition and moles,
sends "is X soluble" to its cation's row of the solubility table, and declines
anything else it is asked about (`DeclineReason.COMPOUND_PROPERTY`): the app holds
no compound properties. A compound whose words are part of a field's own name
("sea water") is not treated as one.

### Non-English dataset questions (roadmap 1.3)

Three planner rules changed with the translated titles (see `retrieval.md`):

- **A definition gets the definition.** The explanation topics map to the
  periodic-trend glossary entries, which answer "why does electronegativity
  increase". A "what is" question now takes the plain entry ("Electronegativity")
  when the glossary has one; before, every language whose word reached this path
  got the trend.
- **Han queries are measured in characters.** The dataset fallback needs three
  words, and unspaced 动能的公式 is one, so it never reached retrieval. Han text
  needs four characters (`MIN_DATASET_HAN_CHARS`), the clause splitter's floor.
- **Request frames are stopwords in every language.** "parle-moi", "háblame",
  "parlami" and "fale" joined "tell" and "berätta", and Italian elided articles
  ("dell'", "nell'") are stopwords, because the tokenizer splits on the
  apostrophe and "dell" was unknown content.

## `Morphology.kt`

`expand(query)` — compounds and affixed superlatives, written as the words they
are made of (roadmap 1.5). `QueryPlanner.plan` runs it first, so every matcher
below sees "smaltpunkt skillnaden" rather than "smältpunktsskillnaden". Three
rules, each splitting a word only into parts it knows, and each leaving alone a
word already known with its property:

- **Compounds.** A word that is no known word is split in two when the first part
  is a field's name or a statistic (`FieldResolver.isFieldWord`, `Lexicon.AVERAGE`,
  `MEDIAN`, `SUM`), the rest is a known word with a short ending
  (`FieldResolver.knowsWord`, a statistic, or `Lexicon.COMPOUND_HEADS`: value,
  temperature and difference in Swedish, German and Afrikaans), and at most a
  linking `s` stands between: "smältpunkts|skillnaden", "Durchschnitts|dichte",
  "medel|tätheten". "atommassan" and "natriumklorid" are never split: one is a known
  word, the other has no known part.
- **Afrikaans -ste.** "ligste" becomes the superlative `Lexicon.SUPERLATIVE_STEMS`
  knows its stem by, "lightest"; a stem that is a field's name becomes "most" and
  the stem. Forms listed whole with their property, "swaarste", stay as they are.
- **Filipino pinaka-.** The same, after the hyphen and the linker (-ng, -g) are
  dropped: "pinakamababang" is the listed "pinakamababa", "pinakamatigas" is
  "hardest", "pinakaelectronegative" is "most electronegative".

Rewritten words are written folded; words with digits are never touched, so the
formulas and quantities the executor re-reads stay as written. `MorphologyTest`
pins the rules and their guards, and Windows mirrors it.

## `Speller.kt`

`correct(query)` — misspellings (roadmap 1.6). Fuzzy matching used to cover element
names only, and only for a query of one or two words (`EntityResolver.fuzzyMatch`,
which stays), so "what is the denisty of gold" was declined. `QueryPlanner.plan`
now asks the speller when, and only when, a question is declined with
`NO_MATCH`, plans the corrected question, and keeps it only if it is answered from
the element data: a correction that only a dataset row would answer is a guess on
a guess ("tell me about mars" is one edit from "mass"), and the decline stands.

A word is corrected only when it is a Latin-script word of four letters or more
that the vocabulary (element names in every language, every field alias's words,
the lexicon's operator words) does not know, and that is not a stopword, a "yes,
go on" word, a `Lexicon.NEVER_CORRECTED` conversational word or a
`DEFER_TO_PERSONALITY` word; when the nearest vocabulary words are within one edit
(two from eight letters), a transposition counting as one; and when they all mean
the same thing. "plomp" becomes "plomb", because "plomb" and "plomo" are both
lead; a word between two meanings stays as written. SymSpell's deletion index buys
speed the decline path does not need, so every candidate of a plausible length is
measured instead.

**By design it leaves alone** a typo in a question something already matched:
"which halogn is the heaviest" is answered over every element, and "averge density
of the noble gases" as a list, because nothing declined. Correcting answered
questions is exactly how "more" once became boron (Windows porting notes, D47).

## `LanguageDetector.kt`

`detect(message, active, byElementNames)` — which language to answer a message
in (roadmap 1.4). A naive Bayes model over the character trigrams of each word,
read from `shared/language/trigrams.tsv` and built by `LanguageModelGoldenTest`
(`-Pgolden.update=true` regenerates it after the strings, stopwords, element
descriptions or dataset titles change). `architecture.md` describes the decision
rule; `LanguageDetectorTest` pins the feature extraction, which Windows mirrors
exactly because both apps read the one model.

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

~2,200 lines, the largest file in the subsystem. It orchestrates everything above
into a `QueryPlan`. Until roadmap 1.8 that was a long **ordered cascade of
guards**: the first branch to return won, so the order of ~30 `if` blocks decided
every answer and could be read only as control flow.

**Arbitration (roadmap 1.8, stage 1).** Every branch now *proposes*:
`proposals(query, state)` returns each branch's plan, tagged with its `Branch`, in
the order they were made, ending with `Branch.NO_MATCH`; `Arbiter.pick` chooses,
and `plan` answers with the choice. The steps between the branches still run in
order — inheritance from the dialogue, a threshold read off an element, the crust
sibling fields — because they refine what the later branches see. In this first
stage the arbiter still chooses by the old order, which is `Branch`'s declaration
order, so no answer changed; within one branch the first proposal wins, which is
how a branch that tries the explicit "X is denser than Y" before its looser
reading keeps meaning the explicit one. What changed is that the order is now
data, and that the losing proposals can be seen.

`ArbitrationTest` checks that the pick is the plan for every corpus row, and
prints where the order still decides: 520 of 2,521 dev rows have more than one
branch proposing, across 50 winner-and-loser pairs. The largest are an aggregate
over a subset list (90 rows: "the average density of the halogens" is also a list
of halogens), isotopes over a property lookup (63: "isotopes" resolves a field
too), a superlative over a subset list (52), and a subset list over a dataset row
(30). The later stages of 1.8 replace the order with evidence, one of these pairs
at a time, with both corpora green between steps.

The branches, in their current order:

1. Defer-to-personality check (greetings, small talk, quiz answers)
2. Out-of-range element numbers ("element 250")
3. `calculationPlan` — the arithmetic branches, checked before entity and field
   resolution because a formula or a nuclide would otherwise be picked apart into
   unrelated matches. Its own internal order is load-bearing (see below).
4. Standalone unit conversion
5. Narrative and etymology declines
6. Explanation and definition routing to the dataset layer
7. Slot inheritance from `DialogueState` — this is what makes "and its boiling
   point?" work
8. Comparative / superlative / aggregate / filter-list branching
9. Dataset fallback through `HybridRetriever`

### Order inside `calculationPlan`

**BALANCE → SOLUTION → DECAY → MOLE → ISOTOPE_COMPARISON → NUCLIDE_COUNT →
ISOTOPES → FORMULA_MASS.** Three of those adjacencies are not stylistic:

- **SOLUTION above MOLE.** `ChemistryMath.MOLE_QUANTITY` matches the `0.5 mol`
  inside `0.5 mol/L`, so below the mole branch every molarity question is
  answered with a particle count.
- **DECAY above ISOTOPES.** `Lexicon.ISOTOPE_WORDS` holds `decay`, `half life`,
  `stable` and `how long does`, and that branch fires at 0.9. Placed after it,
  `DECAY_CALC` is unreachable.
- **BALANCE first, and on the raw query.** `normalized` is lowercased, which
  destroys every formula in the sentence. The branch also needs an exemption from
  `UNBACKED_CONCEPTS`, which contains `reaction` and would otherwise decline
  "balance this reaction: Fe + O2 -> Fe2O3" outright.

Each branch requires positive evidence and returns null without it, so the ones
below stay reachable: a bare "half-life of carbon-14" has no arithmetic in it and
falls through to the isotope table, which answers it better.

### Unit conversion vs property lookup

`UNIT_CONVERT` sits in `plan()` rather than in `calculationPlan`, because the
guard that separates it from a property lookup needs the resolved field and
element. Both are checked against the query **with its units removed**: a unit
token resolves a field on its own — `GPa` reaches the pressure fields, `Pa` even
resolves protactinium — so a plain "did a field resolve?" test declines
"convert 5 GPa to MPa" on the strength of the very units being converted.

The target unit comes from `QuantityScanner.targetUnit` first and only then from
`OperatorExtractor.targetUnit`. The latter accepts any unit with a preposition on
either side, which is right for a property lookup but here picks the unit being
converted *from*: in "convert 5 GPa to MPa" the `GPa` has `to` on its right.

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

### Sets read off an element (roadmap 1.7)

"What else is in that group" takes its group from the element in focus, and "the
same block as iron" from the element it names. The planner reads a set's name
after one of `Lexicon.SET_DETERMINERS` ("in its period", "of that group", "in
derselben Gruppe", "nello stesso gruppo", 同一族) or inside a `SAME_AS_WORDS`
phrase, and turns the reference element's group, period, block or series
(`Lexicon.SET_DIMENSIONS`) into a filter. That filter joins the subsets before
anything else is decided, so a list, a count, a superlative or a statistic all
range over it, and the word that named the set no longer resolves as a property:
"what is in the same block" is not a question about iron's block. A plain list
returns at once; "the lowest melting point in that group" goes on to the
superlative branch with the group in hand.

"Else", "other" and "the rest" (`Lexicon.OTHER_WORDS`) add `Filter.Excludes` for
the reference - but only to a list or a count. "Más", "mais" and "mer" also make
superlatives, and the densest in iron's group may be iron. "What group is iron in"
is unaffected: the set's name must follow a determiner.

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
6. Add the language's `STOPWORDS` and a `shared/datasets/titles_{lang}.tsv`, then
   regenerate the language model with `LanguageModelGoldenTest`
   (`-Pgolden.update=true`) so the detector knows the language. A non-Latin script
   needs no other work there; a Latin one is also trained without its accents.
