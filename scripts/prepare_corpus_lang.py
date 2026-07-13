#!/usr/bin/env python3
"""Prepare corpus.jsonl for a single language file elements_{lang}.json
Writes data/corpus.jsonl (overwrites) from the specified language asset.
Usage: python scripts/prepare_corpus_lang.py en
"""
import json
import sys
from pathlib import Path

if len(sys.argv) < 2:
    print("Usage: prepare_corpus_lang.py <lang>")
    sys.exit(2)

lang = sys.argv[1]
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'
SRC = ASSETS / f'elements_{lang}.json'
OUT = ROOT / 'data'
OUT.mkdir(exist_ok=True)
OUT_FILE = OUT / 'corpus.jsonl'

if not SRC.exists():
    print(f"Source asset {SRC} not found")
    sys.exit(1)


def extract_records(obj):
    records = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            if isinstance(v, dict):
                name = v.get('name') or v.get('symbol') or str(k)
                parts = []
                for f in ('summary','description','wiki','wiki_summary','info','overview'):
                    if v.get(f):
                        parts.append(str(v.get(f)))
                if not parts:
                    parts.append(json.dumps(v))
                text = '\n'.join(parts)
                records.append({'id': str(k), 'title': name, 'text': text})
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            if isinstance(v, dict):
                name = v.get('name') or v.get('symbol') or str(i)
                parts = []
                for f in ('summary','description','wiki','wiki_summary','info','overview'):
                    if v.get(f):
                        parts.append(str(v.get(f)))
                if not parts:
                    parts.append(json.dumps(v))
                text = '\n'.join(parts)
                records.append({'id': str(i), 'title': name, 'text': text})
    return records

try:
    data = json.loads(SRC.read_text(encoding='utf-8'))
except Exception as e:
    print(f"Failed loading {SRC}: {e}")
    sys.exit(1)

recs = extract_records(data)
if not recs and isinstance(data, dict):
    for k, v in data.items():
        recs.append({'id': str(k), 'title': str(k), 'text': str(v)})

# dedupe
seen = set()
unique = []
for r in recs:
    key = (r.get('title'), r.get('text'))
    if key in seen:
        continue
    seen.add(key)
    unique.append(r)

with OUT_FILE.open('w', encoding='utf-8') as fh:
    for r in unique:
        fh.write(json.dumps(r, ensure_ascii=False) + '\n')

print(f'Wrote {len(unique)} passages to {OUT_FILE} for language {lang}')
