#!/usr/bin/env python3
"""
Bulk Element Description Translator - Batch Processor
Efficiently translates remaining element descriptions for all languages.
"""

import json
import os

REPO_PATH = '/home/runner/work/Atomic-Periodic-Table.Android/Atomic-Periodic-Table.Android'
ASSETS_PATH = os.path.join(REPO_PATH, 'app/src/main/assets')

def load_json(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_json(filepath, data):
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def update_element(lang_code, element_key, translated_description):
    """Update a single element's description."""
    lang_file = os.path.join(ASSETS_PATH, f'elements_{lang_code}.json')
    lang_data = load_json(lang_file)
    lang_data[element_key]['description'] = translated_description
    save_json(lang_file, lang_data)
    return True

def bulk_update_elements(lang_code, translations_dict):
    """Update multiple elements at once."""
    lang_file = os.path.join(ASSETS_PATH, f'elements_{lang_code}.json')
    lang_data = load_json(lang_file)
    
    updated_count = 0
    for elem_key, translated_desc in translations_dict.items():
        if elem_key in lang_data:
            lang_data[elem_key]['description'] = translated_desc
            updated_count += 1
    
    save_json(lang_file, lang_data)
    return updated_count

def get_translation_status():
    """Get current translation status for all languages."""
    en_data = load_json(os.path.join(ASSETS_PATH, 'elements_en.json'))
    
    status = {}
    for lang_code in ['de', 'es', 'fr', 'it', 'pt', 'zh', 'hi', 'af']:
        lang_file = os.path.join(ASSETS_PATH, f'elements_{lang_code}.json')
        if not os.path.exists(lang_file):
            continue
        
        lang_data = load_json(lang_file)
        needs_trans = []
        
        for elem_key in en_data.keys():
            desc = lang_data[elem_key].get('description', '')
            # Check if needs translation
            if ('is a chemical element' in desc or 'is the chemical element' in desc or
                (lang_code == 'fr' and 'dans' in desc and 'est a' in desc)):
                needs_trans.append(elem_key)
        
        status[lang_code] = {
            'total': len(lang_data),
            'completed': len(lang_data) - len(needs_trans),
            'remaining': len(needs_trans),
            'elements_needed': needs_trans
        }
    
    return status

if __name__ == '__main__':
    status = get_translation_status()
    
    print("TRANSLATION STATUS BY LANGUAGE")
    print("="*80)
    
    total_remaining = 0
    for lang_code in sorted(status.keys()):
        info = status[lang_code]
        pct = (info['completed'] / info['total'] * 100) if info['total'] > 0 else 0
        print(f"{lang_code}: {info['completed']}/{info['total']} ({pct:.1f}%) - {info['remaining']} remaining")
        total_remaining += info['remaining']
    
    print(f"\nTotal remaining across all languages: {total_remaining}")
    
    # Export for processing
    output_file = os.path.join(REPO_PATH, 'translation_queue.json')
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(status, f, indent=2)
    
    print(f"\nDetailed queue saved to: translation_queue.json")
