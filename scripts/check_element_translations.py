#!/usr/bin/env python3
"""
Check element JSON files for untranslated descriptions.
"""
import os
import sys
import json

# Determine repo path
if len(sys.argv) > 1:
    repo_path = sys.argv[1]
else:
    repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

assets_path = os.path.join(repo_path, "app/src/main/assets")

# Element file language mapping
ELEMENT_FILES = {
    'elements_af.json': 'Afrikaans',
    'elements_de.json': 'German',
    'elements_es.json': 'Spanish',
    'elements_fil.json': 'Filipino',
    'elements_fr.json': 'French',
    'elements_hi.json': 'Hindi',
    'elements_it.json': 'Italian',
    'elements_pt.json': 'Portuguese',
    'elements_sv.json': 'Swedish',
    'elements_ur.json': 'Urdu',
    'elements_zh.json': 'Chinese',
}

print("=" * 80)
print("ELEMENT DESCRIPTION TRANSLATION CHECK")
print("=" * 80)

for elem_file, lang_name in ELEMENT_FILES.items():
    file_path = os.path.join(assets_path, elem_file)
    
    if not os.path.exists(file_path):
        print(f"\n{elem_file} ({lang_name}): FILE NOT FOUND")
        continue
    
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # Check first few elements for English patterns
    english_count = 0
    translated_count = 0
    
    for element_key, element_data in list(data.items())[:10]:  # Check first 10
        if 'description' in element_data:
            desc = element_data['description']
            # Check for English indicators
            if any(phrase in desc for phrase in ['is a chemical element', 'was first', 
                                                  'The element', 'is the', 'It is']):
                english_count += 1
            else:
                translated_count += 1
    
    total_elements = len(data)
    
    print(f"\n{elem_file} ({lang_name}):")
    print(f"  Total elements: {total_elements}")
    if english_count > translated_count:
        print(f"  Status: ❌ NEEDS TRANSLATION (detected English in descriptions)")
        print(f"  All {total_elements} element descriptions need to be translated")
    else:
        print(f"  Status: ✓ Appears to be translated")
        print(f"  Sample check: {translated_count}/10 descriptions appear translated")

print("\n" + "=" * 80)
print("Check complete!")
print("=" * 80)
print("\nNote: This is a heuristic check. Manual review is recommended.")
