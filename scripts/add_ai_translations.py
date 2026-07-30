#!/usr/bin/env python3
"""Insert missing ai_* strings into a locale's strings.xml.

Every agent string a locale lacks falls back to English *inside* an otherwise translated answer.
That is the mechanism behind the half-Swedish replies: 97 of 319 ai_* keys had no Swedish value, so
a Swedish template wrapped English fragments and the "Open Gold" chip was always English.

Usage:
    python scripts/add_ai_translations.py <locale-folder> <translations.py>

The translations file defines a single dict named TRANSLATIONS mapping string name -> translated
text. Keys already present in the target file are left alone, so this is safe to re-run. Keys the
dict does not cover are reported and skipped rather than guessed at.

Placeholders (%1$s, %2$d, %%), XML entities (&amp;) and Android's escaped apostrophes (\\') must be
carried through unchanged; the script verifies the placeholder set matches English and refuses the
string if it does not, because a mismatched format argument is a crash at runtime rather than a
cosmetic error.
"""
import io
import os
import re
import sys

NAME = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
PLACEHOLDER = re.compile(r'%(\d+)\$[sd]')


def read_strings(path):
    if not os.path.isfile(path):
        return {}
    return {m.group(1): m.group(2) for m in NAME.finditer(io.open(path, encoding='utf-8').read())}


def placeholders(text):
    return sorted(PLACEHOLDER.findall(text))


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    folder, table_path = sys.argv[1], sys.argv[2]
    res = 'app/src/main/res' if os.path.isdir('app/src/main/res') else 'src/main/res'
    target = os.path.join(res, folder, 'strings.xml')
    if not os.path.isfile(target):
        print(f'no strings.xml in {folder}')
        return 1

    namespace = {}
    exec(io.open(table_path, encoding='utf-8').read(), namespace)
    translations = namespace['TRANSLATIONS']

    english = {k: v for k, v in read_strings(os.path.join(res, 'values', 'strings.xml')).items()
               if k.startswith('ai_')}
    existing = read_strings(target)
    missing = [k for k in english if k not in existing]

    additions, skipped, mismatched = [], [], []
    for key in sorted(missing):
        value = translations.get(key)
        if value is None:
            skipped.append(key)
            continue
        if placeholders(value) != placeholders(english[key]):
            mismatched.append(f'{key}: expected {placeholders(english[key])}, got {placeholders(value)}')
            continue
        additions.append((key, value))

    if mismatched:
        print('REFUSED — placeholder mismatch would crash at format time:')
        for m in mismatched:
            print('  ', m)
        return 1

    if additions:
        text = io.open(target, encoding='utf-8').read()
        note = namespace.get('NOTE', 'Agent strings: added so answers are not half-English.')
        block = ['', f'    <!-- {note} -->']
        block += [f'    <string name="{k}">{v}</string>' for k, v in additions]
        block.append('')
        insert = text.rindex('</resources>')
        text = text[:insert] + '\n'.join(block) + '\n' + text[insert:]
        io.open(target, 'w', encoding='utf-8', newline='').write(text)

    print(f'{folder}: added {len(additions)}, still missing {len(skipped)}')
    if skipped:
        print('  not covered by the table:', ', '.join(skipped[:15]) + (' …' if len(skipped) > 15 else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
