#!/usr/bin/env python3
"""
AI-based translation script for element descriptions.
Translates untranslated element descriptions from English to target languages.
"""
import json
import os
import sys

# Determine repo path
repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
assets_path = os.path.join(repo_path, "app/src/main/assets")

# Language configuration with translation priorities
LANGUAGES = {
    'fr': {'name': 'French', 'file': 'elements_fr.json', 'priority': 1},
    'sv': {'name': 'Swedish', 'file': 'elements_sv.json', 'priority': 2},
    'fil': {'name': 'Filipino', 'file': 'elements_fil.json', 'priority': 3},
    'de': {'name': 'German', 'file': 'elements_de.json', 'priority': 4},
    'es': {'name': 'Spanish', 'file': 'elements_es.json', 'priority': 5},
    'pt': {'name': 'Portuguese', 'file': 'elements_pt.json', 'priority': 6},
    'it': {'name': 'Italian', 'file': 'elements_it.json', 'priority': 7},
    'hi': {'name': 'Hindi', 'file': 'elements_hi.json', 'priority': 8},
    'zh': {'name': 'Chinese', 'file': 'elements_zh.json', 'priority': 9},
    'af': {'name': 'Afrikaans', 'file': 'elements_af.json', 'priority': 10},
}

ENGLISH_INDICATORS = [
    'is a chemical element',
    'was first isolated',
    'was first discovered',
    'It was first',
    'and atomic number',
]

def is_translated(description):
    """Check if description appears to be translated (not English)."""
    if not description:
        return False
    return not any(phrase.lower() in description.lower() for phrase in ENGLISH_INDICATORS)

def get_untranslated_elements(lang_code):
    """Get list of untranslated elements for a language."""
    en_file = os.path.join(assets_path, 'elements_en.json')
    lang_file = os.path.join(assets_path, LANGUAGES[lang_code]['file'])
    
    with open(en_file, 'r', encoding='utf-8') as f:
        en_data = json.load(f)
    
    with open(lang_file, 'r', encoding='utf-8') as f:
        lang_data = json.load(f)
    
    untranslated = []
    for element_key, element_data in lang_data.items():
        desc = element_data.get('description', '')
        if not is_translated(desc):
            en_desc = en_data[element_key]['description']
            untranslated.append({
                'key': element_key,
                'element_name': element_data.get('element', ''),
                'english_description': en_desc
            })
    
    return untranslated

def save_translation(lang_code, element_key, translated_description):
    """Save a translated description to the JSON file."""
    lang_file = os.path.join(assets_path, LANGUAGES[lang_code]['file'])
    
    with open(lang_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    data[element_key]['description'] = translated_description
    
    with open(lang_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 ai_translate_elements.py <language_code>")
        print("\nAvailable languages:")
        for code, info in sorted(LANGUAGES.items(), key=lambda x: x[1]['priority']):
            print(f"  {code:4} - {info['name']}")
        sys.exit(1)
    
    lang_code = sys.argv[1]
    if lang_code not in LANGUAGES:
        print(f"Error: Unknown language code '{lang_code}'")
        sys.exit(1)
    
    print(f"\nChecking untranslated elements for {LANGUAGES[lang_code]['name']}...")
    untranslated = get_untranslated_elements(lang_code)
    
    print(f"Found {len(untranslated)} untranslated elements.")
    
    if untranslated:
        print("\nUntranslated elements:")
        for item in untranslated[:10]:
            print(f"  - {item['key']}: {item['element_name']}")
        if len(untranslated) > 10:
            print(f"  ... and {len(untranslated) - 10} more")

if __name__ == '__main__':
    main()
