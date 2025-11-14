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
