#!/usr/bin/env python3
"""
Extract untranslated strings to CSV for bulk translation.
Output can be imported into translation tools or Google Sheets.
"""
import os
import sys
import csv
import xml.etree.ElementTree as ET

# Determine repo path
if len(sys.argv) > 1:
    repo_path = sys.argv[1]
else:
    repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

res_path = os.path.join(repo_path, "app/src/main/res")
output_file = os.path.join(repo_path, "untranslated_strings.csv")

# Read English strings
en_strings_path = os.path.join(res_path, "values/strings.xml")
tree = ET.parse(en_strings_path)
root = tree.getroot()
en_strings = {}
for string_elem in root.findall('string'):
    name = string_elem.get('name')
    translatable = string_elem.get('translatable')
    if translatable != 'false':
        en_strings[name] = string_elem.text if string_elem.text else ''

# Language mappings
LANGUAGES = {
    'values-af': 'Afrikaans',
    'values-b+fil': 'Filipino', 
    'values-de': 'German',
    'values-es-rAR': 'Spanish (Argentina)',
    'values-es-rES': 'Spanish (Spain)',
    'values-es-rMX': 'Spanish (Mexico)',
    'values-fr': 'French',
    'values-hi': 'Hindi',
    'values-it-rIT': 'Italian',
    'values-pt-rBR': 'Portuguese (Brazil)',
    'values-sv-rSE': 'Swedish',
    'values-ur-rIN': 'Urdu (India)',
    'values-ur-rPK': 'Urdu (Pakistan)',
    'values-zh-rCN': 'Chinese (Simplified)',
}

# Collect untranslated strings for each language
rows = []
skip_strings = ['bluesky', 'instagram', 'facebook', 'homepage', 'about_author_name',
                'mackenzie_l_davis', 'water_and_wastewater_engineering',
                'sothree_android_sliding_up_panel', 'wikipedia_commons']

for values_dir, lang_name in LANGUAGES.items():
    strings_file = os.path.join(res_path, values_dir, "strings.xml")
    if not os.path.exists(strings_file):
        continue
    
    tree = ET.parse(strings_file)
    root = tree.getroot()
    trans_strings = {}
    for string_elem in root.findall('string'):
        name = string_elem.get('name')
        text = string_elem.text if string_elem.text else ''
        trans_strings[name] = text
    
    # Find strings that match English exactly (likely untranslated)
    for name in trans_strings:
        if name in en_strings and trans_strings[name] == en_strings[name]:
            if name not in skip_strings:
                rows.append({
                    'string_id': name,
                    'language': lang_name,
                    'language_code': values_dir,
                    'english_text': en_strings[name],
                    'current_translation': trans_strings[name],
                    'needs_translation': 'YES'
                })

# Write to CSV
with open(output_file, 'w', newline='', encoding='utf-8') as f:
    fieldnames = ['string_id', 'language', 'language_code', 'english_text', 
                  'current_translation', 'needs_translation']
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(rows)

print(f"Extracted {len(rows)} untranslated strings to {output_file}")
print(f"\nThis file can be:")
print("  1. Imported into Google Sheets for collaborative translation")
print("  2. Sent to professional translators")
print("  3. Used with translation management tools")
print("\nAfter translation, use apply_translations.py to import back into the project")
