#!/usr/bin/env python3
"""Prepare corpus JSONL from app-local knowledge sources.
Looks for:
- app/src/main/assets/elements_*.json
- selected hardcoded model lists in app/src/main/java/com/jlindemann/science/model

Writes data/corpus.jsonl where each line is:
{"id","title","text"}
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'
MODELS = ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'jlindemann' / 'science' / 'model'
OUT = ROOT / 'data'
OUT.mkdir(exist_ok=True)
OUT_FILE = OUT / 'corpus.jsonl'

def extract_records(obj):
    """Try to extract element records from a loaded JSON object."""
    records = []
    # If it's a dict of elements keyed by number/symbol
    if isinstance(obj, dict):
        for k, v in obj.items():
            if isinstance(v, dict):
                name = v.get('name') or v.get('symbol') or str(k)
                parts = []
                for f in ('summary','description','wiki','wiki_summary','info','overview'):
                    if v.get(f):
                        parts.append(str(v.get(f)))
                # Fall back to stringifying the dict
                if not parts:
                    parts.append(json.dumps(v))
                text = '\n'.join(parts)
                records.append({'id': str(k), 'title': name, 'text': text})
    # If it's a list
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


STRING = r'"((?:[^"\\]|\\.)*)"'


def unescape_kotlin_string(value):
    return bytes(value, 'utf-8').decode('unicode_escape')


def extract_model_records():
    records = []
    patterns = [
        (
            MODELS / 'ConstantsModel.kt',
            re.compile(rf'Constants\({STRING},\s*{STRING},\s*{STRING},\s*{STRING},\s*{STRING}\)'),
            lambda m: {
                'id': f"constant:{unescape_kotlin_string(m.group(1))}",
                'title': unescape_kotlin_string(m.group(1)),
                'text': f"Constant in category {unescape_kotlin_string(m.group(5))}. "
                        f"Value: {unescape_kotlin_string(m.group(2))} {unescape_kotlin_string(m.group(3))}. "
                        f"Symbol/info: {unescape_kotlin_string(m.group(4))}."
            }
        ),
        (
            MODELS / 'DictionaryModel.kt',
            re.compile(rf'Dictionary\({STRING},\s*{STRING},\s*{STRING},\s*{STRING}\)'),
            lambda m: {
                'id': f"dictionary:{unescape_kotlin_string(m.group(2))}",
                'title': unescape_kotlin_string(m.group(2)),
                'text': f"Dictionary entry in categories {unescape_kotlin_string(m.group(1))}. "
                        f"{unescape_kotlin_string(m.group(3))}"
            }
        ),
        (
            MODELS / 'EquationModel.kt',
            re.compile(rf'Equation\({STRING},\s*{STRING},\s*R\.drawable\.[^,]+,\s*{STRING}\)'),
            lambda m: {
                'id': f"equation:{unescape_kotlin_string(m.group(1))}",
                'title': unescape_kotlin_string(m.group(1)),
                'text': f"Equation in category {unescape_kotlin_string(m.group(2))}. "
                        f"{unescape_kotlin_string(m.group(3))}"
            }
        ),
        (
            MODELS / 'IndicatorModel.kt',
            re.compile(rf'Indicator\({STRING},\s*{STRING},\s*{STRING},\s*{STRING},\s*{STRING},\s*{STRING},\s*{STRING}\)'),
            lambda m: {
                'id': f"indicator:{unescape_kotlin_string(m.group(1))}",
                'title': unescape_kotlin_string(m.group(1)).replace('_', ' '),
                'text': f"pH indicator. Acid below {unescape_kotlin_string(m.group(2))} is {unescape_kotlin_string(m.group(3))}. "
                        f"Neutral range {unescape_kotlin_string(m.group(4))} is {unescape_kotlin_string(m.group(5))}. "
                        f"Alkali above {unescape_kotlin_string(m.group(6))} is {unescape_kotlin_string(m.group(7))}."
            }
        ),
        (
            MODELS / 'PoissonModel.kt',
            re.compile(rf'Poisson\({STRING},\s*([-0-9.]+),\s*([-0-9.]+),\s*{STRING}\)'),
            lambda m: {
                'id': f"poisson:{unescape_kotlin_string(m.group(1))}",
                'title': unescape_kotlin_string(m.group(1)),
                'text': f"Poisson ratio for {unescape_kotlin_string(m.group(4))}. "
                        f"Range: {m.group(2)} to {m.group(3)}."
            }
        ),
        (
            MODELS / 'IonModel.kt',
            re.compile(rf'Ion\({STRING},\s*{STRING},\s*([0-9]+)\)'),
            lambda m: {
                'id': f"ion:{unescape_kotlin_string(m.group(1))}",
                'title': unescape_kotlin_string(m.group(1)),
                'text': f"Element symbol {unescape_kotlin_string(m.group(2))}. "
                        f"The app includes {m.group(3)} ionization energy value(s)."
            }
        ),
    ]

    for path, pattern, mapper in patterns:
        if not path.exists():
            continue
        text = path.read_text(encoding='utf-8')
        for match in pattern.finditer(text):
            records.append(mapper(match))
    return records

if __name__ == '__main__':
    files = sorted(ASSETS.glob('elements_*.json'))
    all_records = []
    for f in files:
        try:
            data = json.loads(f.read_text(encoding='utf-8'))
        except Exception as e:
            print(f'Failed loading {f}: {e}')
            continue
        recs = extract_records(data)
        if not recs:
            # As a fallback, if top-level is dict and values are primitives, treat as simple mapping
            if isinstance(data, dict):
                for k,v in data.items():
                    all_records.append({'id': str(k), 'title': str(k), 'text': str(v)})
        else:
            all_records.extend(recs)

    all_records.extend(extract_model_records())

    # Deduplicate by title
    seen = set()
    unique = []
    for r in all_records:
        key = (r.get('title'), r.get('text'))
        if key in seen:
            continue
        seen.add(key)
        unique.append(r)

    with OUT_FILE.open('w', encoding='utf-8') as fh:
        for r in unique:
            fh.write(json.dumps(r, ensure_ascii=False) + '\n')

    print(f'Wrote {len(unique)} passages to {OUT_FILE}')
