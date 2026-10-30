#!/usr/bin/env python3
"""Escape apostrophes inside agent string resources.

Android requires an apostrophe in a string resource to be written as \\'. An unescaped one is not a
warning — aapt refuses to compile the entire values file, so one French string takes the whole build
down. Easy to reintroduce whenever a translation is added by hand, hence a script.

Only touches `ai_*` strings, and only apostrophes that are not already escaped.
"""
import glob
import io
import os
import re
import sys

STRING = re.compile(r'(<string name="(ai_[^"]+)">)(.*?)(</string>)', re.S)
BARE_APOSTROPHE = re.compile(r"(?<!\\)'")


def main():
    total = 0
    for path in sorted(glob.glob('app/src/main/res/values*/strings.xml')):
        text = io.open(path, encoding='utf-8').read()
        touched = []

        def repl(match):
            body = match.group(3)
            escaped = BARE_APOSTROPHE.sub(r"\\'", body)
            if escaped != body:
                touched.append(match.group(2))
            return match.group(1) + escaped + match.group(4)

        out = STRING.sub(repl, text)
        if out != text:
            io.open(path, 'w', encoding='utf-8', newline='').write(out)
            locale = os.path.basename(os.path.dirname(path))
            print(f'{locale}: escaped {len(touched)} — {", ".join(touched)}')
            total += len(touched)
    print(f'total fixed: {total}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
