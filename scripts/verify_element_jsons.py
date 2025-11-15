#!/usr/bin/env python3
"""
Comprehensive verification script for element JSON translations.

This script checks:
1. JSON validity
2. Structure consistency with reference file
3. Translation completeness
4. Field presence
5. Data integrity

Usage:
    python3 verify_element_jsons.py [--detailed] [--json-output]

Options:
    --detailed      Show detailed information about untranslated elements
    --json-output   Output results in JSON format
"""
import os
import sys
import json
import argparse
from collections import defaultdict

# Determine repo path
if len(sys.argv) > 1 and not sys.argv[1].startswith('--'):
    repo_path = sys.argv[1]
else:
    repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

assets_path = os.path.join(repo_path, "app/src/main/assets")

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

# English phrases that indicate untranslated content
ENGLISH_INDICATORS = [
    'is a chemical element',
    'was first isolated',
    'was first discovered',
    'It was first',
    'The element',
    'and atomic number',
    'in the periodic table',
]


def check_json_validity(file_path):
    """Check if JSON is valid."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return True, data, None
    except json.JSONDecodeError as e:
        return False, None, str(e)
    except Exception as e:
        return False, None, str(e)


def check_structure(data, reference_data):
    """Check if all elements and fields are present."""
    issues = []
    
    # Check if all elements from reference exist
    ref_elements = set(reference_data.keys())
    curr_elements = set(data.keys())
    
    missing = ref_elements - curr_elements
    extra = curr_elements - ref_elements
    
    if missing:
        issues.append({
            'type': 'missing_elements',
            'count': len(missing),
            'elements': sorted(list(missing))
        })
    if extra:
        issues.append({
            'type': 'extra_elements',
            'count': len(extra),
            'elements': sorted(list(extra))
        })
    
    # Check fields in first element
    if data and reference_data:
        first_elem = list(data.keys())[0]
        ref_first = list(reference_data.keys())[0]
        
        ref_fields = set(reference_data[ref_first].keys())
        curr_fields = set(data[first_elem].keys())
        
        missing_fields = ref_fields - curr_fields
        if missing_fields:
            issues.append({
                'type': 'missing_fields',
                'fields': sorted(list(missing_fields))
            })
    
    return issues


def check_translation_status(data, lang_name):
    """Check if descriptions are translated."""
    english_count = 0
    total_checked = 0
    untranslated_elements = []
    
    for element_key, element_data in data.items():
        if 'description' in element_data:
            desc = element_data['description']
            if desc and any(phrase.lower() in desc.lower() for phrase in ENGLISH_INDICATORS):
                english_count += 1
                untranslated_elements.append(element_key)
            total_checked += 1
    
    return english_count, total_checked, untranslated_elements


def check_data_integrity(data):
    """Check for common data issues."""
    issues = []
    
    for element_key, element_data in data.items():
        # Check for empty descriptions
        if 'description' in element_data:
            if not element_data['description'] or element_data['description'].strip() == '':
                issues.append({
                    'element': element_key,
                    'issue': 'empty_description'
                })
        else:
            issues.append({
                'element': element_key,
                'issue': 'missing_description_field'
            })
        
        # Check for essential fields
        essential_fields = ['element', 'short', 'element_atomic_number']
        for field in essential_fields:
            if field not in element_data:
                issues.append({
                    'element': element_key,
                    'issue': f'missing_field_{field}'
                })
    
    return issues


def main():
    parser = argparse.ArgumentParser(description='Verify element JSON translations')
    parser.add_argument('--detailed', action='store_true', 
                        help='Show detailed information about untranslated elements')
    parser.add_argument('--json-output', action='store_true',
                        help='Output results in JSON format')
    args = parser.parse_args()
    
    # Load reference (English) file first
    en_file_path = os.path.join(assets_path, 'elements_en.json')
    valid, reference_data, error = check_json_validity(en_file_path)
    
    if not valid:
        print(f"❌ ERROR: Reference file (elements_en.json) is invalid!")
        print(f"Error: {error}")
        sys.exit(1)
    
    if not args.json_output:
        print("=" * 80)
        print("ELEMENT JSON VERIFICATION")
        print("=" * 80)
        print(f"\n✓ Reference file loaded: {len(reference_data)} elements")
    
    # Check all other files
    results = {}
    for elem_file, lang_name in ELEMENT_FILES.items():
        file_path = os.path.join(assets_path, elem_file)
        
        if not os.path.exists(file_path):
            results[elem_file] = {
                'status': 'missing',
                'language': lang_name
            }
            continue
        
        # 1. JSON Validity
        valid, data, error = check_json_validity(file_path)
        if not valid:
            results[elem_file] = {
                'status': 'invalid',
                'language': lang_name,
                'error': error
            }
            continue
        
        # 2. Structure Check
        structure_issues = check_structure(data, reference_data)
        
        # 3. Translation Status
        english_count, total_checked, untranslated_elements = check_translation_status(data, lang_name)
        translation_percentage = ((total_checked - english_count) / total_checked * 100) if total_checked > 0 else 0
        
        # 4. Data Integrity
        integrity_issues = check_data_integrity(data)
        
        # Store results
        results[elem_file] = {
            'status': 'ok',
            'language': lang_name,
            'total_elements': len(data),
            'translated': total_checked - english_count,
            'untranslated': english_count,
            'translation_percentage': round(translation_percentage, 2),
            'structure_issues': structure_issues,
            'integrity_issues': integrity_issues,
            'untranslated_elements': untranslated_elements if args.detailed else []
        }
    
    # Output results
    if args.json_output:
        print(json.dumps(results, indent=2))
    else:
        # Text output
        print("\n" + "=" * 80)
        print("RESULTS BY LANGUAGE")
        print("=" * 80)
        
        fully_translated = []
        partially_translated = []
        needs_translation = []
        
        for elem_file, result in results.items():
            if result['status'] != 'ok':
                needs_translation.append((elem_file, result))
            elif result['translation_percentage'] >= 95:
                fully_translated.append((elem_file, result))
            elif result['translation_percentage'] >= 20:
                partially_translated.append((elem_file, result))
            else:
                needs_translation.append((elem_file, result))
        
        print(f"\n✅ Fully Translated ({len(fully_translated)}):")
        for file, result in sorted(fully_translated, key=lambda x: -x[1].get('translation_percentage', 0)):
            pct = result.get('translation_percentage', 0)
            print(f"  {result['language']:15} ({file:25}) {pct:5.1f}%")
        
        if partially_translated:
            print(f"\n⚠️  Partially Translated ({len(partially_translated)}):")
            for file, result in sorted(partially_translated, key=lambda x: -x[1].get('translation_percentage', 0)):
                pct = result.get('translation_percentage', 0)
                untrans = result.get('untranslated', 0)
                print(f"  {result['language']:15} ({file:25}) {pct:5.1f}% ({untrans} need translation)")
        
        if needs_translation:
            print(f"\n❌ Needs Translation ({len(needs_translation)}):")
            for file, result in sorted(needs_translation, key=lambda x: -x[1].get('translation_percentage', 0)):
                if result['status'] == 'ok':
                    pct = result.get('translation_percentage', 0)
                    untrans = result.get('untranslated', 0)
                    print(f"  {result['language']:15} ({file:25}) {pct:5.1f}% ({untrans} need translation)")
                else:
                    print(f"  {result['language']:15} ({file:25}) {result['status'].upper()}")
        
        # Summary
        print("\n" + "=" * 80)
        print("SUMMARY")
        print("=" * 80)
        
        total_elements = sum(r.get('total_elements', 0) for r in results.values() if r['status'] == 'ok')
        total_translated = sum(r.get('translated', 0) for r in results.values() if r['status'] == 'ok')
        overall_pct = (total_translated / total_elements * 100) if total_elements > 0 else 0
        
        print(f"\nOverall: {total_translated}/{total_elements} descriptions translated ({overall_pct:.1f}%)")
        print(f"Fully translated: {len(fully_translated)} languages")
        print(f"Partially translated: {len(partially_translated)} languages")
        print(f"Needs translation: {len(needs_translation)} languages")
        
        # Check for issues
        total_struct_issues = sum(len(r.get('structure_issues', [])) for r in results.values())
        total_integrity_issues = sum(len(r.get('integrity_issues', [])) for r in results.values())
        
        print(f"\nStructural issues: {total_struct_issues}")
        print(f"Data integrity issues: {total_integrity_issues}")
        
        if total_struct_issues == 0 and total_integrity_issues == 0:
            print("\n✅ All files passed structural and integrity checks!")
        
        print("\n" + "=" * 80)


if __name__ == '__main__':
    main()
