#!/usr/bin/env python3
"""
Check translation status for all language files.
Reports which strings are still in English and need translation.
"""
import os
import sys
import xml.etree.ElementTree as ET

# Determine repo path
if len(sys.argv) > 1:
    repo_path = sys.argv[1]
else:
    repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

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

print("=" * 80)
print("TRANSLATION STATUS CHECK")
print("=" * 80)
print(f"\nTotal translatable English strings: {len(en_strings)}")

# Check each translation file
values_dirs = [d for d in os.listdir(res_path) if d.startswith('values-') and 
               not d.startswith('values-v') and not d.startswith('values-night')]

for values_dir in sorted(values_dirs):
    strings_file = os.path.join(res_path, values_dir, "strings.xml")
    if not os.path.exists(strings_file):
        print(f"\n{values_dir}: NO strings.xml file")
        continue
    
    tree = ET.parse(strings_file)
    root = tree.getroot()
    trans_strings = {}
    for string_elem in root.findall('string'):
        name = string_elem.get('name')
        text = string_elem.text if string_elem.text else ''
        trans_strings[name] = text
    
    # Find missing strings
    missing = set(en_strings.keys()) - set(trans_strings.keys())
    
    # Find strings that match English exactly (likely untranslated)
    likely_untranslated = []
    for name in trans_strings:
        if name in en_strings and trans_strings[name] == en_strings[name]:
            # Skip strings that are the same in all languages (URLs, names, etc.)
            if name not in ['bluesky', 'instagram', 'facebook', 'homepage', 
                           'about_author_name', 'mackenzie_l_davis',
                           'water_and_wastewater_engineering',
                           'sothree_android_sliding_up_panel',
                           'wikipedia_commons']:
                likely_untranslated.append(name)
    
    print(f"\n{values_dir}:")
    print(f"  Total strings: {len(trans_strings)}/{len(en_strings)}")
    if missing:
        print(f"  Missing: {len(missing)} strings")
    if likely_untranslated:
        print(f"  Likely untranslated (matches English): {len(likely_untranslated)} strings")
        if len(likely_untranslated) <= 20:
            for name in likely_untranslated[:10]:
                print(f"    - {name}")
            if len(likely_untranslated) > 10:
                print(f"    ... and {len(likely_untranslated) - 10} more")
        else:
            print(f"    First 10: {', '.join(likely_untranslated[:10])}")
            print(f"    ... and {len(likely_untranslated) - 10} more")

print("\n" + "=" * 80)
print("Check complete!")
print("=" * 80)
