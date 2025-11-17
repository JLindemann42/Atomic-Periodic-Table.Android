#!/usr/bin/env python3
"""
Automatically translate all untranslated strings in the project using Google Translate.
This script handles both string resources (XML) and element descriptions (JSON).
"""
import os
import sys
import json
import time
import xml.etree.ElementTree as ET
from deep_translator import GoogleTranslator

# Language code mappings
LANGUAGE_MAPPINGS = {
    'values-af': 'af',           # Afrikaans
    'values-b+fil': 'tl',        # Filipino (Tagalog)
    'values-de': 'de',           # German
    'values-es-rAR': 'es',       # Spanish (all variants)
    'values-es-rES': 'es',
    'values-es-rMX': 'es',
    'values-fr': 'fr',           # French
    'values-hi': 'hi',           # Hindi
    'values-it-rIT': 'it',       # Italian
    'values-pt-rBR': 'pt',       # Portuguese
    'values-sv-rSE': 'sv',       # Swedish
    'values-ur-rIN': 'ur',       # Urdu
    'values-ur-rPK': 'ur',
    'values-zh-rCN': 'zh-CN',    # Chinese Simplified
}

ELEMENT_LANGUAGE_MAPPINGS = {
    'elements_af.json': 'af',
    'elements_de.json': 'de',
    'elements_es.json': 'es',
    'elements_fil.json': 'tl',
    'elements_fr.json': 'fr',
    'elements_hi.json': 'hi',
    'elements_it.json': 'it',
    'elements_pt.json': 'pt',
    'elements_sv.json': 'sv',
    'elements_ur.json': 'ur',
    'elements_zh.json': 'zh-CN',
}

# Strings that should not be translated
SKIP_STRINGS = [
    'bluesky', 'instagram', 'facebook', 'homepage', 'about_author_name',
    'mackenzie_l_davis', 'water_and_wastewater_engineering',
    'sothree_android_sliding_up_panel', 'wikipedia_commons',
    'credits_giancarlo', 'credits_electro_boy', 'app_name',
    'web_client_id', 'pro_price_discount', 'blog'
]

def translate_text(text, target_lang, max_retries=3):
    """Translate text to target language with retry logic."""
    if not text or not text.strip():
        return text
    
    for attempt in range(max_retries):
        try:
            translator = GoogleTranslator(source='en', target=target_lang)
            result = translator.translate(text)
            time.sleep(0.5)  # Rate limiting
            return result
        except Exception as e:
            print(f"    Translation error (attempt {attempt + 1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                time.sleep(2)  # Wait before retry
            else:
                return text  # Return original if all retries fail
    
    return text

def translate_string_resources(repo_path):
    """Translate XML string resources for all languages."""
    print("\n" + "=" * 80)
    print("TRANSLATING STRING RESOURCES")
    print("=" * 80)
    
    res_path = os.path.join(repo_path, "app/src/main/res")
    
    # Read English strings as reference
    en_strings_path = os.path.join(res_path, "values/strings.xml")
    tree = ET.parse(en_strings_path)
    root = tree.getroot()
    en_strings = {}
    for string_elem in root.findall('string'):
        name = string_elem.get('name')
        translatable = string_elem.get('translatable')
        if translatable != 'false':
            en_strings[name] = string_elem.text if string_elem.text else ''
    
    # Process each language
    for values_dir, lang_code in LANGUAGE_MAPPINGS.items():
        strings_file = os.path.join(res_path, values_dir, "strings.xml")
        if not os.path.exists(strings_file):
            print(f"\n{values_dir}: File not found, skipping")
            continue
        
        print(f"\n{values_dir} ({lang_code}):")
        
        # Parse existing translations
        tree = ET.parse(strings_file)
        root = tree.getroot()
        
        trans_count = 0
        skip_count = 0
        
        # Find and translate strings
        for string_elem in root.findall('string'):
            name = string_elem.get('name')
            current_text = string_elem.text if string_elem.text else ''
            
            # Skip if already translated or should not be translated
            if name not in en_strings:
                continue
            
            if name in SKIP_STRINGS:
                skip_count += 1
                continue
            
            # Check if needs translation (matches English exactly)
            if current_text == en_strings[name]:
                en_text = en_strings[name]
                print(f"  Translating: {name}")
                
                # Translate
                translated = translate_text(en_text, lang_code)
                string_elem.text = translated
                trans_count += 1
        
        # Save updated file
        tree.write(strings_file, encoding='utf-8', xml_declaration=True)
        print(f"  ✓ Translated {trans_count} strings (skipped {skip_count})")

def translate_element_descriptions(repo_path):
    """Translate element descriptions in JSON files."""
    print("\n" + "=" * 80)
    print("TRANSLATING ELEMENT DESCRIPTIONS")
    print("=" * 80)
    
    assets_path = os.path.join(repo_path, "app/src/main/assets")
    
    # Read English elements as reference
    en_file = os.path.join(assets_path, "elements_en.json")
    with open(en_file, 'r', encoding='utf-8') as f:
        en_elements = json.load(f)
    
    # Process each language file
    for json_file, lang_code in ELEMENT_LANGUAGE_MAPPINGS.items():
        file_path = os.path.join(assets_path, json_file)
        if not os.path.exists(file_path):
            print(f"\n{json_file}: File not found, skipping")
            continue
        
        print(f"\n{json_file} ({lang_code}):")
        
        # Load current translations
        with open(file_path, 'r', encoding='utf-8') as f:
            elements = json.load(f)
        
        trans_count = 0
        
        # Translate each element description
        for element_key in elements:
            if element_key not in en_elements:
                continue
            
            en_desc = en_elements[element_key].get('description', '')
            current_desc = elements[element_key].get('description', '')
            
            # Check if needs translation (is in English)
            # Simple heuristic: if description contains common English words
            if current_desc and ('is a chemical element' in current_desc or 
                                 'was first' in current_desc or
                                 'atomic number' in current_desc):
                print(f"  Translating: {element_key}")
                
                # Translate description
                translated_desc = translate_text(en_desc, lang_code)
                elements[element_key]['description'] = translated_desc
                trans_count += 1
        
        # Save updated file
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(elements, f, ensure_ascii=False, indent=2)
        
        print(f"  ✓ Translated {trans_count} element descriptions")

def main():
    """Main translation function."""
    if len(sys.argv) > 1:
        repo_path = sys.argv[1]
    else:
        repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    print("Starting automatic translation...")
    print(f"Repository: {repo_path}")
    print("\nNote: This uses Google Translate. Results may need manual review.")
    print("Press Ctrl+C to cancel\n")
    
    try:
        # Translate string resources
        translate_string_resources(repo_path)
        
        # Translate element descriptions
        translate_element_descriptions(repo_path)
        
        print("\n" + "=" * 80)
        print("TRANSLATION COMPLETE!")
        print("=" * 80)
        print("\nPlease review the translations and make manual corrections as needed.")
        print("Run check_translations.py to verify the results.")
        
    except KeyboardInterrupt:
        print("\n\nTranslation cancelled by user.")
        sys.exit(1)
    except Exception as e:
        print(f"\n\nError during translation: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
