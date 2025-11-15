#!/usr/bin/env python3
"""
Batch translate element descriptions using AI assistance.
This script processes elements systematically for each target language.
"""

import json
import os
import sys

def load_json(filepath):
    """Load JSON file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_json(filepath, data):
    """Save JSON file with proper formatting."""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def get_translation_prompt(element_name, description, target_lang):
    """Generate translation prompt for an element."""
    lang_names = {
        'de': 'German',
        'es': 'Spanish',
        'fr': 'French',
        'it': 'Italian',
        'pt': 'Brazilian Portuguese',
        'sv': 'Swedish',
        'fil': 'Filipino (Tagalog)',
        'af': 'Afrikaans',
        'hi': 'Hindi (Devanagari script)',
        'zh': 'Simplified Chinese'
    }
    
    lang_name = lang_names.get(target_lang, target_lang)
    
    return f"""Translate this chemical element description to {lang_name}.
Maintain scientific accuracy and use proper chemical terminology.
Keep the same factual information and structure.

Element: {element_name}
English Description:
{description}

Provide only the {lang_name} translation:"""

def needs_translation(description, lang_code):
    """Check if description needs translation."""
    if not description or len(description) < 20:
        return True
    
    english_indicators = [
        'is a chemical element',
        'is the chemical element', 
        'was first isolated',
        'is the lightest element'
    ]
    
    has_english = any(ind in description for ind in english_indicators)
    
    # Check for target language indicators
    lang_indicators = {
        'de': ['ist ein chemisches Element', 'wurde erstmals'],
        'es': ['es un elemento químico', 'fue aislado'],
        'fr': ['est un élément chimique'],
        'it': ['è un elemento chimico'],
        'pt': ['é um elemento químico'],
        'sv': ['är ett kemiskt grundämne'],
        'fil': ['ay isang elementong kemikal'],
        'af': ["is 'n chemiese element"],
        'hi': ['एक रासायनिक तत्व है'],
        'zh': ['是一种化学元素', '是化学元素']
    }
    
    if lang_code in lang_indicators:
        has_target = any(ind in description for ind in lang_indicators[lang_code])
        if has_target and not has_english:
            return False
    
    return has_english


def main():
    """Main function to manage translation batches."""
    repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    assets_path = os.path.join(repo_path, 'app/src/main/assets')
    
    # Load English reference
    en_file = os.path.join(assets_path, 'elements_en.json')
    en_data = load_json(en_file)
    
    print("="*80)
    print("BATCH ELEMENT TRANSLATION TOOL")
    print("="*80)
    print("\nThis script helps translate element descriptions systematically.")
    print("Given the large scope (681 descriptions), translations will be")
    print("processed in batches for each language.\n")
    
    # Show translation needs
    languages = ['de', 'es', 'fr', 'it', 'pt', 'sv', 'fil', 'af', 'hi', 'zh']
    
    for lang in languages:
        lang_file = os.path.join(assets_path, f'elements_{lang}.json')
        if not os.path.exists(lang_file):
            continue
        
        lang_data = load_json(lang_file)
        needs_trans = sum(1 for k, v in lang_data.items() 
                         if needs_translation(v.get('description', ''), lang))
        
        print(f"{lang}: {needs_trans}/118 elements need translation")
    
    print("\n" + "="*80)
    print("To translate elements, the script exports prompts that can be")
    print("processed with AI translation services or tools.")
    print("="*80)

if __name__ == '__main__':
    main()
