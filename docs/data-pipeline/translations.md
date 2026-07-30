---
title: Translations
parent: Data Pipeline
nav_order: 2
---

# Translation tooling

Two separate surfaces need translating, and they use different tooling:

| Surface | Files | Checked with |
|:--|:--|:--|
| Interface strings | `res/values-{locale}/strings.xml` | `check_translations.py` |
| Element names and descriptions | `assets/elements_{lang}.json` | `verify_element_jsons.py` |

See [Localisation](../development/localization) for what is translated and the
current coverage.

## Checking status

```bash
python3 scripts/check_translations.py
```

Reports per language: total strings, missing strings, and strings that appear
untranslated because they match English exactly.

That last heuristic produces false positives — many technical terms are
legitimately identical across languages. Treat it as a worklist, not a defect
count.

```bash
python3 scripts/verify_element_jsons.py --detailed
```

Shows element name and description completeness per language, with better
heuristics than a raw string comparison. This matters for element names in
particular: helium, lithium, neon, argon and titanium are identical to English in
several languages, so a naive comparison reports German element names as 35%
translated when most of that gap is correct-by-identity.

## Exporting work

```bash
python3 scripts/extract_missing.py
```

Writes `untranslated_strings.csv` to the repository root — one row per missing
string, suitable for importing into a spreadsheet or a translation platform
(Crowdin, POEditor, or similar).

The CSV is a working file. Do not commit it.

## Applying translations

Interface strings go into the relevant `res/values-{locale}/strings.xml` by hand
or by script. Element descriptions go into `assets/elements_{lang}.json`.

Afterwards, always:

```bash
python3 scripts/fix_string_escaping.py     # unescaped apostrophes etc.
python3 scripts/verify_element_jsons.py
./gradlew :app:testDebugUnitTest           # StringCoverageTest
```

`fix_string_escaping.py` matters more than it sounds. An unescaped apostrophe in
a French or Italian string breaks the resource compiler, and a bulk translation
pass produces a lot of apostrophes.

`StringCoverageTest` fails the build if any `ai_*` agent string is missing from
any shipped locale — that is the guard keeping the assistant's vocabulary
complete.

## The historical batch scripts

`scripts/` contains around 25 further Python files from a 2025 translation
campaign. They are successive one-off passes:

| Group | Files |
|:--|:--|
| Percentage pushes | `push_to_60_percent.py`, `push_toward_60.py`, `push_60_plus.py`, `continue_toward_65.py`, `push_toward_70.py` |
| Bulk updates | `batch_updater.py`, `large_batch_update.py`, `mega_batch_update.py`, `bulk_translate_processor.py`, `comprehensive_push.py` |
| Translation drivers | `auto_translate.py`, `ai_translate_elements.py`, `batch_translate_elements.py`, `comprehensive_translate.py`, `translate_all_elements.py`, `translate_element_descriptions.py`, `translate_strings_manual.py`, `continue_all_languages.py` |
| Language-specific | `translate_swedish.py`, `fix_urdu_translations.py`, `update_all_urdu_descriptions.py`, `urdu_translator_template.py`, `complete_urdu_translations.py`, `translate_urdu_descriptions.py` |
| Supporting | `add_ai_translations.py`, `complete_all_translations.py` |

Two of these (`complete_urdu_translations.py`, `translate_urdu_descriptions.py`)
are zero-byte files.

They are kept because they encode the translations that were actually applied,
but they are not a maintained toolkit — they overlap, they hardcode language
lists and target percentages, and several would need rewriting to run again
usefully.

**For new translation work**, prefer exporting with `extract_missing.py`,
translating externally, and applying the result, over resurrecting one of these.

## Machine translation and review

Much of the existing translation was machine-assisted. Where output needs a
native speaker's eye it is tracked in
[Translation review](../ai/translation-review), which lists Urdu agent queries
flagged for checking.

If you are a native speaker of any shipped language, reviewing the element
descriptions is among the most useful contributions available — the data is
correct but the phrasing has not been checked by a human for most languages.

## Adding a language

See [Localisation](../development/localization#adding-a-language) for the app
side and [Extending the engine](../ai/extending#add-a-language) for the
assistant side.
