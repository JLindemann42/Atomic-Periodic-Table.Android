---
title: Localisation
parent: Developer Guide
nav_order: 8
---

# Localisation

Two independent translation surfaces: Android string resources (the interface)
and the element JSON files (element names and descriptions). Both must be
updated for a language to be complete.

## Supported locales

17 `values-*` resource directories covering 12 base languages:

| Language | Resource dirs |
|:--|:--|
| English | `values` (default) |
| Afrikaans | `values-af` |
| Chinese (Simplified) | `values-zh-rCN` |
| Filipino | `values-b+fil` |
| French | `values-fr` |
| German | `values-de` |
| Hindi | `values-hi` |
| Italian | `values-it-rIT` |
| Portuguese (Brazil) | `values-pt-rBR` |
| Spanish | `values-es`, `values-es-rAR`, `values-es-rES`, `values-es-rMX` |
| Swedish | `values-sv-rSE` |
| Urdu | `values-ur`, `values-ur-rIN`, `values-ur-rPK` |

`values-night` and `values-v23` … `values-v31` are theme and API-level
qualifiers, not locales.

Element data ships for the same 12 base languages as
`assets/elements_{af,de,en,es,fil,fr,hi,it,pt,sv,ur,zh}.json`. Note the element
files use base language codes only — regional Spanish and Urdu variants share one
element file each.

## Runtime language switching

The app's language is independent of the system language. It is stored as
`app_language` / `app_country` in the `"settings"` SharedPreferences file and
applied per-activity:

```kotlin
// activities/BaseActivity.kt
override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleUtil.wrap(newBase))
}
```

`utils/LocaleUtil.kt` builds a configuration-wrapped context. Because this
happens in `attachBaseContext`, a language change takes effect on the next
activity creation rather than requiring an app restart or a trip to system
settings.

**Any activity not extending `BaseActivity` will ignore the language setting.**

`ElementDataLoader.getAppLanguage(context)` reads the same key, falling back to
the system locale, and is what selects which `elements_*.json` to load.

## Which element fields are translated

Only seven fields differ between the twelve element files:

`element`, `element_group`, `description`, `element_appearance`,
`element_phase`, `electrical_type`, `magnetic_type`

All numeric and unit data is identical across files. The AI engine relies on
this: `ai/data/KnowledgeStore` parses English once and overlays these seven per
language.

If you add a translatable element field, you must also add it to the
`LocalizedView` overlay list, or the English value will show in every language.

## Fallback behaviour

`ElementDataLoader` falls back to `elements_en.json` when a language file is
missing, or when an element key is absent from it. A partially translated
language degrades field by field rather than failing — which is why translation
gaps are easy to miss without running the verification scripts.

## Localisation inside the AI engine

The assistant handles language differently from the rest of the app, in three
ways worth knowing:

**Per-message detection.** `AIAgentManager.detectResponseLanguage(query)` scores
each word against a cross-language element-name map built from all twelve files.
A higher-scoring non-active language switches the response language for that
message and rebuilds the cached engine.

**No `Locale.setDefault()`.** Answers are composed through
`ai/core/StringProvider.kt`, whose `AndroidStrings` implementation builds a
`ConfigurationContext` for the resolved language. This is deliberate: an
auto-detected chat language must not leak into the rest of the app's formatting.

**Field labels double as query aliases.** `ai/nlu/FieldResolver` derives its
aliases from the app's own localised `labelRes` strings first. Translating a
field's UI label automatically teaches the assistant that word — no separate
NLU translation pass.

The engine's operator vocabulary (comparators, superlatives, aggregations,
series names) is hand-maintained per language in `ai/nlu/Lexicon.kt`, which is
around 1,490 lines and is the main cost of adding a language to the assistant.

## Known gaps

**No RTL mirroring.** The manifest sets `android:supportsRtl="false"` with a
`tools:replace` override — meaning some dependency requests RTL and the app
explicitly overrides it off. Urdu is fully translated but renders in a
left-to-right layout. Fixing this means removing the override and auditing every
layout for hardcoded `left`/`right` (rather than `start`/`end`) attributes.

**Reference datasets are English-only.** Constants, equations, the dictionary,
ions, indicators, the electrode series, solubility, geology and alloys are not
translated. The assistant glosses a foreign-language query to English for
*searching* (`Lexicon.DATASET_GLOSS`) and frames the answer in the user's
language, but the table content stays English.

**Translation completeness varies.** Historical status reports disagree with
each other; the authoritative answer is whatever
`scripts/verify_element_jsons.py` reports today. See [Project history](history).

## Adding a language

1. Add `res/values-{code}/strings.xml`, translated from `values/strings.xml`.
2. Add `app/src/main/assets/elements_{code}.json` — copy the English file and
   translate the seven localised fields. `ElementDataLoader.getAvailableLanguages`
   discovers it automatically from the filename.
3. Add the language to the picker in `SettingsActivity`.
4. For assistant support, extend `ai/nlu/Lexicon.kt` with the operator and
   series vocabulary, and add the language to `EntityResolver`'s alias table.
5. Verify with `python3 scripts/verify_element_jsons.py --detailed` and
   `python3 scripts/check_translations.py`.

Steps 1–3 give a fully localised app. Step 4 is what makes the assistant work in
that language, and is by far the largest piece.

See also the [Data Pipeline](../data-pipeline/translations) section for the
tooling.
