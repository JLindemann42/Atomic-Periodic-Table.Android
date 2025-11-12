# Translation Guide for Atomic - Periodic Table

## Overview

This guide explains what translations are needed and how to complete them. The app currently has incomplete translations with English strings that need to be translated to the app's supported languages.

## Current Status

### 1. String Resources - PARTIALLY COMPLETE ⚠️

The following translation files in `app/src/main/res/` have been updated with 100 new strings, but **these strings are currently in English and need native speaker translation**:

- `values-af/strings.xml` (Afrikaans) - 100 English strings need translation
- `values-b+fil/strings.xml` (Filipino) - 100 English strings need translation  
- `values-de/strings.xml` (German) - 100 English strings need translation
- `values-es-rAR/strings.xml` (Spanish - Argentina) - 100 English strings need translation
- `values-es-rES/strings.xml` (Spanish - Spain) - 100 English strings need translation
- `values-es-rMX/strings.xml` (Spanish - Mexico) - 100 English strings need translation
- `values-fr/strings.xml` (French) - 100 English strings need translation
- `values-hi/strings.xml` (Hindi) - 100 English strings need translation
- `values-it-rIT/strings.xml` (Italian) - 100 English strings need translation
- `values-pt-rBR/strings.xml` (Portuguese - Brazil) - 100 English strings need translation
- `values-sv-rSE/strings.xml` (Swedish) - 100 English strings need translation
- `values-ur-rIN/strings.xml` (Urdu - India) - 100 English strings need translation
- `values-ur-rPK/strings.xml` (Urdu - Pakistan) - 100 English strings need translation
- `values-zh-rCN/strings.xml` (Chinese Simplified) - 100 English strings need translation

**The new strings that were added are at the bottom of each file** and include:
- `achievement_reached_prefix`
- `additional_data_item`
- `all_core_features`
- `appearance_colon`
- `atomic_mass_colon`
- `boiling_point_colon`
- `calculations`
- `correct`/`wrong`
- `game_results`
- `get_pro_button`/`get_pro_plus_button`
- `go_pro_school_start_sale`
- `lives_label`/`lives_unlimited`
- `pro_version`/`pro_plus_version`
- `rate_app`/`rate_app_description`
- And 75+ more...

### 2. Element Descriptions - NOT STARTED ❌

All element JSON files in `app/src/main/assets/` still contain English descriptions that need translation:

- `elements_af.json` (Afrikaans) - 118 element descriptions
- `elements_de.json` (German) - 118 element descriptions
- `elements_es.json` (Spanish) - 118 element descriptions
- `elements_fil.json` (Filipino) - 118 element descriptions
- `elements_fr.json` (French) - 118 element descriptions
- `elements_hi.json` (Hindi) - 118 element descriptions
- `elements_it.json` (Italian) - 118 element descriptions
- `elements_pt.json` (Portuguese) - 118 element descriptions
- `elements_sv.json` (Swedish) - 118 element descriptions
- `elements_ur.json` (Urdu) - 118 element descriptions
- `elements_zh.json` (Chinese) - 118 element descriptions

Each element JSON has a `"description"` field that needs to be translated from English to the target language.

## How to Complete the Translations

### For String Resources

1. Open the appropriate `strings.xml` file for your language
2. Find the newly added strings at the bottom of the file (after line ~430)
3. Replace the English text with the proper translation
4. Preserve XML entities and format specifiers like:
   - `%s`, `%d`, `%1$d`, `%2$s` - these are placeholders for dynamic values
   - `\'` - escaped apostrophes
   - `\n` - newlines
   - Special characters like `∞` should be preserved

Example:
```xml
<!-- Before (English placeholder) -->
<string name="lives_unlimited">Lives: ∞</string>

<!-- After (German translation) -->
<string name="lives_unlimited">Leben: ∞</string>
```

### For Element Descriptions

1. Open the appropriate `elements_XX.json` file for your language
2. For each element, translate the `"description"` field
3. Keep all other fields unchanged (element names, symbols, numbers, etc.)
4. Preserve JSON structure and formatting

Example:
```json
{
  "hydrogen": {
    "description": "Hydrogen is a chemical element with the symbol H and atomic number 1...",
    ...
  }
}
```

Should become (for German):
```json
{
  "hydrogen": {
    "description": "Wasserstoff ist ein chemisches Element mit dem Symbol H und der Ordnungszahl 1...",
    ...
  }
}
```

## Translation Workflow

### Recommended Approach

1. **Use Professional Translation Services**: For best quality, use professional translators familiar with scientific terminology
2. **Community Contribution**: Open issues on GitHub asking for help from native speakers
3. **Translation Tools**: Use tools like:
   - POEditor, Crowdin, or Weblate for collaborative translation
   - Google Translate or DeepL as a starting point (but review carefully!)
   - Wikipedia translations of element names for reference

### Quality Checklist

- [ ] All new strings in all `strings.xml` files are translated
- [ ] Format specifiers (`%s`, `%d`, etc.) are preserved exactly
- [ ] XML entities are properly escaped
- [ ] Element descriptions are scientifically accurate
- [ ] Translations sound natural to native speakers
- [ ] Special symbols and units are preserved

## Utility Scripts

Scripts are available in the `scripts/` directory to help with translation:

- `check_translations.py` - Verify which strings are still in English
- `extract_missing.py` - Extract all untranslated strings to a CSV for bulk translation
- `apply_translations.py` - Apply bulk translations from CSV back to XML/JSON

## Total Translation Scope

- **String Resources**: 1,400 translations (100 strings × 14 language files)
- **Element Descriptions**: 1,298 translations (118 elements × 11 language files)
- **Total**: 2,698 text segments requiring translation

## Notes

- The English reference files are:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/assets/elements_en.json`
- Translations should match the style and tone of existing translations in each language
- For technical/chemical terms, consult scientific dictionaries in the target language
- If unsure about a translation, it's better to leave a note than guess

## Getting Help

- Open an issue on GitHub for translation questions
- Tag issues with the `translation` label
- Include the language code and specific strings you need help with

## Credits

Translations contributed by:
- Giancarlo Mérida - Spanish and Italian
- ElectroBoy10 - Afrikaans
- (Add your name here when contributing!)
