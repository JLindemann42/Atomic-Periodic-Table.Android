# Translation Helper Scripts

This directory contains utility scripts to help manage translations for the Atomic - Periodic Table app.

## Scripts

### check_translations.py
Checks the status of all translation files and reports:
- Total number of strings per language
- Missing strings
- Strings that appear untranslated (match English exactly)

**Usage:**
```bash
python3 scripts/check_translations.py
```

### check_element_translations.py
Checks element JSON files to see if descriptions have been translated from English.

**Usage:**
```bash
python3 scripts/check_element_translations.py
```

### verify_element_jsons.py
**NEW** - Comprehensive verification script for element JSON translations. This is the recommended script for thorough verification.

Performs complete validation including:
- JSON structural validity
- Translation completeness with detailed statistics
- Data integrity checks
- Element count verification
- Field presence validation

**Usage:**
```bash
# Basic verification
python3 scripts/verify_element_jsons.py

# Detailed mode (shows untranslated element names)
python3 scripts/verify_element_jsons.py --detailed

# JSON output (for automation)
python3 scripts/verify_element_jsons.py --json-output
```

### extract_missing.py
Extracts all untranslated strings to a CSV file that can be:
- Imported into Google Sheets for collaborative translation
- Sent to professional translators
- Used with translation management tools like Crowdin, POEditor, etc.

**Usage:**
```bash
python3 scripts/extract_missing.py
```

This creates `untranslated_strings.csv` in the repository root.

## Workflow for Translations

1. **Check current status:**
   ```bash
   python3 scripts/check_translations.py
   python3 scripts/verify_element_jsons.py
   ```

2. **Extract untranslated strings:**
   ```bash
   python3 scripts/extract_missing.py
   ```

3. **Translate the strings:**
   - Use professional translation services
   - Import CSV into Google Sheets and share with native speakers
   - Use translation management platforms

4. **Apply translations manually:**
   - Edit the `strings.xml` files directly for each language
   - Edit the `elements_XX.json` files for element descriptions

5. **Verify translations:**
   - Run the check scripts again to ensure all strings are translated
   - Test the app in each language to verify correctness

## Requirements

- Python 3.6 or higher
- No external dependencies (uses only standard library)

## Notes

- Scripts automatically detect the repository root
- All scripts can be run from any directory
- Output is color-coded for easy reading (when terminal supports it)

## Element data scripts

`populate_element_data.py` fills `"---"` placeholders in the element JSON files
from IUPAC, NIST, CRC Handbook and WebElements values. `check_field_registry.py`
cross-checks the element JSON against the AI engine's `FieldRegistry`.
`fix_string_escaping.py` repairs escaping damage in `strings.xml` after a bulk
translation pass.

## Historical batch scripts

Most of the remaining files here are one-off passes from a 2025 translation
campaign (`push_toward_60.py`, `mega_batch_update.py`, `continue_all_languages.py`
and similar). They are kept because they encode the translations that were
actually applied, but they overlap heavily and are not a maintained toolkit. For
new work, use `extract_missing.py` to export, translate externally, apply, and
verify.

## Full documentation

See [docs/data-pipeline](../docs/data-pipeline) for the complete guide.

> **Removed:** an earlier version of this README documented an "on-device RAG
> asset generation" pipeline (`prepare_corpus.py`, `build_embeddings.py`,
> `requirements.txt`, `convert_to_tflite.md`). Those scripts were never present
> in this repository, and the embedding approach they described was abandoned —
> the AI agent uses BM25 lexical retrieval built at runtime, with no shipped
> model or embedding artifacts. See
> [docs/ai/retrieval.md](../docs/ai/retrieval.md).
