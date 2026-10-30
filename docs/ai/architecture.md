---
title: Architecture reference
parent: AI Agent
nav_order: 2
---

# AI Agent — Technical Documentation

The AI Agent is a fully on-device chemistry assistant built into the Atomic Periodic Table app.  
It requires **no cloud API**, works offline, and answers questions in the user's language.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [File Reference](#file-reference)
3. [What the structured engine adds](#what-the-structured-engine-adds)
4. [ChatOrchestrator](#chatorchestrator)
5. [AiChatAgent](#aichatagent)
6. [AIPersonality](#aipersonality)
7. [AIRateLimiter](#airatelimiter)
8. [Retrieval (HybridRetriever)](#retrieval-hybridretriever)
9. [ChatHistoryManager](#chathistorymanager)
10. [Data Models](#data-models)
11. [Language Support](#language-support)
12. [Chat History & Persistence](#chat-history--persistence)
13. [Rate Limiting & Tiers](#rate-limiting--tiers)
14. [Adding a New Language](#adding-a-new-language)
15. [Adding New Knowledge](#adding-new-knowledge)
16. [Testing](#testing)

---

## Architecture Overview

The engine is the only source of answers about chemistry. Around it, a small **orchestrator**
answers what the engine declines by design — greetings, overviews, quizzes, "tell me more" — and
an Android **adapter** handles what the orchestrator must not know: rate limits, telemetry,
resources and persistence. The orchestrator replaced the 2,664-line keyword router
(`AIAgentManager`) in roadmap item 0.2, ported back from the Windows app's `ChatOrchestrator.cs`.

```
User Input
    │
    ▼
AiChatAgent.generateResponse()             Android: limits, strings, telemetry
    ├── Rate limit check                   (AIRateLimiter)
    ├── Element page in focus              (ChatOrchestrator.focusOn)
    │
    ▼
ChatOrchestrator.ask()                     platform-free, JVM-tested
    ├── Language detection                 (RetrievalService.detectLanguage → LanguageDetector)
    ├── Quiz run or open quiz question?    (QuizRun, ElementQuiz)
    │
    ├── STRUCTURED ENGINE                  (AiEngine) — always asked first
    │     1. Tokenize                      (Tokenizer: Han bigrams, Arabic, …)
    │     2. Resolve entities              (EntityResolver, 12 languages)
    │     3. Resolve fields                (FieldResolver, from localized labels)
    │     4. Extract operators             (OperatorExtractor)
    │     5. Plan                          (QueryPlanner) ── declines ──┐
    │     6. Execute                       (QueryExecutor)              │
    │     7. Compose + cite                (AnswerComposer)             │
    │                                                                   │
    └── WHAT THE ENGINE DECLINES  ◄─────────────────────────────────────┘
          ├── Reactivity of two elements   (ReactivityScale)
          ├── "Tell me more"               (ElementMoreInfo → AiEngine.fieldAnswerFor)
          ├── "Gold", "element 79"         (ElementOverview)
          ├── Reactions                    (ElementReactions)
          ├── Similar elements             (ElementSimilarity)
          ├── Uses, biology, etymology     (ElementNarrative)
          └── Greeting, thanks, fun fact, questions about the app (AppFeatures), or decline

Chat persistence (PRO, signed-in users only)
    └── ChatHistoryManager → Firebase Firestore
```

**The order is the contract.** The engine is asked first and its decline is taken seriously:
everything after it is for questions with no grounded answer, and reaching them when the engine
could have answered would replace a cited fact with a pleasantry. What the orchestrator owes the
user is defined by two corpus files, `must_defer.tsv` and `capabilities.tsv`.

---

## File Reference

| File | Role |
|------|------|
| `AiChatAgent.kt` | Android adapter. Rate limits, locale-wrapped strings, entitlements, offline card policy, telemetry, interest persistence. |
| `AiTurnStats.kt` | What one turn did, for analytics. Shape only, never the question. |
| `AIPersonality.kt` | Localised greeting and suggestion strings for the panel and the widget. |
| `AIRateLimiter.kt` | Daily message quota per subscription tier. |
| `ChatHistoryManager.kt` | Saves and loads chat sessions to/from Firebase Firestore. |

### `ai/chat/` — the conversation

| File | Role |
|------|------|
| `ChatOrchestrator.kt` | One conversation: language per message, engine first, then the handlers below. Holds the `DialogueState`. |
| `ElementOverview.kt` | "Gold", "tell me (everything) about gold": basic facts plus sections, in the reader's language. |
| `ElementMoreInfo.kt` | "Tell me more": picks the next unsaid field; the engine renders it. |
| `ElementQuiz.kt` | "Quiz me", and the verdict on the next reply. |
| `study/` | The study coach (roadmap 2.6): `Fsrs` (FSRS-5), `StudyDeck` (cards, due order, merge), `StudyStore` (this device's file), `StudyAccess` (what the chat gets). |
| `QuizRun.kt` | Quiz runs (roadmap 2.5): "quiz me on the halogens", read into a count, a scope and a kind of question, and judged one pick at a time. |
| `ElementReactions.kt` | What an element does with water, oxygen, acid or another element. |
| `ElementSimilarity.kt` | The elements in the same group. |
| `ElementNarrative.kt` | Usage, biological role and etymology, read from the description. |
| `AppFeatures.kt` | Questions about the app's own tools. |
| `FollowUps.kt` | Follow-up chips built from the answer's plan, offered only when the engine would answer them (roadmap 2.1). |
| `InterestProfile.kt` | Which elements a user asks about, for the returning-user greeting. Counts only. |

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
| `UnitConverter.kt` | Multiplicative and affine unit conversion, plus the eV↔kJ/mol bridge that only an explicit conversion request may cross. |
| `Fraction.kt` | Exact rationals for the balancer. No epsilon anywhere: a floating-point residue is indistinguishable from a real zero pivot. |
| `EquationBalancer.kt` | Balances an equation, or declines with a reason. Shared with the Equation Balancer screen. |
| `ChemistryMath.kt` | Formula parsing (hydrates included), molar mass, neutron counts, mole conversions. |
| `DecayMath.kt` | Exponential decay over a half-life read from `IsotopeParser`. |
| `SolutionMath.kt` | `n = c·V`, mass through a molar mass, and `C₁V₁ = C₂V₂`. |
| `ReactivityScale.kt` | An ordinal reactivity heuristic. Not a field, which is why the planner declines reactivity. |
| `KnowledgeStore.kt` | The index itself, plus per-field coverage counts. |
| `DatasetIndex.kt` | Every non-element dataset (dictionary, equations, constants, Poisson, geology, ions, indicators, electrode series, solubility, units) in one searchable form. |

### `ai/retrieval/` — search

| File | Role |
|------|------|
| `Tokenizer.kt` | Multilingual tokenisation. Han unigrams + bigrams, Arabic folding, Devanagari-safe. |
| `Bm25Index.kt` | Okapi BM25 over a corpus built at runtime from live app data. |
| `EntityResolver.kt` | Which elements a query names, with the short-symbol collision guard, and which language it is in. |
| `TextMatching.kt` | Shared `levenshtein` / `hasKeyword` / normalisation helpers. |
| `FuzzyMatch.kt` | "Yes, go on" in twelve languages, which is never a misspelled element. |
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
| `core/DialogueState.kt` | Multi-turn memory driven by slot-emptiness inheritance, plus the open quiz question and what "tell me more" has said. |
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
| Say it has no data | *"vickers hardness of helium"* → says so, reports the field is recorded for 45 of 118 elements, and names what *is* recorded |
| Convert a value the user brought | *"convert 500 K to °C"*, *"2 eV in kJ/mol"* — free |
| Molar mass | *"molar mass of CuSO4·5H2O"* — hydrates included |
| Balance an equation | *"balance Fe + O2 -> Fe2O3"* — PRO+, same solver as the Equation Balancer screen |
| Work a solution problem | *"how many grams of NaCl for 250 mL of 0.5 mol/L"*, *"dilute 50 mL of 2 mol/L to 0.5 mol/L"* — PRO+ |
| Run a decay calculation | *"how much of 100 g of carbon-14 remains after 11460 years"* — free |

Every answer names its sources and offers a chip that opens the screen the figure came from.

The four calculators are all deterministic and offline, like everything else here.
Two of them decline rather than guess, and that is deliberate: an equation with
several independent balances is reported as such instead of being answered with
whichever one the elimination happened to produce, and a nuclide the data records
as stable, unlisted, or listed with an unknown half-life produces three different
sentences rather than one shrug.

---

## ChatOrchestrator

`class ChatOrchestrator(retrieval, stringsFor, entitlements, cardPolicy, language, random)`

One conversation. Platform-free: no `Context`, no Firebase, no storage, so every handler is
covered by JVM tests over the real data (`ai/chat/*Test.kt`).

| Member | Description |
|--------|-------------|
| `ask(message): ChatReply` | Answer one message. The reply carries its text, `ReplySource`, source chips, card, suggestion chips and the engine's plan. |
| `focusOn(elementKey)` | Put an element in focus without a turn — the chat was opened from its page. |
| `suggestions()` | The chips for the current state, before anything has been asked. |
| `reset()` | Start a new thread, keeping the language. |
| `state` | The `DialogueState`, public so a host can persist it. |
| `profile` | The `InterestProfile`, which the host saves and restores. |
| `language` | The language the last message was answered in. |

**Language** is detected per message, not per session, by `RetrievalService.detectLanguage`: a
character-trigram model (`LanguageDetector`, roadmap 1.4) judges a message of two or more words, and
element names decide a one-word message and break a near-tie. A language takes over only when it
beats the one in play by a clear margin; see *Auto-detection* below.

**Engines** are built per language on demand and rebuilt when the entitlements or the offline
setting change, so a purchase takes effect on the next message.

**Suggestion chips** come from the shipped string arrays (`ai_suggest_element`,
`ai_suggest_general`, `ai_suggest_tools`), so all twelve languages get their own.
`SuggestionChipTest` asks every chip in every language and fails if one is declined.

**Follow-up chips** (roadmap 2.1) come first, built by `chat/FollowUps.kt` from the plan the
engine just answered with rather than from a fixed list:

| The answer | The chips |
|---|---|
| A property of an element | The same field for the element's neighbour in its group ("Density of Gold and Silver"); the element with the highest value ("Which element has the highest density?"); the same value in another unit the converter supports ("Density of Gold in kg/m³") |
| A comparison | The element with the highest value of the field compared |
| A ranking over the whole table | Its other end ("Which element has the lowest melting point?") |
| A list or a statistic over a subset | "How many are there?", which inherits the subset |

The partner is the nearest element in the group with a value for the field, a natural one before a
synthetic one, and the lighter on a tie; the lanthanides and actinides, which have no group, take
the nearest in their series. Each chip is a template in the reader's language (`ai_follow_*`) filled
with the field's sentence label and the element's localized name.

A chip is offered only if `AiEngine.wouldAnswer` plans it, as the next turn and against a copy of
the dialogue state, into an answer that is not the one just given. A phrasing the planner cannot
read in some language is therefore never shown there, rather than leading to a decline. The fixed
chips fill the remaining places, less any that would repeat the question ("Atomic mass of Gold"
under the atomic mass of gold). `FollowUpsTest` asks each locale's own seed questions and then
every chip they produce, and fails if any is declined.

**Shareable answer cards** (roadmap 2.10, free). Every answer to a question has a share button
under it; the greeting has none. It shares the answer as a branded image:
- **Layout.** `utils/AnswerShareImage` lays out `share_answer_image.xml` off screen, at 400 dp, so an
  image looks the same whichever phone shared it. It shows the Ask Atomic mark, the question, the
  answer as the chat shows it, the card, and the footer `ai_share_footer`.
- **Card.** The card is a snapshot of the one in the conversation. A locked card is left out, so a
  share never shows what the paywall hides; the text is not gated and always goes in.
- **Sharing.** The PNG is written to `cache/shared/` and handed to the share sheet through a new
  FileProvider (`res/xml/share_paths.xml`, authority `<applicationId>.fileprovider`), which exposes
  that folder only.
- **Why a button.** It is not a long press, because the answer text is selectable: a long press
  selects text. The selection bar already offers Copy and Share for the text.
- **Analytics.** `ai_answer_shared` records whether the image had a card, never the text.

**Context from any screen** (roadmap 2.9). Before this, only the main screen and the element pages
hosted the chat; an element page focused its element. Now every table and tool screen hosts it too,
and the chat knows which screen it was opened from.
- **Hosting.** `BaseActivity` attaches the chat button and the existing panel to any screen that
  overrides `chatScreen`, without a layout change. Both go into the content frame. The panel's back
  callback is registered after the screen's own, so while the panel is open, back closes it and
  not the screen. Eighteen screens opt in: the eleven reference tables, the solubility and isotope
  screens, and the tools. Several share a screen id (both balancers, the flashcards and the
  Learning Games).
- **Screen ids.** `Screens` (`ai/chat/ScreenChips.kt`) lists seventeen ids, such as `nuclides`,
  `ideal_gas` and `balancer`. `AiChatPanelController` takes one as `screen`.
  `ChatOrchestrator.openedFrom(screen, element)` records it, and focuses the element when the
  screen has one.
- **Opening chips.** `suggestions()` puts the screen's own questions first, then the general ones,
  three in all. A new thread keeps them, since the screen has not changed. The chips are shared
  data, `datasets/screen_chips.tsv`: two per screen in each language.
- **The test.** `ScreenChipsTest` asks every chip in its language, after opening from its screen,
  and fails on any decline. The first pass failed 54 of 408. Rewording rescued 20. The other 34
  were dropped, so those screens fall back to the general chips in those languages. They mark real
  gaps, mostly in Chinese, Urdu and Hindi: dataset lookups (electrode potentials, alloys, minerals,
  constants) and the 2.8 calculators in Chinese ("0.01 M HCl的pH", where the unit runs into the
  Chinese text).

**More calculators, stage 1** (roadmap 2.8, PRO+). Three calculators join the ones from 2.2, with
the same gate and the same "show steps" working:
- **Ideal gas law.** `ai/data/GasLaw` solves PV = nRT for whichever of pressure, volume, amount and
  temperature is missing. The planner's `gasLawPlan` needs three of the four, with "at STP" counting
  as two (1 atm and 273.15 K). It also needs a gas-law word, STP or a pressure, so a volume and an
  amount alone stay a solution question. A mass with a formula ("16 g of O2") is turned into moles
  first. `UnitConverter` now knows atm, bar, mmHg and Torr.
- **pH.** `ai/data/AcidBase` covers the strong acids (HCl, HBr, HI, HNO3, HClO4, HClO3, and H2SO4
  with both protons) and the strong hydroxides of groups 1 and 2. A formula or a name is matched by
  composition, so "sodium hydroxide" and "BaO2H2" both resolve. The ion concentration solves
  x = (c + √(c² + 4Kw)) / 2, so a very dilute acid stays below pH 7. A weak acid needs a Ka the app
  does not have, so `phPlan` does not claim it and it falls through to the decline.
- **Ion configuration.** `ai/data/IonConfig` derives an ion's configuration from the element's own.
  A cation loses electrons from the highest shell, then the highest l, so Fe³⁺ is [Ar] 3d⁵ and lead
  keeps its 6s². An anion fills in Madelung order. A result with a noble gas's electron count reads
  as that gas ("[Ne]") and names it. `ionConfigPlan` runs before every other calculation, because
  "Fe3+" otherwise resolves to iron and gets the neutral atom's configuration. It reads Fe3+, Fe 3+,
  Fe+3, Fe^3+, Fe³⁺, Cl- and O2-, and only beside a configuration word.

**More calculators, stage 2** (roadmap 2.8, PRO+). Percent composition already came with the
formula-mass answer. Stage 2 adds:
- **One element's share.** A composition question that names one of the compound's elements ("mass
  percent of oxygen in water", "Fe in Fe2O3") leads with that element's share. `COMPOSITION_WORDS`
  gains the mass-percent words of every language, so German "Massenanteil" is no longer declined.
- **Empirical formula.** `ai/data/Empirical` turns element percentages or masses into moles, divides
  by the smallest, and multiplies by up to 8 until every ratio is within 0.1 of a whole number
  (72.4% Fe and 27.6% O give Fe3O4). The formula is written in Hill order. A molecular formula is
  instead reduced by its common factor (C6H12O6 to CH2O). With a molar mass given, the molecular
  formula follows (CH2 and 56 g/mol give C4H8). `empiricalPlan` needs an empirical-formula word, or a
  molecular-formula word with a molar mass. Before this, "40% C, 6.7% H and 53.3% O" was answered as
  a comparison of the three elements.
- **Stoichiometry and the limiting reagent.** The balancer drops lowercase words but keeps numbers,
  so "4 g of H2 in 2H2 + O2 -> 2H2O" was balanced as nonsense with a species "H2 2H2".
  `EquationBalancer.span` now cuts the equation out of the sentence: from the arrow outwards,
  species and "+" alternate, and the first word that breaks the pattern ends that side.
  `stoichiometryPlan` runs before the balance branch and claims a sentence only when an amount (g or
  mol) is tied to one of its species. The species is written or named in the next three words after
  the amount ("16 g of methane"). A species named without an amount is the target.
  `ai/data/Stoichiometry` turns each amount into moles and divides by its coefficient; the smallest
  over the given reactants is how far the reaction runs. With two reactants given, that one is the
  limiting reagent, and the answer gives the products and what is left of the other. Without an
  equation ("CO2 from 10 g of CH4") the question is still declined: the app cannot know the
  reaction.

`corpus/calculators.tsv` holds 40 rows, 21 from stage 1 and 19 from stage 2; each failed before its
stage. `CalculatorMathTest` checks the math on both platforms. One gap remains: "how many moles of
O2" leads with grams, and the moles follow in brackets.

**Facts read off the data** (roadmap 2.7). `ai/data/DataFacts` ranks ten free fields and keeps
each field's top and bottom: twenty facts such as "Osmium has the highest density of the 99 elements
with a recorded value: 22.59 (g/cm^3)". The fields are density, melting and boiling points,
electronegativity, atomic radius, ionization energy, thermal conductivity, specific heat, and
fusion and vaporization heat. Atomic mass is left out, since it follows atomic number.
- **Up to fermium.** Only elements up to fermium count. Past it most values are predictions; with
  them, seaborgium's predicted 35 g/cm³ was the densest element.
- **Ties.** On a tie at either end the lighter element wins: caesium and francium share the lowest
  electronegativity, and francium's is an estimate.
- **Per element.** `forElement` gives an element's place when it is within three of either end.
  `forDay` cycles through the extremes.

Where the facts appear:
- **Chat:** a fun fact now draws from the ten shipped facts and the twenty data facts together.
- **Science Daily widget:** a data fact on even days (`AIPersonality.getDailyFact`).
- **Element of the Day widget:** the element's place leads its description when it has one
  (`getElementFact`).

The widgets show plain text, so the Markdown bold is stripped there. The `ai_fact_data_*`
templates in German, Hindi and Urdu avoid gender agreement with the field name.

**The study coach** (roadmap 2.6, PRO). The coach schedules the quiz questions a user gets wrong and
brings each one back just before it would be forgotten. The cards, the scheduler and the synced
field are defined in the shared data's `sync/study.md`, before either app's code.

- **Cards.** One card per skill and element, keyed `periodic_group:bromine`. The skills are the five
  Learning Games categories that ask about one given element: classification, period, group,
  valence electrons and isotope neutrons. `QuizQuestion` now carries `subjectKey` and `category`;
  the generators that ask about one element fill them in.
- **Feeding.** An answer feeds its card wherever the question was asked: the Learning Games
  (`StudyStore.record`, a timeout counting as a miss), a chat quiz run, or a review. Right rates Good
  and wrong rates Again. A question about no single element (rankings, molar mass) feeds nothing.
- **Scheduler.** `study/Fsrs.kt` is FSRS-5 with its default parameters. `tools/fsrs_golden.py` in the
  shared data is the reference. `golden/fsrs.tsv` holds its vectors, and `FsrsTest` on both
  platforms checks them: stability and difficulty to 1e-9, the interval to the day.
- **Review.** "Review" or "what should I study" in chat (twelve languages, short messages only)
  starts a run of the due cards, most overdue first, at most ten. It reuses the 2.5 run machinery:
  each card's generator is scoped to its element, so the options are fresh every time. Without PRO,
  the reply says what the coach would do.
- **Storage and sync.** `StudyDeck` is pure and holds the cards. `StudyStore` keeps them in
  `study_cards.tsv` and gives the chat a `StudyAccess`. The progress sync carries them in
  `study.cards`, merged per card with the later review winning (`ProgressMerge.mergeStudy`). Android
  writes with `SetOptions.merge()`, Windows with an update mask of `study`.

**Quiz runs** (roadmap 2.5). "Quiz me on the halogens", "five isotope questions on uranium" and
"frågesport om ädelgaserna" start a run of multiple-choice questions, asked one at a time with the
options as chips. The questions come from the Learning Games generators (`quiz/`), and each right
answer earns that game's XP through `ChatOrchestrator`'s `onXp`. `AiChatAgent` passes
`XpManager.addGameXp`, so chat XP counts towards the user's level like the games'.

A request becomes a run when it names a scope, a count or a kind of question. A bare "quiz me" keeps
the single free-text question of `ElementQuiz`. `QuizRun.request` reads:
- the count: digits, or number words up to ten in each language; 5 by default and 10 at most. "Period
  3" is a scope, not a count, and Chinese needs a measure word (五道题), since 三 also opens 第三周期.
- the kind: isotope words give the four isotope games, "molar mass" the molar-mass game, and
  "highest" or "lowest" the ranking games. Anything else gets a mix of classification, position,
  valence, ranking and isotope-neutron questions.

The scope comes from `OperatorExtractor`, the same reading of families, blocks, phases, periods and
groups the engine uses, or else from the elements the request names. The planner itself is not
asked, because it declines any message with a quiz word in it. `GeneratorContext` takes the scope as
a set of keys. The element a question is about comes from it; wrong options still come from the
whole table, since a question about bromine's period needs other periods. A generator the scope
cannot feed returns nothing and is skipped: one element has no "which of these is densest".

During a run:
- a pick is an option said exactly (a chip) or alone inside a short sentence ("it's sodium");
- "stop" ends the run with the score so far;
- any other message leaves the run and is answered as usual.

The run is not persisted: it belongs to one sitting.

Building quiz runs exposed a data defect: radon's `element_group` said "Halogens", and fluorine
and chlorine were filed as reactive nonmetals. So every "halogens" filter gave bromine, iodine,
astatine, tennessine and radon. The shared element files now file fluorine and chlorine as halogens
and radon as a noble gas, in all twelve languages. Three aggregate rows over "the halogens" in
`morphology.tsv` changed their expected values with it.

**Explanations** (roadmap 2.4). "Why does atomic radius increase down a group", "why are noble
gases unreactive" and "why is mercury a liquid" plan to `Intent.EXPLANATION`: a written explanation,
shown with the data it explains. There are 60, the number the roadmap asks for. Stage 1 wrote 20:
the radius, ionization, electronegativity, melting-point and electron-affinity trends, and concepts
such as the ionization dips and jumps, noble gases, halogens, the lanthanide contraction and
mercury. Stage 2 added 40:
- trends in boiling point, density, electron affinity down a group, thermal conductivity, specific
  heat and work function;
- concepts such as the halogens' states, the transition metals' radii, ionization and melting
  points, helium's ionization energy, the electron-configuration exceptions, tungsten, gallium, the
  noble metals, the inert pair, the diagonal relationship, where hydrogen and helium sit, xenon
  compounds, carbon, metalloids, shielding, periodicity, polar and ionic bonds, and why atomic
  masses are not whole numbers.

A card for a PRO field (electron affinity) is left off for a free user. The text is free, and the
electron-affinity texts quote no values. Stage 1 had shown the PRO values in the card to everyone.

Trigger phrases are substrings, so a short part has to be unambiguous on its own. French "or"
(gold) sits inside "chlore", Chinese 金 inside 金属 (metal), Spanish and Italian "oro" inside
"cloro". The gold triggers therefore say "l'or est", 黄金 and "el oro" / "l oro". The planner's text
keeps the apostrophe, so French triggers spell "l'or" as well as "l or".

The data is in the shared repository. `datasets/explanations.tsv` says what each explains (a field
and a scope: group, period or table) and which card goes with it (`trend:<field>:group=1`,
`trend:<field>:table`, `trend:<field>:series=lanthanoid`, `ionization:sodium`).
`datasets/explanations_<lang>.tsv` holds the words in each of the twelve languages: the text and
its trigger phrases (`|` between alternatives, `+` between parts that must all appear). The
non-English files are machine-translated, with `reviewed` left blank until a native speaker checks
them. A row a language lacks falls back to English, and the answer then says the text is in English,
as a dictionary entry does. The texts cite numbers from the app's data (Na 0.93 and Cl 3.16, Hg
−38.8 °C), so the card beside each one shows the same values.

`Explanations` (`ai/data`) chooses one in two ways: by trigger phrase, where the longest match wins,
so "why is fluorine the most electronegative" gets the fluorine explanation rather than the group
trend; or by a field the question names plus a direction from `Lexicon.EXPLAIN_DOWN_WORDS` or
`EXPLAIN_ACROSS_WORDS`. A related field counts as its family: any radius means atomic radius. The
planner tries this before the dictionary, for a why or a mechanism only. A definition ("what is
electronegativity") still gets the dictionary. A why with an operator in it ("the most …") reaches
the explanation when one exists; before, the operator sent it to the ranking. The same applies to
the unbacked-concept guard: "why is fluorine the most reactive halogen" is no longer declined as a
reactivity ranking. Chinese has no spaces, so a why word there is matched on its own; "为什么" was
never recognised before. `QueryExecutor` builds the card's data when it answers: the trend through
the 2.3 path, or the element whose ionization series the card shows. `TREND` now sits before
`SUBSET_LIST` in the arbiter, because "how does X change down group 1" could be claimed by either
and the trend is the better reading.

`explanations.tsv` joins the corpus (38 files), with `expect_row` reading `explanation/<key>`. Five
earlier rows that the dictionary answered or that deferred now expect an explanation. So does one
held-out row, `ds-why-1`: it failed at the end-of-item held-out run, and only its expectation was
updated. Nothing was tuned against it. Stage 2 adds a row per new explanation in English and two in
each other language; held-out passed unchanged.

Two data findings were left for their own change. Titanium's atomic radius is stored as 76 pm,
about a hundred too small, so the period-4 radius card dips at titanium; the transition-radius text
avoids it. Resistivity's scale multipliers are inconsistent (silver's comes out ten times copper's),
so no explanation uses it, and the conductor texts use thermal conductivity instead.

**Trends** (roadmap 2.3). "Electronegativity across period 3", "how does atomic radius change
down group 1" and "a heat map of density" plan to `Intent.TREND`: one measurable field over the
elements a period, group or series leaves, in atomic-number order, or over the whole table when the
question asks for a map (`Lexicon.TREND_WORDS`, `HEATMAP_WORDS`, twelve languages). A ranking, a
statistic, a threshold or a named element asks something else, and a position — period, group,
atomic number — is not a trend. `ExecutionResult.Trend` keeps every element with a value, unranked
and uncut, and counts the ones without. The answer lists the values by symbol, says whether they
rise or fall overall from the first element to the last (70% of the steps one way; ionization energy
across a period dips and still rises), and names the extremes; a map gives the span and the
extremes only. A "how does … change" with a field and a trend word is no longer taken as a
mechanism question; a "why" still is.

Two cards draw it, the first about many elements: `TREND_LINE` (`TrendLineView`) and `HEAT_MAP`
(`HeatMapView`, a mini periodic table with the f-block below it, cells shaded on a log scale when
the field spans a decade, and an outline where an element has no value). The card's element is
only its first point; its arguments carry the field and, for the line, the elements in order, and
`TrendSeriesReducer` and `HeatMapReducer` read the values back out of the store. `trends.tsv` pins
each trend's first value.

Two defects turned up on the way. "Which element in period 3 has the highest electronegativity"
read the 3 of "period 3" as "top 3" and listed three; `OperatorExtractor` no longer counts a number
a period or group has read. And the data filed fluorine and chlorine as reactive nonmetals, so "the
halogens" started at bromine everywhere in the app; that was fixed with roadmap 2.5. The predicted
densities that make seaborgium the densest element are a data matter left for its own change.

**Steps** (roadmap 2.2). Every calculation answer shows a short working: the atom check for a
balance, n = c·V for a solution, the number of half-lives for a decay, the composition of a molar
mass. Asked for the steps — "step by step" with the question, or "show the steps" after it, which
the follow-up chips offer under every calculation — it shows the derivation instead, numbered,
from `compose/Workings.kt`:

| Calculation | The steps |
|---|---|
| Balance | An unknown coefficient per formula; one equation per element ("O: 2b = 3c"); the smallest whole numbers that solve them; the atom check |
| Solution | The volume in litres; n = c × V or n = m ÷ M; m = n × M, c = n ÷ V or V = n ÷ c; for a dilution c₁V₁ = c₂V₂ and the solvent to add |
| Decay | t½ in the unit asked; n = t ÷ t½; N ÷ N₀ = (½)ⁿ; N = N₀ × (½)ⁿ — or, for the time, n = log₂(N₀ ÷ N) and t = n × t½ |
| Moles | M; n = m ÷ M, or N = n × N_A |
| Molar mass | Each element's count × atomic mass, and their sum |

The lines are mathematics with the numbers put in, at four significant figures; a legend string
names the symbols in the reader's language. The planner sets `QueryPlan.showSteps` on a calculation
asked for with its steps, and `Branch.SHOW_STEPS` re-plans the previous calculation with it, ranked
after `CALCULATION` so a new calculation in the same message is the one answered. `StepsTest` asks
for the steps of each kind, and of every locale's own balancing chip in its own words.

Building it found that the balancer read prose sharing a token with a formula as elements: the
German chip "Gleiche Fe + O2 -> Fe2O3 aus" answered that gold appears on one side, "balance C3H8 + O2
-> CO2 + H2O" that barium, lanthanum, nitrogen and cerium do, and "step by step" grew sulfur. A
lower-case word with no digit beside a formula is now dropped. The corpus rows had passed on the
intent alone; `expect_value` on a balanced equation is now its coefficient sum, and
`balance_equations.tsv` (corpus format 2) carries it for every row that balances.

Building the follow-up chips found three planner gaps, fixed with rows in `unit_convert.tsv`: "kg/m³" with a
superscript was not a unit; the capital in "°F" and "°C" was read as fluorine or carbon, turning
"melting point of gold in °F" into a comparison; and Afrikaans "na" (to) was not a conversion
preposition, so the app's own chip "Skakel 500 K om na °C" had been answered as potassium against
carbon.

---

## AiChatAgent

`class AiChatAgent(context: Context)`

The Android half, owned by `AiChatPanelController`. It wraps one `ChatOrchestrator` and supplies
what that must not know:

- the daily allowance (`AIRateLimiter`), checked before the orchestrator is asked;
- strings from a locale-wrapped `Context` per language, without `Locale.setDefault`;
- the user's entitlements and the offline card policy;
- `AiTurnStats` for the panel to log — the path (`engine`, `personality`, `reactivity`,
  `fallback`, `rate_limited`, `context_lost`), intent, confidence, language and query shape,
  and on a fallback the `DeclineReason` (`no_match`, `low_confidence`, `no_result`,
  `unbacked_concept`, …) so the console shows *why* questions go unanswered;
- the **Send this question** chip on every declined reply (`DeepLinkTarget.REPORT_QUESTION`).
  The panel intercepts it, asks in a dialog that says exactly what is sent, and only then
  writes the question to the create-only `unanswered_questions` collection
  (`UnansweredQuestions.kt`; see [Persistence and sync](../development/persistence-and-sync)).
  The analytics event `ai_question_sent` carries the reason, never the text;
- the interest counts behind the returning-user greeting, kept in `ai_learning_prefs` /
  `element_interests` where the old `AILearningManager` kept them.

---

## AIPersonality

`object AIPersonality`

Stateless singleton. Methods return localised strings by creating a `ConfigurationContext` for the
requested language, then reading from `strings.xml`. Used by the chat panel for its opening
greeting and by the Science Daily widget; the conversation itself reads its strings through the
orchestrator.

All strings are defined in `res/values/strings.xml` (and language-specific overrides, e.g.
`res/values-sv-rSE/strings.xml`).

---

## AIRateLimiter

`class AIRateLimiter(context: Context)`

Enforces daily message quotas stored in `ai_rate_limit_prefs` `SharedPreferences`.  
The key format is `msg_count_{year}_{month}_{day}` — quotas reset automatically each calendar day.

| Tier | Daily limit |
|------|-------------|
| Free | 16 messages |
| PRO | 64 messages |
| PRO+ | Unlimited |

```kotlin
if (rateLimiter.canSendMessage()) {
    rateLimiter.incrementMessageCount()
    // … generate response
}
val remaining = rateLimiter.getRemainingMessages()
```

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

1. User changes language in settings **or** the agent auto-detects the language of a message.
2. The orchestrator's `state.activeLanguage` changes; nothing is persisted, since the next
   message is detected afresh.
3. Strings come from a `ConfigurationContext` with the new locale, so property labels and
   section headers are in the right language too.
4. The display overlay for `assets/elements_{code}.json` names elements and translates the
   prose fields.
5. `AiChatAgent` creates a `ConfigurationContext` with the new locale so all UI strings
   (property labels, section headers) are also in the correct language. Note this deliberately
   does **not** call `Locale.setDefault` — the chat language is auto-detected per message, and
   making it the process default would leak into every formatter in the app.
6. The cached `AiEngine` is discarded so its localized field aliases and BM25 index are rebuilt
   for the new language.

### Auto-detection

Element names alone carried no signal for a question naming no element, so "hur många ädelgaser
finns det" was answered in English. Since roadmap 1.4, `RetrievalService.detectLanguage(message,
active)` asks `LanguageDetector`, a naive Bayes model over the character trigrams of each word.
Its counts are `shared/language/trigrams.tsv`, which `LanguageModelGoldenTest` builds from the app
strings, `Lexicon.STOPWORDS`, the element descriptions (less any sentence still in English) and the
dataset titles, never from the corpus. The Windows app reads the same file.

- Features are the trigrams of each word, lowercased, NFKC-normalised and padded with spaces, with
  accents kept. Chemical formulas and the unit names in `Lexicon.UNIT_WORDS` are not words of any
  language, and each Han character is a word of its own.
- Each word votes, and no word decides: a language loses at most `WORD_CLIP` nats on one word, so
  "y en fahrenheit" no longer outvotes Spanish with German-looking letters.
- A message of one word goes to `EntityResolver.detectLanguage`, the old element-name tally: "guld"
  is Swedish, "gold" keeps whatever language is in play. The same tally breaks a near-tie between
  the two best languages.
- A language takes over only when it beats the one in play by `SWITCH_MARGIN` nats. Both margins
  were measured on the dev corpus (see `corpus.md`), where no row is switched away from its own
  language.

---

## Chat History & Persistence

Chat history is only stored for users signed in with a Google account (Firebase Auth).

```
Sign-in detected
    └── ChatHistoryManager.loadLatestChatSession()
            └── Restore messages into the panel
                └── Apply session language via setLanguage()

User sends a message
    └── ChatHistoryManager.saveChatSession(currentSession)

Language changed
    └── Persist to SharedPreferences + save current session with new language
```

Offline / anonymous users: the messages live only in the panel's list, and the conversation's
memory in the orchestrator's `DialogueState`, for the duration of the session.

---

## Rate Limiting & Tiers

```
Free  → 16 messages / day
PRO   → 64 messages / day
PRO+  → Unlimited
```

Counts are stored per-day in `SharedPreferences` under the key
`msg_count_{year}_{month}_{day}` — they reset automatically on calendar rollover.

The limit check happens at the **start** of `AiChatAgent.generateResponse`, before the
orchestrator is asked:

```kotlin
if (!rateLimiter.canSendMessage()) {
    return reply(strings.get(R.string.ai_rate_limit_reached, rateLimiter.getDailyLimit()) + reset, …)
}
rateLimiter.incrementMessageCount()
```

---

## Adding a New Language

1. Create `shared/elements/elements_{code}.json` in the shared-data submodule.  
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

Add rows to the corresponding model class (e.g. `EquationModel`). `DatasetIndex` reads every
model class, so the engine can cite the new rows; add a `datasets.tsv` corpus row that asks for one.

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

### Data defects, fixed

`RealCorpusTest` used to carry two defects in `elements_*.json` as known-bad: lanthanum and cerium
held their **boiling** points in the melting-point fields, and gold never marked Au-197 stable. Both
were fixed by the merged data now in the shared submodule (roadmap 0.3). The test now asserts
tungsten heads the melting points with nothing excluded, that no element melts above its boiling
point, and that 80 elements report a stable isotope. Roadmap 0.8 fixed the rest; the Windows
porting notes, D58, list them.
