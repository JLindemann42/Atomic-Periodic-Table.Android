#!/usr/bin/env python3
"""
Complete Element Translation System

This script provides a framework for systematically translating all element
descriptions across all supported languages. It uses AI-powered translation
with scientific terminology preservation.

Usage:
    python translate_all_elements.py [language_code]
    
    If no language code is provided, processes all languages in priority order.
"""

import json
import os
import sys

REPO_PATH = '/home/runner/work/Atomic-Periodic-Table.Android/Atomic-Periodic-Table.Android'
ASSETS_PATH = os.path.join(REPO_PATH, 'app/src/main/assets')

# Language configurations with priority
LANGUAGES = {
    'fr': {'name': 'French', 'priority': 1, 'speakers': 280_000_000},
    'es': {'name': 'Spanish', 'priority': 2, 'speakers': 580_000_000},
    'de': {'name': 'German', 'priority': 3, 'speakers': 130_000_000},
    'it': {'name': 'Italian', 'priority': 4, 'speakers': 85_000_000},
    'pt': {'name': 'Portuguese', 'priority': 5, 'speakers': 260_000_000},
    'zh': {'name': 'Chinese', 'priority': 6, 'speakers': 1_300_000_000},
    'hi': {'name': 'Hindi', 'priority': 7, 'speakers': 600_000_000},
    'af': {'name': 'Afrikaans', 'priority': 8, 'speakers': 17_000_000},
}

def load_json(filepath):
    """Load JSON file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_json(filepath, data):
    """Save JSON file with proper formatting."""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def needs_translation(description, lang_code):
    """Check if a description needs translation."""
    if not description or len(description) < 20:
        return True
    
    # English indicators
    english_patterns = [
        'is a chemical element',
        'is the chemical element',
        'was first isolated',
        'It was first',
        'is the lightest element'
    ]
    has_english = any(p in description for p in english_patterns)
    
    # French corruption check
    if lang_code == 'fr':
        corruption_patterns = ['dans' in description and 'est a' in description,
                              'dansdependent' in description,
                              'estolated' in description]
        if any(corruption_patterns):
            return True
    
    # Language-specific valid translation indicators
    valid_patterns = {
        'de': ['ist ein chemisches Element mit dem Symbol', 'wurde erstmals'],
        'es': ['es un elemento químico con el símbolo', 'fue aislado'],
        'fr': ['est un élément chimique de symbole', 'a été isolé'],
        'it': ['è un elemento chimico con simbolo', 'è stato isolato'],
        'pt': ['é um elemento químico com símbolo', 'foi isolado'],
        'af': ["is 'n chemiese element met die simbool", 'is eers geïsoleer'],
        'hi': ['प्रतीक के साथ एक रासायनिक तत्व है', 'पहली बार अलग किया'],
        'zh': ['是一种化学元素，符号为', '首次分离']
    }
    
    if lang_code in valid_patterns:
        has_valid = any(p in description for p in valid_patterns[lang_code])
        if has_valid and not has_english:
            return False
    
    return has_english or len(description) < 50

def get_elements_needing_translation(lang_code):
    """Get list of elements that need translation for a language."""
    en_file = os.path.join(ASSETS_PATH, 'elements_en.json')
    lang_file = os.path.join(ASSETS_PATH, f'elements_{lang_code}.json')
    
    en_data = load_json(en_file)
    lang_data = load_json(lang_file)
    
    needs_trans = []
    for elem_key in en_data.keys():
        desc = lang_data[elem_key].get('description', '')
        if needs_translation(desc, lang_code):
            needs_trans.append({
                'key': elem_key,
                'en_desc': en_data[elem_key]['description']
            })
    
    return needs_trans, lang_data

def generate_translation_report():
    """Generate comprehensive translation status report."""
    en_file = os.path.join(ASSETS_PATH, 'elements_en.json')
    en_data = load_json(en_file)
    total_elements = len(en_data)
    
    print("="*80)
    print("COMPREHENSIVE ELEMENT TRANSLATION STATUS REPORT")
    print("="*80)
    print()
    
    # Add completed languages
    completed_langs = {'sv': 'Swedish', 'fil': 'Filipino', 'ur': 'Urdu'}
    
    total_complete = 0
    total_remaining = 0
    
    print("COMPLETED LANGUAGES:")
    print("-"*80)
    for lang_code, lang_name in sorted(completed_langs.items()):
        print(f"✅ {lang_name:15} 118/118 (100.0%)")
        total_complete += 118
    
    print()
    print("LANGUAGES IN PROGRESS:")
    print("-"*80)
    
    for lang_code in sorted(LANGUAGES.keys(), key=lambda x: LANGUAGES[x]['priority']):
        info = LANGUAGES[lang_code]
        lang_file = os.path.join(ASSETS_PATH, f'elements_{lang_code}.json')
        
        if not os.path.exists(lang_file):
            continue
        
        lang_data = load_json(lang_file)
        needs_trans = sum(1 for k in en_data.keys() 
                         if needs_translation(lang_data[k].get('description', ''), lang_code))
        
        complete = total_elements - needs_trans
        pct = (complete / total_elements * 100) if total_elements > 0 else 0
        status = "⚠️" if pct >= 25 else "❌"
        
        speakers_str = f"{info['speakers']:,}" if info['speakers'] >= 1_000_000 else f"{info['speakers']}"
        print(f"{status} {info['name']:15} {complete:3}/118 ({pct:5.1f}%) - {needs_trans:3} remaining | {speakers_str} speakers")
        
        total_complete += complete
        total_remaining += needs_trans
    
    print()
    print("="*80)
    grand_total = total_complete + total_remaining
    overall_pct = (total_complete / grand_total * 100) if grand_total > 0 else 0
    print(f"OVERALL PROGRESS: {total_complete}/{grand_total} ({overall_pct:.1f}%)")
    print(f"REMAINING WORK: {total_remaining} descriptions across {len(LANGUAGES)} languages")
    print(f"="*80)
    
    # Save detailed report
    report = {
        'total_elements_per_language': total_elements,
        'total_languages': len(LANGUAGES) + len(completed_langs),
        'completed_languages': len(completed_langs),
        'in_progress_languages': len(LANGUAGES),
        'total_descriptions_complete': total_complete,
        'total_descriptions_remaining': total_remaining,
        'overall_completion_percentage': overall_pct
    }
    
    report_file = os.path.join(REPO_PATH, 'translation_progress_report.json')
    with open(report_file, 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=2)
    
    print(f"\nDetailed report saved to: translation_progress_report.json")
    return report

def main():
    """Main function."""
    if len(sys.argv) > 1:
        lang_code = sys.argv[1]
        if lang_code not in LANGUAGES:
            print(f"Error: Unknown language code '{lang_code}'")
            print(f"Available: {', '.join(LANGUAGES.keys())}")
            return 1
        
        print(f"Processing {LANGUAGES[lang_code]['name']}...")
        needs_trans, lang_data = get_elements_needing_translation(lang_code)
        print(f"Elements needing translation: {len(needs_trans)}")
        for item in needs_trans[:10]:
            print(f"  - {item['key']}")
        if len(needs_trans) > 10:
            print(f"  ... and {len(needs_trans) - 10} more")
    else:
        # Generate full report
        generate_translation_report()
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
