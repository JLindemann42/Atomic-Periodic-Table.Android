#!/usr/bin/env python3
"""Report element descriptions that are still partly English.

The narrative answers ("what is titanium used for", "what is its biological role") are built by
splitting an element's `description` and filtering the sentences by keyword. When a locale's
description is half-translated and the keyword list is English, the filter *preferentially selects
the untranslated half* — which is how a Swedish answer came out as
"Det har various allotropes, but only the gray form, which has a metallic appearance…".

A naive "is it translated?" check does not catch this: none of these strings are byte-identical to
English, they are a Swedish sentence followed by an English one. So the test is lexical — how many
English function words appear in a string that is supposed to be in another language.

Usage:
    python scripts/check_description_translations.py [--list <lang>]

Exit code is always 0: this reports a data-quality backlog, it does not gate the build.
"""
import io
import os
import re
import sys

# Function words, not content words. "Titanium" appearing in a Swedish sentence is normal — the
# element name is often the same. "which has a metallic appearance" is not.
ENGLISH_FUNCTION_WORDS = re.compile(
    r'\b(the|and|which|with|has|have|is|are|was|were|been|of|from|that|its|it|only|also|'
    r'most|used|found|other|than|but|such|these|those|there|their|when|while|into|about)\b'
)

# Below this many English function words a sentence is plausibly just borrowed terminology.
THRESHOLD = 4

DESCRIPTION = re.compile(r'"description"\s*:\s*"((?:[^"\\]|\\.)*)"')


def assets_dir():
    for candidate in ('app/src/main/assets', 'src/main/assets'):
        if os.path.isdir(candidate):
            return candidate
    raise SystemExit('could not locate the assets directory')


def descriptions(path):
    text = io.open(path, encoding='utf-8').read()
    return DESCRIPTION.findall(text)


def main():
    root = assets_dir()
    languages = sorted(
        name[len('elements_'):-len('.json')]
        for name in os.listdir(root)
        if name.startswith('elements_') and name.endswith('.json')
    )

    wanted = None
    if len(sys.argv) == 3 and sys.argv[1] == '--list':
        wanted = sys.argv[2]

    print(f'{"lang":6} {"total":>6} {"part-English":>13} {"share":>7}')
    print('-' * 36)
    for language in languages:
        if language == 'en':
            continue
        found = descriptions(os.path.join(root, f'elements_{language}.json'))
        mixed = [d for d in found if len(ENGLISH_FUNCTION_WORDS.findall(d.lower())) >= THRESHOLD]
        share = 100.0 * len(mixed) / max(len(found), 1)
        print(f'{language:6} {len(found):6} {len(mixed):13} {share:6.1f}%')
        if wanted == language:
            print()
            for d in mixed:
                print('   ', d[:160] + ('…' if len(d) > 160 else ''))
            print()

    print()
    print('These are a data backlog, not a code defect. The code-side mitigation is that the')
    print('sentence filters now use localized keyword lists, so they no longer preferentially')
    print('select the English half of a mixed description.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
