# The AI agent training corpus

1,595 questions under `app/src/test/resources/ai/corpus/`, run against the real agent stack by
`app/src/test/java/com/jlindemann/science/ai/corpus/`.

```bash
./gradlew testDebugUnitTest --tests "com.jlindemann.science.ai.corpus.*"
```

## Why it exists in this shape

**It is a scoreboard, not a tripwire.** Every row in a file is evaluated and the failures are
reported together with a pass rate. Failing on the first bad row would make a thousand-question
corpus useless for the thing it is for: seeing how wide a defect is, and watching a number move as
it gets fixed. The one exception is `regressions.tsv`, where every row is a defect a user actually
hit.

**It wires a real retriever.** Every pre-existing engine test constructs
`QueryPlanner(..., retriever = null)`, so the dataset-retrieval fallback never runs. That is why
`PropertyCoverageTest` asserts the engine does not claim `"quiz me"` and passes, while the shipped
app answers it with the electron mass from the constants table. Every must-defer assertion in the
old suite was vacuous. `CorpusHarness` builds a real `HybridRetriever`, so a green row means the app
actually declines.

**It fails rather than skips when misconfigured.** Every asset-backed test in the suite opens with
`assumeTrue(TestAssets.available())`, which turns the whole agent suite green while asserting nothing
if the working directory is wrong. `CorpusSmokeTest` is the one test that makes that visible. If it
fails, no other agent test result means anything.

## Format

Tab-separated, `#` comments, ten columns:

```
id	session	turn	lang	query	expect_intent	expect_fields	expect_card	must_defer	notes
```

- `expect_intent` — an `Intent` name, or blank/`*` for "any claimed intent". Two pseudo-intents
  cover clause splitting, which is decided before any intent exists: `COMPOUND` (must split and be
  answered) and `NOSPLIT` (must stay whole).
- `expect_fields` — comma-separated field ids that must **all** appear in `plan.fieldIds`.
- `expect_card` — a `ChatCardKind`, `none`, or blank. Authored now, enforced once the card layer
  lands (`CorpusTest.enforceCards`).
- `must_defer` — `y` means the engine must decline so the personality layer answers.
- `session` + `turn` — rows sharing a session replay in turn order against **one** `DialogueState`.
  This is the only way follow-up slot inheritance can be tested.

The parser **requires exactly ten columns**. One tab too many silently shifts every later value into
the wrong column — `must_defer` lands in `notes` and a hard expectation quietly becomes a comment.
A corpus that mis-parses reports a pass rate for the wrong question, so that is a hard error.

`CorpusCoverageTest` guards the corpus against rotting: every queryable field, every dataset, every
shipped language and a floor on must-defer volume. Adding a field without adding a question for it
fails the build.

## Baseline — before any fixes

Measured with the strings fallback matching production (`TestStrings(fallBackToEnglish = true)`), so
these numbers are about **routing**, not translation coverage. Missing translations are asserted
separately by `StringCoverageTest`.

| File | Baseline | After routing fixes | Δ |
|---|---|---|---|
| calculations | 25/25 (100.0%) | 25/25 (100.0%) | — |
| compound | 27/35 (77.1%) | 34/35 (97.1%) | +7 |
| regressions | 23/42 (54.8%) | 41/42 (97.6%) | **+18** |
| must_defer | 106/124 (85.5%) | 120/124 (96.8%) | +14 |
| followups | 27/32 (84.4%) | 31/32 (96.9%) | +4 |
| safety | 23/26 (88.5%) | 25/26 (96.2%) | +2 |
| cards | 43/47 (91.5%) | 45/47 (95.7%) | +2 |
| comparisons | 35/38 (92.1%) | 35/38 (92.1%) | — |
| isotopes_nuclides | 27/32 (84.4%) | 27/32 (84.4%) | — |
| superlatives_filters | 66/78 (84.6%) | 66/78 (84.6%) | — |
| properties | 121/158 (76.6%) | 130/158 (82.3%) | +9 |
| categories | 17/27 (63.0%) | 22/27 (81.5%) | +5 |
| localized_other | 85/118 (72.0%) | 88/118 (74.6%) | +3 |
| aggregates | 16/30 (53.3%) | 21/30 (70.0%) | +5 |
| datasets | 33/48 (68.8%) | 33/48 (68.8%) | — |
| localized_sv | 59/123 (48.0%) | 75/123 (61.0%) | **+16** |
| **total** | **708/958 (73.9%)** | **793/958 (82.8%)** | **+85** |

`regressions` and `localized_sv` are the two numbers that matter most: the first is the reported bugs,
the second is the locale they were reported in.

Note the total is 958 scored against 983 authored rows — rows in a session that fails early do not
all get scored.

## Conversation corpora

`conversations_simple.tsv` (67 rows) and `conversations_complex.tsv` (88 rows) replay whole threads
rather than isolated turns, against one `DialogueState` each. A rule that holds for one follow-up can
still drift over five, and an interruption that is correctly declined can still leave the thread
broken behind it. A failure part-way through stops that thread, which is the point: a conversation is
only as good as its weakest hop.

The hardest rows are the **elliptical** ones, where the subject, the field, or the operation is not
in the query at all:

| Turn | What has to be reconstructed |
|---|---|
| "and in fahrenheit" | the element and the field, from two turns back |
| "which is denser" | both subjects, from the previous answer |
| "by how much" | both subjects, the field *and* the operation |
| "and their melting points" | the whole previous set, not just the focus element |
| "is that the highest" | the field and the filters, with no element in play |

All five shapes now pass. The two that were listed as unresolved were closed by a **plural anaphor**
rule — "their", "them", "both", "these" inherit the entire previous set rather than the focus
element, which is what had been quietly narrowing a comparison thread into a single lookup — and a
**margin cue** rule, where "by how much" or "what is the difference" re-opens the comparison just
made.

## Multi-hop questions: `complex.tsv`

Every other file exercises one mechanism at a time: a filter, or a superlative, or a unit.
`complex.tsv` (82 rows) combines them, which is the whole reason a plan is a structure rather than a
keyword branch — a subset **and** a threshold **and** an ordinal; a comparison whose operand is
itself derived; an aggregate over a negated set. It covers subset-plus-threshold,
subset-plus-superlative, ordinals inside a subset, ranges, negation, aggregates over constrained
sets, derived fields that exist in no JSON key, cross-referencing one element against a set,
three-operand comparisons, units inside a computed question, banked fields addressed by slot,
chemistry that mixes a calculation with a lookup, honest limits where the field is understood but the
data is sparse, and the same shapes in Swedish, German, French and Spanish.

It found six defects on its first run:

| Query | Was | Cause |
|---|---|---|
| "which elements melt at a higher temperature than tungsten" | property lookup | `GREATER` had "higher than" but not "higher temperature than", so the named element read as the subject rather than the bound |
| "quels éléments sont plus denses que l'or" | superlative | the Romance languages build the comparative and the superlative from the same word; only the particle "que" separates "le plus dense" from "plus dense **que** l'or" |
| "which elements do not occur naturally" | dataset lookup | `NATURAL_WORDS` listed "occurs naturally" and not its plural |
| "which elements have a recorded curie point" | dataset lookup | there was no way to ask which elements have a value at all — a question about coverage rather than chemistry, worth answering because several fields are recorded for a handful of elements |
| "compare the halogens on electronegativity" | dataset lookup | a comparison whose operand is a family rather than named elements; the family ranked by that field is the answer |
| "first ionization energy of caesium in ev" | (correct) | the **expectation** was wrong: caesium has two recorded steps and the card's ≥3 guard rightly suppressed it. The row now asserts `none`, which makes it a test of the guard |

## Answer quality, not only routing

`CorpusTest` asserts routing: the right intent, the right field, a non-blank result. An answer can
pass all of that and still be a bare number, which is how most of the original complaints actually
felt. `AnswerQualityTest` states eight properties of the *shape* of an answer and checks each over
every corpus row that produces that shape, aggregating failures the same way:

| Rule | What it requires |
|---|---|
| `rankableValuesCarryTheirRank` | a ranked value is placed against the field's range |
| `superlativesNameThePoolTheyTopped` | a superlative says what it won against |
| `comparativesShowBothSides` | a comparison names both sides and both values |
| `listsDiscloseHowManyMatched` | a truncated list says how many matched |
| `aggregatesDiscloseTheirBasis` | a statistic says how many values it used |
| `averagesShowTheirSpread` | a mean or median shows the range it came from |
| `isotopeAnswersStateTheTotals` | a truncated isotope list states the total |
| `comparisonsDeclareAWinner` | a two-element comparison says who came out ahead |
| `noDataOffersAnAlternative` | a missing value points at what *is* recorded |
| `noAnswerLeaksInternals` | no `str:` id, `---` sentinel or literal `null` reaches the reader |

Two were failing when written, and both were fixed rather than relaxed:

- **A superlative won against nothing.** A plain property lookup already said "the 6th densest of 105
  elements with a recorded density"; the more emphatic claim — "the densest element is osmium" — gave
  no sense of the field it topped. It now reuses `ai_rank_highest`/`ai_rank_lowest`, so the fix cost
  no new translation in any of the fourteen locales.
- **An average had no spread.** The mean density of the transition metals is about 10 g/cm³, and
  nothing in that figure hints the set runs from scandium to osmium. A central value with no range is
  the statistic most likely to be quoted back as if it described every member. This needed one new
  string, `ai_aggregate_spread`, written for all fourteen locales.

Two more were added later, and both failed on first run:

- **A comparison handed the question back.** "Compare gold and silver" produced a table of five
  properties against two elements — data, not an answer. It now ends with "Gold has the higher value
  on 5 of the 5 properties compared." Phrased as *higher*, not *better*: that is the only thing the
  numbers support. Two elements only; with three or more, "leads on 2 of 5" hides more than it says.
- **A missing value was a dead end.** "I don't have the Curie point for tungsten" is honest and
  useless. It now continues: "Recorded for Tungsten: Electrical Type, Electrical Resistivity,
  Magnetic Type, Superconducting Point." Those values were already loaded, so this costs nothing.

Writing the second rule also exposed a flaw in the harness rather than the code: it resolved field
labels in English while judging answers written in eleven other languages. Rules now receive the
row's language, which is what any locale-aware assertion needs.

Note the shape of the first fix. The rule as first written flagged 82 superlatives for "showing 1 of
118 matches without saying so". That reading was wrong — a superlative's `matched` is the pool it was
chosen from, not a truncation — but the smell was real. The rule was split into a correct truncation
rule and a correct pool rule, and the pool rule then failed honestly.

### Final, with the card layer enforced

| File | Passing | Rate |
|---|---|---|
| aggregates, calculations, followups, cards | 100.0% | |
| regressions, reported, complex, must_defer | 100.0% | |
| conversations_simple | 67/67 | 100.0% |
| superlatives_filters | 76/78 | 97.4% |
| properties | 154/158 | 97.5% |
| conversations_complex | 86/88 | 97.7% |
| compound | 34/35 | 97.1% |
| isotopes_nuclides | 31/32 | 96.9% |
| safety | 25/26 | 96.2% |
| localized_sv | 118/123 | 95.9% |
| localized_other | 112/118 | 94.9% |
| categories | 25/27 | 92.6% |
| comparisons | 35/38 | 92.1% |
| multilingual | 228/242 | 94.2% |
| conversations_multi | 86/99 | 86.9% |
| datasets | 40/48 | 83.3% |
| **total** | **1537/1595** | **96.4%** |

708/958 (73.9%) at the baseline, 1537/1595 (96.4%) now — over a corpus that has since grown by 637 questions, 341 of them in the ten languages that had almost no coverage — and all eight quality rules hold across the
1,015 corpus rows that produce an answer.

## The Indic-script bug, and how it hid

The single largest defect found was in `TextMatching`, and it had nothing to do with vocabulary.

`normalizeForLookup` stripped **all** combining marks. On Latin script that is correct and
desirable — it makes `Väte` and `vate` agree. On Devanagari a combining mark is a *vowel*, so the
same line reduced every Hindi word to a consonant skeleton:

| word | meaning | after normalisation |
|---|---|---|
| `सोना` | gold | `सन` |
| `पारा` | mercury | `पर` |
| `परमाणु` | atomic | `परमण` |

`परमण` begins with `पर`, so **every Hindi question about atomic number or atomic mass resolved
mercury** alongside the element actually being asked about, and planned as a two-element comparison.
Marks are now stripped only when the base character is a Latin letter.

Fixing that exposed a second layer immediately: `splitQueryTokens` split on `[^\p{L}0-9]+`, and a
Devanagari vowel sign is a mark rather than a letter — so `सोने` was torn into `स` and `न` before any
matcher saw it. The class is now `[^\p{L}\p{M}0-9]+`.

Only after both were fixed was native-script vocabulary worth adding at all; before that, no Hindi or
Urdu entry could have matched anything, which is why earlier passes saw no return from adding them.

**How it hid for so long:** the earlier reading of these failures was that Devanagari was
*under*-matching and needed script-aware stemming. The opposite was true — it was over-matching, and
the fix was to stop mangling the input. The diagnostic that settled it printed the resolver's
`matched` surface rather than the plan's entities; a plan with intent `UNKNOWN` carries no entities
at all, so reading them off the plan had been showing an empty list regardless of what resolution
actually did.

### One more from the same diagnostic

`silver chloride` resolved as two elements — silver plus chlorine — because German `Chlor` is an
alias, and English `chloride` is within the suffix tolerance of it. Inflection tolerance is now
confined to the language being spoken plus English. An exact name from any language still resolves,
so a Swede writing "gold" is unaffected; only *stem* matching across an unrelated language is
blocked.

## The second round of reported conversations

`reported.tsv` (34 rows) is the second batch of screenshots from the shipped app, and like
`regressions.tsv` it is a tripwire rather than a scoreboard. One pattern accounted for most of it,
and it was not a missing answer — it was a **confident wrong one**.

A mechanism question mentions a real field in passing. The planner resolves that field, finds a
subject in the thread, and replies with a cited number answering something nobody asked:

| Asked | Answered |
|---|---|
| "Why does radius decrease left to right, despite adding electrons" | "Potassium's Electrons is 19." |
| "Why does that trend hold going across period 2?" | "Helium's Period is 1." |
| "How does that change above Curie temperature?" | the thermal-energy equation |
| "How does that relate to van der Waals forces scaling with atomic mass" | xenon's atomic mass |
| "Where does that symbol come from etymologically" | tin's nine abundance reservoirs |
| "What's the weather today" | "Iron's Electrical Type is Conductor." |

`explanationPlan` already recognised "why" questions, but when it found no dictionary topic it
returned null and the query **fell through to the property path**. It now declines instead. A
mechanism frame ("how does", "what happens when", "what causes") declines even mid-thread, which is
where every reported case lived — and even when a topic matches, because any topic such a question
mentions is incidental. A comparison cue exempts it: "how does it compare to lead" opens the same way
and has a real answer.

Saying nothing is a far better answer than saying the wrong thing, and it lets the personality layer
offer what it can.

### Numbers that were not atomic numbers

Two reported answers came from reading a number as an element's place in the table:

- **"Is iron-54 stable"** → *"Yes. Xenon (54) beats Iron (26) on atomic number."* A mass number is
  not an atomic number; a digit run preceded by a hyphen is now rejected. The query routes to iron's
  isotopes, which name Fe-54 and say it is stable.
- **"If I have 12 grams of carbon-12, how many moles is that?"** → *"2 elements match: Magnesium,
  Carbon."* The 12 resolved magnesium. Atomic numbers are supposed to be resolved only by
  `atomicNumberIn`, which checks the query frames the number as one — but the generic alias loop
  matched `NUMBER` aliases too and asked no such question. It no longer does.

The second half of that question was also a real gap: mass-to-moles now works, reusing
`ChemistryMath.massToMoles` and one new string.

**"Tell me about element 119"** returned the mass of an electron. An element number past the end of
the table has a definite answer, so it is now recognised and declined rather than guessed at.

### Other reported fixes

- **"Noble gases"** mid-thread inherited xenon and its boiling point. A family name and nothing else
  is a request for that family — guarded by a token budget, by there being no element named, and by
  there being no anaphor, so "is it radioactive" stays a follow-up.
- **The molar mass of a compound now shows its per-element breakdown** unconditionally. Asked for
  Ca(NO₃)₂ the agent gave only the total, and the follow-up "show the calculation step by step"
  retrieved the definition of stoichiometry. The breakdown *is* the calculation, and it reuses rows
  that every locale already has.

## The multilingual corpus

The localized coverage before this was thin and uneven: Swedish had breadth, the other ten languages
had a handful of rows each, and whole shapes — aggregates, isotopes, nuclides, unit requests,
deferrals — went untested outside English. A defect in those languages could only be found by a user.

`multilingual.tsv` is the **same twenty-two question shapes in each of the eleven non-English
languages** (242 rows), and `conversations_multi.tsv` is two whole threads per language (99 rows):
one that widens from a property to a comparison, one that narrows from a filtered list to a single
element's detail. Uniform by design — a gap shows up as a row of failures down one language rather
than as scattered noise, and each shape can be compared directly against its English equivalent.

It opened at **187/242 and 60/99**. What it found was structural, not vocabulary:

**The comparative is periphrastic in most languages.** English marks comparative and superlative
apart morphologically — "denser" against "densest" — so `COMPARATIVE_ADJECTIVES` can key on a single
word. Italian, Portuguese, Spanish, French, Filipino, Hindi, Urdu and Chinese all build both from the
*same* degree word: "più denso" against "il più denso", "更致密" against "最致密". Every comparative
in those languages was being answered as a superlative over the whole table, or as a side-by-side
property table.

Two rules fixed it, both keyed on a new `AMBIGUOUS_DEGREE` list rather than on `MOST` as a whole:

- Two named elements plus an ambiguous degree word is a comparison **between those two**, decided
  ahead of the superlative branch and built directly — `comparativePlan` reads the explicit
  "is X denser than Y" shape, which these languages do not have.
- In an elliptical follow-up, an ambiguous degree word with a pair already in play means "between
  these two" rather than "of the whole table".

Scoping to `AMBIGUOUS_DEGREE` is the whole of it. Keyed on `MOST`, the same rules turned every
English "which is the densest" follow-up into a two-element comparison, costing seven rows in the
English conversation files before the list was narrowed.

**Thresholds can precede their comparator.** Hindi, Urdu and Chinese are postpositional:
"2000 केल्विन से ऊपर", "2000 کیلون سے اوپر" and "2000开尔文以上" all read *2000 kelvin above*.
`numberAfter` found nothing, so the threshold silently vanished and the query became an unfiltered
list. There is now a `numberBefore` to match.

**An anaphor is positive evidence.** The unknown-word test that closed off-topic rejection was
quietly breaking localized follow-ups: "är det farligt", "è pericoloso", "is dit gevaarlik" are all
built from safety vocabulary, which appears nowhere in a corpus of element data and dataset rows. An
explicit anaphor now exempts the turn from that test — it exists to catch a turn that *changes* the
subject, and "is it dangerous" plainly does not.

Vocabulary followed: `symbol` in eight languages, the thermal-properties family in seven, noble gases
and transition metals in six, safety and isotope words, and the plural anaphors that make "and their
melting points" work.

### Where each language stands

| | rows passing | | | rows passing |
|---|---|---|---|---|
| sv | 30/31 | | fil | 27/31 |
| de | 30/31 | | hi | 24/31 |
| af | 29/31 | | zh | 24/31 |
| fr, it, pt | 28/31 | | ur | 17/31 |
| es | 26/31 | | | |

Swedish, German and Afrikaans are effectively clean. **Urdu is the outlier and the honest next
target**: more than half its remaining failures are single shapes that resolve nothing at all, which
usually means an alias table has no entry in that script rather than that a rule is wrong. Verifying
the phrasing needs a reader of the language — the queries themselves are machine-assisted and are
listed in `docs/TRANSLATION_REVIEW.md`. A query that resolves *nothing* is still a defect worth
recording regardless of how idiomatic it is, which is why those rows are in the corpus.

## Urdu, Hindi and Chinese

The three worst languages after the multilingual corpus landed — ur 17/31, hi 24/31, zh 24/31. Every
cause turned out to be structural, and none of them was a missing translation.

**NFD decomposition silently broke every Arabic-script lexicon entry.** `normalizeForLookup` needs
the decomposed form to strip Latin diacritics, and it returned that form. Urdu "آئسوٹوپ" decomposes
from seven characters to nine, so the normalised query was never equal to the lexicon entry spelled
the way anyone types it, and the entire isotope vocabulary matched nothing. The function now
recomposes to NFC on the way out; Latin has already lost its marks by then, so recomposition cannot
undo the stripping.

**Chinese element characters were being eaten by the words around them.** A single Han character only
counts as an element when it is not inside a longer run of Han characters — the rule that stops 金 in
金属 ("metal") resolving as gold. But Chinese writes no spaces, so 比较**金**和银 and **砷**危险吗 put
the element inside a run too, and both resolved nothing. The question vocabulary (比, 较, 危, 险, 更,
最, 熔, 点, 元, 素 …) now counts as a boundary. None of those characters begins an element name, so
admitting them costs nothing.

**Nuclides were matched with a Latin-only pattern.** `([a-z]{1,13})-(\d{1,3})` finds nothing in
"کاربن-14", "कार्बन-14" or "碳-14". It is `\p{L}` now.

**Urdu ships transliterations where users write the native word.** The shipped table gives "آئرن" for
iron and "آرسینک" for arsenic — English words in Urdu script — so someone writing لوہا or سنکھیا got
nothing. A small `NATIVE_SYNONYMS` table covers the cases where the shipped name is a transliteration
*and* the language has a common word of its own. Everything else still comes from the assets. The
first version of that table included "ٹن" for tin, which is two characters and sits inside "ٹنگسٹن"
(tungsten), so every tungsten question resolved a second element — a reminder that a short synonym in
an abjad is the same hazard as a short symbol in Latin.

**Degree markers can be phrases, and can lack a degree word entirely.** Urdu builds its superlative
from two words, "سب سے" plus the adjective, and the matcher was single-word. Hindi marks the
comparative with the particle alone — "सोना सीसे **से** घना है" has no degree word at all — so the
particle now counts as evidence in its own right.

| | before | after |
|---|---|---|
| Urdu | 17/31 | 25/31 |
| Hindi | 24/31 | 30/31 |
| Chinese | 24/31 | 30/31 |

Urdu is still the weakest and the honest next target. Its remaining failures are two spurious
second-element matches and a superlative phrase that the corpus rows spell differently from the
lexicon — the sort of thing a reader of the language would settle in minutes, and which I cannot
settle by inspection.

## Off-topic rejection, finally

`must_defer` reached 124/124. The signal that worked is not a score at all.

BM25 ranks; it never abstains. Its scores are normalised against the top hit, so the best result is
always exactly the same number however unlike the corpus the query is — which is why no threshold
ever separated "write me a poem" from "what is the gas constant", and why an earlier attempt to
require a *rare shared term* cost three correct answers to gain one rejection.

Two weak questions together do what neither does alone:

1. **Does the query name something the corpus has never seen?** Not "is this a weak match" — is the
   word absent entirely. "Poem", "weather" and "dinner" appear in no document.
2. **Does the retrieved row share a word with the question?** Real questions contain ordinary verbs
   the corpus has never seen — "define", "convert", "tell" — so absence alone rejects far too much.

"Define molar mass" trips the first test and passes the second (it retrieved *Molar Mass*). "Write me
a poem" fails both (it retrieved *Electron mass*). The same absence test also stops a short off-topic
follow-up inheriting the thread, which is what had "what's the weather today" answering about iron.

The asymmetry is what makes this safe to act on: refusing produces a decline, which the personality
layer handles, while a false accept produces a cited answer to a question nobody asked.

### What it exposed about the corpus

Three `datasets.tsv` rows had been **passing on nonsense**. Asserting only the intent and never which
row came back, they were green while "poisson ratio of granite" retrieved *Basalt*, "tell me about
granite" retrieved *Electron mass* and "formula of the ammonium ion" retrieved *Acid*. The data
contains no granite and no ammonium at all — the geology table holds minerals and the ion table holds
element ions. The rows now name data that exists.

Worth fixing properly: `expect_intent` cannot express *which* dataset row is correct, so any row
asserting `DATASET_LOOKUP` is weaker than it looks.

## The short-surface collisions, and the capitalisation rule

Four failures in three languages turned out to be one defect. A one- or two-character surface
collides with ordinary words across twelve languages at once, and each collision resolved a second
element, turning a plain lookup into a two-element comparison:

| Query | Also resolved | Because |
|---|---|---|
| "what's tungsten's chemical symbol" | sulfur | the possessive `'s` splits off as a bare "s" |
| "is silicon a conductor or a semiconductor" | gold | French "or" is gold |
| "vad är symbolen för guld" | argon | Swedish "är" normalises to "ar" |
| "what is element 26's melting point" | sulfur | the possessive again |

The previous guard let a single letter through whenever the query mentioned "symbol" or "element",
which was exactly backwards: "what's tungsten's chemical symbol" contains both words, so the escape
fired precisely where the collision was worst.

The rule is now **capitalisation**, read from the original query because it is the only place case
survives normalisation. A short symbol must be written the way a symbol is written — capital first
letter, own word. That is a convention users already follow, and requiring it also *gained* a case
the old rule lost: "atomic mass of W" had been missed entirely. An all-caps query is treated as
carrying no case information, so the symbol is declined rather than matched against every two-letter
word in it.

Separately, a short *name* from a language the user is not writing in is now rejected outright below
three characters. The language guard had only ever applied to stem matching, so an exact match on
French "or" slipped past it. Symbols are exempt — "Au" is gold in every language, and applying a
foreign-name floor to symbols silently broke every "melting point of Fe".

## What the remaining 87 failures are waiting on

The easy vocabulary gains are exhausted. What is left needs investigation or design, not more list
entries, and is recorded here rather than papered over.

**`datasets` (8) — still the largest remaining cluster.** Three separate causes. Spurious subset matches
beat the dataset lookup ("gas constant" and "ideal gas law" become phase-filtered lists because "gas"
is a phase word). Compounds resolve as their constituents, so "silver chloride" is silver, which
needs the resolver to return surface positions it currently does not. And the Swedish and French rows
fail because the dataset corpus is indexed in English only — "vad är elektronens massa" cannot match
a row titled "Electron mass" by any lexical means.

**`localized_other` (6) — Urdu, Filipino and Afrikaans.** Not the gap it first appears for Hindi,
which is now clean. What remains needs morphology no list can express: the Tagalog superlative is the
prefix `pinaka-` fused to the adjective (`pinakadensidad`), and Afrikaans forms its superlative by
suffix (`dig` → `digste`). Both need a rule, and verifying either needs a reader of that language.

**`localized_sv` (5).** Swedish solid compounding: "uranatom", "mediandensitet". Syncope is now
handled — "kisel" → "kislets" and "kvicksilver" → "kvicksilvrets" both resolve — but splitting a
compound into two known words is a different mechanism, and doing it wrong resolves elements nobody
named.

**`must_defer` (3), `properties` (4), `comparisons` (3), `categories` (2).** A long tail of
individually distinct phrasings with no shared cause, plus the two off-topic rows below.

**`conversations_complex` (2).** Down from five. What is left is the deepest ellipsis — "what else is
in that group", where the *filter* has to be reconstructed from a property of the focus element that
was never stated as a filter in the first place.

### The dataset row is rarely the top hit

Two dataset failures had nothing to do with retrieval quality:

- **"What is the gas constant"** and **"what is the ideal gas law"** were answered with a list of
  every gaseous element, because "gas" is a phase word and nothing looked at what it was part of.
  A short list of fixed terms is now removed before phase matching.
- **"Standard electrode potential of zinc"** ranks zinc first — of course it does, the query says
  zinc — so the top hit was an element, the `is Dataset` check failed, and the question was
  declined. The planner now looks a few places down the ranking for the best *dataset* row. The
  element hit is worth nothing at that point: it is already known, and no field resolved to look up
  on it.

### Two more things measured and rejected

The remaining electrode and solubility rows are blocked by the confidence bar, not by ranking, and
two ways of lowering it were tried and backed out:

| change | datasets | must_defer |
|---|---|---|
| drop the with-element bar to the element-free one | +5 | **−4** |
| drop it only when the row's title shares a word with the query | +5 | **−3** |

Both are bad trades. Declining "solubility of sodium chloride" is a gap; answering "what is the
weather today" from the constants table is a *wrong answer*, and a wrong answer costs more than a
missing one. The bar stayed where it is and the finding is recorded on the constant.

### Two things measured and rejected

**The dataset threshold is inert, and always was.** Raising `DATASET_CONFIDENCE` from 0.45 to 0.55
had been recorded here as changing nothing. The reason turns out to be structural rather than a
matter of tuning: `Bm25Index.searchNormalized` divides every score by the top hit, so the best result
always scores exactly 1.0 — whether it was an excellent match or the least bad of a corpus containing
nothing relevant. Fused with `RetrievalWeights.LEXICAL`, every candidate arrives at exactly 0.55.
*No* threshold on that number can separate anything. This is now documented on the method itself so
the next person does not spend the afternoon on it.

**A discriminating-overlap gate does not separate them either.** With the raw BM25 scores exposed,
the legitimate and off-topic queries overlap: junk peaks at 8.4 ("write me a poem") and legitimate
questions bottom out at 9.5 ("what is electronegativity"). Requiring the matched document to share a
rare term with the query looked promising — every content word in the off-topic queries has a
document frequency of zero — but the junk survives on ordinary English words that happen to occur in
the corpus prose ("today", "about", "have", "for"), while legitimate matches rest on terms too common
to count ("gas" is in 28 of 265 documents). Gating on it cost three correct dataset answers to gain
one rejection, and was backed out. Separating these needs a stopword list per language, which is real
work in twelve languages and should be done deliberately rather than as a side effect.

## Working on it

Each phase must move the total up and regress no file. When a row's expectation turns out to be
wrong rather than the code, change the row and say so in its `notes` — an expectation nobody can
justify is worse than no expectation.
