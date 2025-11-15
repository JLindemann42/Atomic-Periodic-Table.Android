#!/usr/bin/env python3
"""
Comprehensive element description translator.
This script coordinates the translation of all element descriptions
across all supported languages.
"""

import json
import os
import sys

# Translation mappings - will be populated by actual translations
TRANSLATIONS = {}

def load_json(filepath):
    """Load JSON file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_json(filepath, data):
    """Save JSON with proper formatting."""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def needs_translation(description, lang_code):
    """Check if a description needs translation."""
    if not description or len(description) < 20:
        return True
    
    # English indicators
    english_patterns = ['is a chemical element', 'is the chemical element', 
                       'was first isolated', 'It was first']
    has_english = any(p in description for p in english_patterns)
    
    # Corruption indicator for French
    if lang_code == 'fr':
        if 'dans' in description and has_english:
            return True
        if 'dansdepend' in description or 'estolated' in description:
            return True
    
    # Language-specific good translation indicators
    good_patterns = {
        'de': ['ist ein chemisches Element mit dem Symbol', 'wurde erstmals'],
        'es': ['es un elemento químico con el símbolo', 'fue aislado'],
        'fr': ['est un élément chimique de symbole', 'a été isolé'],
        'it': ['è un elemento chimico con simbolo', 'è stato isolato'],
        'pt': ['é um elemento químico com símbolo', 'foi isolado'],
        'sv': ['är ett kemiskt grundämne med symbolen', 'isolerades först'],
        'fil': ['ay isang elementong kemikal na may simbolo', 'unang inihiwalay'],
        'af': ["is 'n chemiese element met die simbool", 'is eers geïsoleer'],
        'hi': ['प्रतीक के साथ एक रासायनिक तत्व है', 'पहली बार अलग किया'],
        'zh': ['是一种化学元素，符号为', '首次分离']
    }
    
    if lang_code in good_patterns:
        has_good_trans = any(p in description for p in good_patterns[lang_code])
        if has_good_trans and not has_english:
            return False
    
    return has_english or len(description) < 50

def analyze_all_languages(assets_path):
    """Analyze translation needs for all languages."""
    en_data = load_json(os.path.join(assets_path, 'elements_en.json'))
    
    languages = {
        'de': 'German',
        'es': 'Spanish', 
        'fr': 'French',
        'it': 'Italian',
        'pt': 'Portuguese',
        'sv': 'Swedish',
        'fil': 'Filipino',
        'af': 'Afrikaans',
        'hi': 'Hindi',
        'zh': 'Chinese'
    }
    
    results = {}
    total_needed = 0
    
    for lang_code, lang_name in languages.items():
        lang_file = os.path.join(assets_path, f'elements_{lang_code}.json')
        if not os.path.exists(lang_file):
            continue
        
        lang_data = load_json(lang_file)
        needs_trans = []
        
        for elem_key in en_data.keys():
            desc = lang_data[elem_key].get('description', '')
            if needs_translation(desc, lang_code):
                needs_trans.append(elem_key)
        
        results[lang_code] = {
            'name': lang_name,
            'total': len(lang_data),
            'needs': len(needs_trans),
            'elements': needs_trans
        }
        total_needed += len(needs_trans)
    
    return results, total_needed

def main():
    """Main function."""
    repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    assets_path = os.path.join(repo_path, 'app/src/main/assets')
    
    print("="*80)
    print("COMPREHENSIVE ELEMENT DESCRIPTION TRANSLATION")
    print("="*80)
    
    results, total = analyze_all_languages(assets_path)
    
    print(f"\nAnalysis Results:")
    print("-"*80)
    
    for lang_code in sorted(results.keys(), key=lambda x: results[x]['needs'], reverse=True):
        info = results[lang_code]
        pct = (info['needs'] / info['total'] * 100) if info['total'] > 0 else 0
        status = "✅" if info['needs'] == 0 else "⚠️" if pct < 20 else "❌"
        print(f"{status} {info['name']:15} {info['needs']:3}/{info['total']:3} elements ({pct:5.1f}%)")
    
    print(f"\n{'='*80}")
    print(f"TOTAL DESCRIPTIONS NEEDING TRANSLATION: {total}")
    print(f"{'='*80}")
    
    # Save detailed report
    report = {
        'total_needed': total,
        'by_language': results
    }
    
    report_file = os.path.join(repo_path, 'translation_status.json')
    with open(report_file, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    
    print(f"\nDetailed status saved to: translation_status.json")
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
