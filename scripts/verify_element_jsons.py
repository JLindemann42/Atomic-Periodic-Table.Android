#!/usr/bin/env python3
"""
Comprehensive verification script for element JSON translations.

This script checks:
1. JSON validity
2. Structure consistency with reference file
3. Translation completeness
4. Field presence
5. Data integrity
6. Physical and internal consistency of the values themselves (all 12 files, English included)
7. That language-invariant fields really are identical in every language

Translation completeness is reported for information only. The checks in 6 and 7 are hard
failures: the script exits non-zero when any of them trips.

Usage:
    python3 verify_element_jsons.py [--detailed] [--json-output]

Options:
    --detailed      Show detailed information about untranslated elements
    --json-output   Output results in JSON format
"""
import os
import re
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

ALL_ELEMENT_FILES = {
    'elements_en.json': 'English',
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


# ---------------------------------------------------------------------------
# Value-level checks.
#
# Every rule below was added because it caught a real defect in the shipped data: swapped
# melting and boiling columns on cerium and lanthanum, an isotope table labelled with the wrong
# element's symbol on boron, a dropped shell label on 34 elements, an ionization-energy list
# shifted by two positions on nickel. Keep them strict - each one has a clean pass rate on the
# other 117 elements, so a new failure means new bad data rather than a tolerance to widen.
# ---------------------------------------------------------------------------

# Fields whose value legitimately differs per language. Everything else must match English
# byte for byte, because the twelve files describe the same 118 elements.
LOCALIZED_FIELDS = {
    'element', 'element_group', 'description', 'element_appearance', 'element_phase',
    'electrical_type', 'magnetic_type', 'element_discovered_name', 'element_year',
    'crystal_structure', 'element_block', 'human_body', 'special',
}

# Sentinels the data uses for "no value". Mirrors ValueParser.NULL_TOKENS in the Kotlin source.
NULL_TOKENS = {'', '---', '--', 'n/a', 'none', 'null', '?', '???', 'unknown'}

# Shell occupancies are authored as "K2 L8 M18 N18 O5 P0 Q0 R0".
SHELL_PATTERN = re.compile(r'^K\d+ L\d+ M\d+ N\d+ O\d+ P\d+ Q\d+ R\d+$')

# Isotope labels appear as "B-11" and, on four elements, as "127-Sb".
ISOTOPE_LABEL = re.compile(r'^([A-Za-z]+)-(\d+)$|^(\d+)-([A-Za-z]+)$')

# element_model points at a Wikimedia electron-shell diagram named after its own element.
SHELL_DIAGRAM = re.compile(r'Electron_shell_(\d+)_([A-Za-z]+)')

MEDIA_FIELDS = ('link', 'element_model', 'spectral_img', 'wikilink')

# Standard temperature, which is what element_phase describes. Not 298.15 K: francium melts at
# 281 K and is correctly authored as a solid.
STP_KELVIN = 273.15


def leading_number(raw):
    """The numeric prefix of a unit-suffixed value such as "1811 (K)", or None."""
    if not isinstance(raw, str):
        return None
    match = re.match(r'^-?[\d.]+', raw.replace('−', '-').strip())
    if not match:
        return None
    try:
        return float(match.group())
    except ValueError:
        return None


def is_missing(raw):
    """True when a cell carries one of the data's "no value" sentinels."""
    return not isinstance(raw, str) or raw.strip().lower() in NULL_TOKENS


def isotope_indices(element_data):
    """The 1-based indices of an element's isotope rows, which are numbered contiguously."""
    index = 1
    while f'iso_{index}' in element_data:
        index += 1
    return range(1, index)


def mass_number(label):
    """The mass number in an isotope label, accepting both "B-11" and "127-Sb"."""
    match = ISOTOPE_LABEL.match(str(label))
    if not match:
        return None
    digits = match.group(2) or match.group(3)
    return int(digits)


def isotope_symbol(label):
    """The element symbol in an isotope label, or None when the label spells the name out."""
    match = ISOTOPE_LABEL.match(str(label))
    if not match:
        return None
    symbol = match.group(1) or match.group(4)
    # "Actinium-227" spells the name out rather than using a symbol; nothing to compare.
    return symbol if len(symbol) <= 2 else None


def check_identity(element_key, element_data, issues):
    """Atomic number, proton count and electron count are the same number."""
    number = element_data.get('element_atomic_number')
    protons = element_data.get('element_protons')
    electrons = element_data.get('element_electrons')
    if not (number == protons == electrons):
        issues.append({
            'element': element_key,
            'issue': 'atomic_number_mismatch',
            'detail': f'Z={number} protons={protons} electrons={electrons}',
        })


def check_shells(element_key, element_data, issues):
    """The shell string is well formed and its occupancies sum to the electron count."""
    shells = element_data.get('element_shells_electrons')
    electrons = element_data.get('element_electrons')
    if is_missing(shells):
        return
    if not SHELL_PATTERN.match(shells):
        issues.append({
            'element': element_key,
            'issue': 'shell_format',
            'detail': repr(shells),
        })
        return
    if not str(electrons).isdigit():
        return
    total = sum(int(n) for n in re.findall(r'[A-Z](\d+)', shells))
    if total != int(electrons):
        issues.append({
            'element': element_key,
            'issue': 'shell_sum',
            'detail': f'{shells!r} sums to {total}, expected {electrons}',
        })


def check_isotopes(element_key, element_data, issues):
    """Isotope rows agree with their own labels, with Z, and with each other."""
    symbol = element_data.get('short')
    number = element_data.get('element_atomic_number')
    if not str(number).isdigit():
        return
    number = int(number)
    seen = {}

    for index in isotope_indices(element_data):
        label = element_data.get(f'iso_{index}')
        if is_missing(label):
            continue
        mass = mass_number(label)
        if mass is None:
            issues.append({
                'element': element_key,
                'issue': 'isotope_label_unparsed',
                'detail': f'iso_{index}={label!r}',
            })
            continue

        row_symbol = isotope_symbol(label)
        if row_symbol is not None and row_symbol != symbol:
            issues.append({
                'element': element_key,
                'issue': 'isotope_wrong_symbol',
                'detail': f'iso_{index}={label!r} but the element is {symbol}',
            })

        z = element_data.get(f'iso_Z_{index}')
        n = element_data.get(f'iso_N_{index}')
        a = element_data.get(f'iso_A_{index}')
        if not (str(z).isdigit() and str(n).isdigit() and str(a).isdigit()):
            issues.append({
                'element': element_key,
                'issue': 'isotope_non_numeric',
                'detail': f'iso_{index}={label!r} Z={z!r} N={n!r} A={a!r}',
            })
            continue
        z, n, a = int(z), int(n), int(a)

        if z != number:
            issues.append({
                'element': element_key,
                'issue': 'isotope_wrong_z',
                'detail': f'iso_{index}={label!r} has Z={z}, element Z={number}',
            })
        if a != z + n:
            issues.append({
                'element': element_key,
                'issue': 'isotope_mass_arithmetic',
                'detail': f'iso_{index}={label!r} A={a} but Z={z} + N={n} = {z + n}',
            })
        if a != mass:
            issues.append({
                'element': element_key,
                'issue': 'isotope_label_disagrees_with_a',
                'detail': f'iso_{index}={label!r} but A={a}',
            })
        if a in seen:
            issues.append({
                'element': element_key,
                'issue': 'duplicate_isotope',
                'detail': f'A={a} appears as iso_{seen[a]} and iso_{index}',
            })
        seen[a] = index


def check_temperatures(element_key, element_data, issues):
    """The three temperature scales agree, and melting comes before boiling."""
    for transition in ('melting', 'boiling'):
        kelvin = leading_number(element_data.get(f'element_{transition}_kelvin'))
        celsius = leading_number(element_data.get(f'element_{transition}_celsius'))
        fahrenheit = leading_number(element_data.get(f'element_{transition}_fahrenheit'))
        if kelvin is None:
            continue
        if celsius is not None and abs((kelvin - 273.15) - celsius) > 1.0:
            issues.append({
                'element': element_key,
                'issue': f'{transition}_celsius_mismatch',
                'detail': f'{kelvin} K is {kelvin - 273.15:.2f} C, file says {celsius}',
            })
        if fahrenheit is not None and abs((kelvin * 9 / 5 - 459.67) - fahrenheit) > 2.0:
            issues.append({
                'element': element_key,
                'issue': f'{transition}_fahrenheit_mismatch',
                'detail': f'{kelvin} K is {kelvin * 9 / 5 - 459.67:.2f} F, file says {fahrenheit}',
            })

    melting = leading_number(element_data.get('element_melting_kelvin'))
    boiling = leading_number(element_data.get('element_boiling_kelvin'))
    if melting is not None and boiling is not None and melting > boiling:
        issues.append({
            'element': element_key,
            'issue': 'melting_above_boiling',
            'detail': f'melts at {melting} K but boils at {boiling} K',
        })


def check_phase(element_key, element_data, issues):
    """element_phase matches what the melting and boiling points imply at STP."""
    phase = element_data.get('element_phase')
    melting = leading_number(element_data.get('element_melting_kelvin'))
    boiling = leading_number(element_data.get('element_boiling_kelvin'))
    if is_missing(phase) or melting is None or boiling is None:
        return
    if boiling < STP_KELVIN:
        expected = 'gas'
    elif melting <= STP_KELVIN <= boiling:
        expected = 'liquid'
    else:
        expected = 'solid'
    if phase.strip().lower() != expected:
        issues.append({
            'element': element_key,
            'issue': 'phase_mismatch',
            'detail': f'{phase!r} but mp={melting} K bp={boiling} K imply {expected}',
        })


def check_ionization_energies(element_key, element_data, issues):
    """Successive ionization energies increase. Gaps in coverage are fine; disorder is not."""
    previous = None
    index = 1
    while f'element_ionization_energy{index}' in element_data:
        current = leading_number(element_data[f'element_ionization_energy{index}'])
        if previous is not None and current is not None and current < previous:
            issues.append({
                'element': element_key,
                'issue': 'ionization_energy_not_increasing',
                'detail': f'IE{index - 1}={previous} then IE{index}={current}',
            })
        if current is not None:
            previous = current
        index += 1


def check_media(element_key, element_data, issues):
    """Media URLs use https and, for shell diagrams, depict the right element."""
    for field in MEDIA_FIELDS:
        value = element_data.get(field)
        if not isinstance(value, str) or is_missing(value):
            continue
        if value.startswith('http://'):
            issues.append({
                'element': element_key,
                'issue': 'cleartext_url',
                'detail': f'{field} uses http, which is blocked at the app target SDK',
            })
        elif not value.startswith('https://'):
            issues.append({
                'element': element_key,
                'issue': 'malformed_url',
                'detail': f'{field}={value!r}',
            })

    match = SHELL_DIAGRAM.search(str(element_data.get('element_model')))
    if match:
        diagram_z, diagram_name = int(match.group(1)), match.group(2).lower()
        if str(diagram_z) != str(element_data.get('element_atomic_number')) or \
                diagram_name != element_key.lower():
            issues.append({
                'element': element_key,
                'issue': 'wrong_shell_diagram',
                'detail': f'element_model depicts {match.group(0)}',
            })


def check_whitespace(element_key, element_data, issues):
    """No stray leading or trailing whitespace; it defeats exact-match comparisons."""
    for field, value in element_data.items():
        if isinstance(value, str) and value != value.strip():
            issues.append({
                'element': element_key,
                'issue': 'untrimmed_value',
                'detail': f'{field}={value!r}',
            })


def check_physics(data, is_reference=False):
    """Run every value-level check over one language file.

    `element_phase` is one of the localized fields, so its wording can only be matched against
    the melting and boiling points in the English file. Every other check here compares numbers
    or element symbols and is therefore language independent.
    """
    issues = []
    codes = defaultdict(list)

    for element_key, element_data in data.items():
        check_identity(element_key, element_data, issues)
        check_shells(element_key, element_data, issues)
        check_isotopes(element_key, element_data, issues)
        check_temperatures(element_key, element_data, issues)
        if is_reference:
            check_phase(element_key, element_data, issues)
        check_ionization_energies(element_key, element_data, issues)
        check_media(element_key, element_data, issues)
        check_whitespace(element_key, element_data, issues)

        # element_code keys note storage in ProgressSyncManager, so it must exist and be unique.
        code = element_data.get('element_code')
        if not code:
            issues.append({
                'element': element_key,
                'issue': 'missing_element_code',
                'detail': repr(code),
            })
        else:
            codes[code].append(element_key)

    for code, owners in codes.items():
        if len(owners) > 1:
            issues.append({
                'element': ', '.join(sorted(owners)),
                'issue': 'duplicate_element_code',
                'detail': code,
            })

    return issues


def check_language_invariance(data, reference_data):
    """Every field outside LOCALIZED_FIELDS must equal the English value."""
    issues = []
    for element_key, reference_row in reference_data.items():
        row = data.get(element_key)
        if row is None:
            continue
        for field, expected in reference_row.items():
            if field in LOCALIZED_FIELDS:
                continue
            if row.get(field) != expected:
                issues.append({
                    'element': element_key,
                    'issue': 'language_invariant_field_differs',
                    'detail': f'{field}: {row.get(field)!r} != English {expected!r}',
                })
    return issues


def report_value_checks(value_results, detailed):
    """Print the value-level findings, grouped by file then by kind of defect."""
    print("\n" + "=" * 80)
    print("DATA INTEGRITY (values, all 12 files)")
    print("=" * 80 + "\n")

    for elem_file, lang_name in ALL_ELEMENT_FILES.items():
        issues = value_results.get(elem_file, [])
        if not issues:
            print(f"  {lang_name:15} ({elem_file:25}) OK")
            continue

        print(f"  {lang_name:15} ({elem_file:25}) {len(issues)} issue(s)")
        grouped = defaultdict(list)
        for issue in issues:
            grouped[issue['issue']].append(issue)
        for kind, group in sorted(grouped.items()):
            print(f"      {kind}: {len(group)}")
            shown = group if detailed else group[:5]
            for issue in shown:
                print(f"        - {issue['element']}: {issue['detail']}")
            if len(group) > len(shown):
                print(f"        ... {len(group) - len(shown)} more (use --detailed)")


def main():
    parser = argparse.ArgumentParser(description='Verify element JSON translations')
    # Consumed at import time to locate the assets; declared here so argparse accepts it.
    parser.add_argument('repo_path', nargs='?',
                        help='Repository root to check (defaults to this script\'s repo)')
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
    
    # Value-level checks, over all 12 files including English.
    value_results = {}
    for elem_file in ALL_ELEMENT_FILES:
        file_path = os.path.join(assets_path, elem_file)
        if not os.path.exists(file_path):
            value_results[elem_file] = [{'element': '-', 'issue': 'missing_file', 'detail': ''}]
            continue
        valid, data, error = check_json_validity(file_path)
        if not valid:
            value_results[elem_file] = [
                {'element': '-', 'issue': 'invalid_json', 'detail': error}
            ]
            continue
        is_reference = elem_file == 'elements_en.json'
        issues = check_physics(data, is_reference=is_reference)
        if not is_reference:
            issues += check_language_invariance(data, reference_data)
        value_results[elem_file] = issues

    value_issue_count = sum(len(v) for v in value_results.values())

    # Output results
    if args.json_output:
        print(json.dumps({
            'translations': results,
            'value_checks': value_results,
            'value_issue_count': value_issue_count,
        }, indent=2))
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

        report_value_checks(value_results, args.detailed)

        print("\n" + "=" * 80)
        if value_issue_count:
            print(f"❌ {value_issue_count} value-level issue(s) across the element data")
        else:
            print("✅ All 12 files passed the value-level and language-invariance checks")
        print("=" * 80)

    # Translation completeness is informational. Bad values are not.
    return 1 if value_issue_count else 0


if __name__ == '__main__':
    sys.exit(main())
