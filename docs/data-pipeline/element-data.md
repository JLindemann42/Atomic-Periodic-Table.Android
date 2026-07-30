---
title: Element data
parent: Data Pipeline
nav_order: 1
---

# Element data tooling

Maintaining `app/src/main/assets/elements_{lang}.json` — twelve files, 118
elements each, ~80 non-isotope fields per element. See the
[element data model](../development/data-model) for the schema.

## `verify_element_jsons.py`

The authoritative checker. Run this after any change to the element data.

```bash
python3 scripts/verify_element_jsons.py
```

```bash
python3 scripts/verify_element_jsons.py --detailed
```

```bash
python3 scripts/verify_element_jsons.py --json-output
```

Checks:

| Check | Catches |
|:--|:--|
| JSON structural validity | Trailing commas, broken escaping, encoding damage |
| Consistency against the English reference | A field added to one file but not the others |
| Element count | All 118 keys present in every file |
| Field presence | Fields missing from individual elements |
| Translation completeness | Per-language statistics with `--detailed` |
| Data integrity | Malformed values |

`--json-output` emits machine-readable results for scripting.

The older `check_element_translations.py`, `check_translations.py` and
`check_description_translations.py` predate this and cover subsets of the same
ground. Prefer this one.

## `populate_element_data.py`

Fills `"---"` placeholders from reference sources — IUPAC, NIST, the CRC
Handbook and WebElements. Around 715 lines, most of it hardcoded per-element
lookup tables.

```bash
python3 scripts/populate_element_data.py
```

It writes to all twelve language files at once, which is the point: a numeric
value is identical across languages, so it must land in all of them together.

To add values for a new field, extend the lookup dictionary at the top of the
script and re-run. **Do not fabricate values.** Leave `"---"` where no
authoritative published value exists — the app renders that as an explicit
"no data" state, and a plausible-looking wrong number is worse than a blank.

## `check_field_registry.py`

Cross-checks the element JSON against `ai/data/FieldRegistry.kt` — catching a
field present in the data but never registered (so unanswerable by the
assistant), or registered against a JSON key that does not exist.

Run it after adding a field. See
[Extending the engine](../ai/extending#make-a-new-element-field-answerable).

## `fix_string_escaping.py`

Repairs escaping damage in `strings.xml` files — unescaped apostrophes and
similar, which is the most common way a bulk translation pass breaks the build.

## The `"---"` convention

Load-bearing. It means "no authoritative published value exists" and is distinct
from a field being inapplicable.

```
"resistivity": "---"          ← no published value
"thermal_expansion": "---"    ← no published value
```

`ai/data/ValueParser.kt` maps it to `FieldValue.Missing`, which the AI engine
turns into `ExecutionResult.NoData` and the element page renders as an explicit
no-data row. Replacing it with `null`, `""` or `0` breaks all three.

Other absent-value forms the parser also accepts, seen in the existing data:
`""`, `"N/A"`, and JSON `null`. Prefer `"---"` for anything new.

## Adding a field to the data

1. Extend the appropriate script (or write a small one) to add the key with a
   value or `"---"` to **all twelve files**.
2. Run `verify_element_jsons.py` to confirm consistency.
3. Run `check_field_registry.py` after registering it in `FieldRegistry.kt`.
4. Follow the rest of the checklist in
   [Extending the engine](../ai/extending#make-a-new-element-field-answerable).

## Verifying by hand

For a quick check without the full script:

```bash
python3 -c "
import json, io, glob, os
en = json.load(io.open('app/src/main/assets/elements_en.json', encoding='utf-8'))
for f in sorted(glob.glob('app/src/main/assets/elements_*.json')):
    d = json.load(io.open(f, encoding='utf-8'))
    missing = set(en) - set(d)
    print(os.path.basename(f), len(d), 'elements', ('MISSING: ' + str(missing)) if missing else 'ok')
"
```
