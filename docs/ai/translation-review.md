---
title: Translation review
parent: AI Agent
nav_order: 8
---

# Translation review worklist

All 14 shipped locales now resolve every one of the 330 `ai_*` agent strings.
`StringCoverageTest` fails the build if that stops being true.

## What needs a native speaker, and what does not

I wrote these translations. Six languages I can write to a shippable standard; five I cannot verify,
and those are marked in the XML with a `REVIEW: machine-assisted` comment above the block they were
added in.

| Locale | Strings added | Confidence | Action |
|---|---|---|---|
| `values-sv-rSE` | 108 | Shippable | Spot-check only |
| `values-de` | 96 | Shippable | Spot-check only |
| `values-fr` | 82 | Shippable | Spot-check only |
| `values-es` | 82 | Shippable | Spot-check only |
| `values-it-rIT` | 82 | Shippable | Spot-check only |
| `values-pt-rBR` | 82 | Shippable | Spot-check only |
| `values-af` | 82 | **Machine-assisted** | Needs a native reviewer |
| `values-b+fil` | 82 | **Machine-assisted** | Needs a native reviewer |
| `values-hi` | 82 | **Machine-assisted** | Needs a native reviewer |
| `values-ur` | 82 | **Machine-assisted** | Needs a native reviewer, including RTL rendering |
| `values-zh-rCN` | 82 | **Machine-assisted** | Needs a native reviewer |

What *is* verified in every locale, including the machine-assisted ones: the format placeholders
(`%1$s`, `%2$d`) match English exactly — `scripts/add_ai_translations.py` refuses a string whose
placeholder set differs, because a mismatch is a crash at format time, not a cosmetic error — and
the `**bold**` markup the chat renderer understands is preserved.

## Two structural fixes worth knowing about

**Ordinal suffixes.** `AnswerComposer` picks between `ai_ordinal_suffix_st/nd/rd/th` using the
English 1st/2nd/3rd/th rule. That rule does not exist in most languages, so rather than leak "3rd"
into a Hindi sentence each locale defines what makes sense for it: Swedish `:a`/`:e`, German and
French their own forms, and Chinese, Hindi, Urdu and Filipino **empty strings**, because those
languages mark ordinals with a prefix or a separate word. The composer's numeral still renders.

**Spanish and Urdu region folders.** `values-es-rAR` and `values-es-rMX` were missing 248 agent
strings each, and `values-ur-rIN` 207 — far more than any other locale. The cause was resource
resolution, not translation: Android walks `values-es-rAR` → `values-es` → `values`, and there was no
`values-es`, so those users fell straight through to English. The agent strings now live in
`values-es` and `values-ur`, which every region of those languages resolves through.
`StringCoverageTest.regionVariantsResolveThroughTheirBaseLanguage` guards it.

The two empty locale folders `values-b+es` and `values-it` were removed — they advertised a
supported language and contained nothing.

## Separate backlog: half-translated element descriptions

This is a **data** problem, not a strings problem, and it is not fixed.

```bash
python scripts/check_description_translations.py
```

| Locale | Descriptions still partly English |
|---|---|
| `af` | 104 / 118 (88.1%) |
| `sv` | 84 / 118 (71.2%) |
| `fil` | 84 / 118 (71.2%) |
| `fr` | 23 / 118 (19.5%) |
| `hi`, `pt` | 2 / 118 |
| `it` | 1 / 118 |
| `de`, `es`, `ur`, `zh` | 0 |

These are sentences like *"Titanium är ett kemiskt grundämne … Det är a lustrous transition metal
with a silver color"* in `assets/elements_sv.json`. A naive "is it translated?" check passes, because
none are byte-identical to English — they are a translated sentence followed by an untranslated one.

The code-side mitigation is in: `AIAgentManager.sentencesInActiveLanguage` drops sentences that read
as English when the conversation is not in English. Previously the narrative handlers filtered
description sentences by *English* keywords, so they preferentially picked the untranslated half —
that is exactly how the reported *"Det har various allotropes, but only the gray form…"* was
produced. Now those sentences are skipped, and if every sentence looks English the original list is
kept, on the grounds that a partial answer in the wrong language still beats no answer.

Repairing the underlying asset text needs a native speaker per language. Run the script with
`--list sv` to print the offending descriptions for one locale.

## Also outstanding: hazard wording on the element screen

`InfoExtension.setHazards` still holds its NFPA descriptions as hardcoded English literals
("Will not burn", "Above 93.3°C", "Capable of detonation or explosive decomposition") in an app
shipping twelve languages.

They were deliberately **not** folded into `NfpaLabeller` during the card work. The agent's wording
is descriptive ("Must be preheated to burn") while the screen's is flash-point based
("Above 93.3°C"); both are legitimate NFPA phrasings, and delegating would have silently changed
what the element screen says to every existing user. The shared *layout* was extracted — the diamond
markup now lives once in `view_nfpa_diamond.xml`, so the screen and the chat card cannot drift apart
— but the screen keeps its own text.

Moving those ~17 strings into resources and translating them is a self-contained follow-up.
