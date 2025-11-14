#!/usr/bin/env python3
"""
Translate element descriptions in JSON files using AI translation.
This script translates element descriptions from English to target languages.
"""

import json
import os
import sys
import re
from typing import Dict, List, Tuple

# Language configurations
LANGUAGES = {
    'de': {
        'name': 'German',
        'file': 'elements_de.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to German. Maintain scientific accuracy and use proper German chemical terminology:\n\n{text}\n\nProvide only the German translation, no explanations.'
        }
    },
    'es': {
        'name': 'Spanish',
        'file': 'elements_es.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Spanish. Maintain scientific accuracy and use proper Spanish chemical terminology:\n\n{text}\n\nProvide only the Spanish translation, no explanations.'
        }
    },
    'fr': {
        'name': 'French',
        'file': 'elements_fr.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to French. Maintain scientific accuracy and use proper French chemical terminology:\n\n{text}\n\nProvide only the French translation, no explanations.'
        }
    },
    'it': {
        'name': 'Italian',
        'file': 'elements_it.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Italian. Maintain scientific accuracy and use proper Italian chemical terminology:\n\n{text}\n\nProvide only the Italian translation, no explanations.'
        }
    },
    'pt': {
        'name': 'Portuguese',
        'file': 'elements_pt.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Brazilian Portuguese. Maintain scientific accuracy and use proper Portuguese chemical terminology:\n\n{text}\n\nProvide only the Portuguese translation, no explanations.'
        }
    },
    'sv': {
        'name': 'Swedish',
        'file': 'elements_sv.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Swedish. Maintain scientific accuracy and use proper Swedish chemical terminology:\n\n{text}\n\nProvide only the Swedish translation, no explanations.'
        }
    },
    'fil': {
        'name': 'Filipino',
        'file': 'elements_fil.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Filipino (Tagalog). Maintain scientific accuracy and use proper Filipino chemical terminology:\n\n{text}\n\nProvide only the Filipino translation, no explanations.'
        }
    },
    'af': {
        'name': 'Afrikaans',
        'file': 'elements_af.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Afrikaans. Maintain scientific accuracy and use proper Afrikaans chemical terminology:\n\n{text}\n\nProvide only the Afrikaans translation, no explanations.'
        }
    },
    'hi': {
        'name': 'Hindi',
        'file': 'elements_hi.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Hindi. Maintain scientific accuracy and use proper Hindi chemical terminology. Write in Devanagari script:\n\n{text}\n\nProvide only the Hindi translation, no explanations.'
        }
    },
    'zh': {
        'name': 'Chinese (Simplified)',
        'file': 'elements_zh.json',
        'prompts': {
            'system': 'You are a professional scientific translator specializing in chemistry. Translate accurately while preserving scientific terminology.',
            'user_template': 'Translate this chemical element description from English to Simplified Chinese. Maintain scientific accuracy and use proper Chinese chemical terminology:\n\n{text}\n\nProvide only the Simplified Chinese translation, no explanations.'
        }
    }
}


def needs_translation(description: str, lang_code: str) -> bool:
    """Check if a description needs translation based on language patterns."""
    if not description or len(description) < 20:
        return True
    
    # Check for English patterns
    english_patterns = [
        'is a chemical element with',
        'is the chemical element with',
        'was first isolated',
        'It was first',
        'is the lightest element',
        'is the most abundant'
    ]
    
    has_english = any(pattern in description for pattern in english_patterns)
    
    # Language-specific patterns to detect existing translations
    lang_patterns = {
        'de': ['ist ein chemisches Element', 'wurde erstmals'],
        'es': ['es un elemento químico', 'fue aislado'],
        'fr': ['est un élément chimique', 'a été isolé'],
        'it': ['è un elemento chimico', 'è stato isolato'],
        'pt': ['é um elemento químico', 'foi isolado'],
        'sv': ['är ett kemiskt grundämne', 'är ett grundämne'],
        'fil': ['ay isang elementong kemikal', 'ay isang elemento'],
        'af': ["is 'n chemiese element", "is 'n element"],
        'hi': ['एक रासायनिक तत्व है', 'है जो'],
        'zh': ['是一种化学元素', '是化学元素', '是一個']
    }
    
    if lang_code in lang_patterns:
        has_target_lang = any(pattern in description for pattern in lang_patterns[lang_code])
        # If it has target language patterns and no English, it's already translated
        if has_target_lang and not has_english:
            return False
    
    return has_english or len(description) < 50


def get_repo_path():
    """Get the repository path."""
    if len(sys.argv) > 1:
        return sys.argv[1]
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load_json_file(filepath: str) -> Dict:
    """Load a JSON file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_json_file(filepath: str, data: Dict):
    """Save data to JSON file with proper formatting."""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def analyze_translation_needs(repo_path: str) -> Dict:
    """Analyze which files need translation and how many elements."""
    assets_path = os.path.join(repo_path, 'app/src/main/assets')
    en_file = os.path.join(assets_path, 'elements_en.json')
    
    en_data = load_json_file(en_file)
    results = {}
    
    for lang_code, config in LANGUAGES.items():
        target_file = os.path.join(assets_path, config['file'])
        if not os.path.exists(target_file):
            print(f"Warning: {config['file']} not found")
            continue
        
        target_data = load_json_file(target_file)
        
        needs_trans = []
        for element_key, element_data in target_data.items():
            desc = element_data.get('description', '')
            if needs_translation(desc, lang_code):
                needs_trans.append(element_key)
        
        results[lang_code] = {
            'name': config['name'],
            'file': config['file'],
            'total': len(target_data),
            'needs_translation': len(needs_trans),
            'elements': needs_trans
        }
    
    return results


def main():
    """Main function."""
    repo_path = get_repo_path()
    
    print("="*80)
    print("ELEMENT DESCRIPTION TRANSLATION ANALYSIS")
    print("="*80)
    
    results = analyze_translation_needs(repo_path)
    
    total_needed = 0
    for lang_code, info in sorted(results.items(), key=lambda x: x[1]['needs_translation'], reverse=True):
        pct = (info['needs_translation'] / info['total'] * 100) if info['total'] > 0 else 0
        status = "✅" if info['needs_translation'] == 0 else "⚠️" if pct < 50 else "❌"
        
        print(f"\n{status} {info['name']} ({info['file']}):")
        print(f"   Needs translation: {info['needs_translation']}/{info['total']} ({pct:.1f}%)")
        
        total_needed += info['needs_translation']
    
    print(f"\n{'='*80}")
    print(f"TOTAL DESCRIPTIONS NEEDING TRANSLATION: {total_needed}")
    print(f"{'='*80}")
    
    # Save detailed report
    report_file = os.path.join(repo_path, 'translation_needs_report.json')
    with open(report_file, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    
    print(f"\nDetailed report saved to: {report_file}")
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
