#!/usr/bin/env python3
"""Compute embeddings for corpus and save compact artifacts for Android.
Produces:
 - data/passages.jsonl  (same as corpus.jsonl)
 - data/embeddings.npy  (numpy float32 array of shape [N, D])
 - data/embeddings_meta.json (list of ids/titles to map indices)

Requires: sentence-transformers, numpy
"""
import json
from pathlib import Path
import numpy as np

try:
    from sentence_transformers import SentenceTransformer
except Exception as e:
    raise RuntimeError('Install sentence-transformers: pip install sentence-transformers')

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'data' / 'corpus.jsonl'
OUT_DIR = ROOT / 'data'
OUT_DIR.mkdir(exist_ok=True)

MODEL_NAME = 'sentence-transformers/all-MiniLM-L6-v2'  # compact, good for on-device embedding conversion

if __name__ == '__main__':
    if not CORPUS.exists():
        raise SystemExit('Run prepare_corpus.py first to create data/corpus.jsonl')

    passages = []
    with CORPUS.open('r', encoding='utf-8') as fh:
        for line in fh:
            obj = json.loads(line)
            passages.append(obj)

    texts = [p['text'] for p in passages]
    ids = [p.get('id') for p in passages]
    titles = [p.get('title') for p in passages]

    print(f'Loading model {MODEL_NAME} ...')
    model = SentenceTransformer(MODEL_NAME)
    print('Computing embeddings...')
    emb = model.encode(texts, show_progress_bar=True, convert_to_numpy=True)
    emb = np.array(emb, dtype=np.float32)
    print('Saving embeddings...')
    np.save(OUT_DIR / 'embeddings.npy', emb)

    # Also save a compact JSON array for Android assets (embeddings.json)
    emb_list = emb.tolist()
    with (OUT_DIR / 'embeddings.json').open('w', encoding='utf-8') as fh:
        json.dump(emb_list, fh, ensure_ascii=False)

    meta = [{'id': ids[i], 'title': titles[i], 'index': i} for i in range(len(ids))]
    with (OUT_DIR / 'embeddings_meta.json').open('w', encoding='utf-8') as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=2)

    # copy passages to data/passages.jsonl (already corpus)
    (OUT_DIR / 'passages.jsonl').write_text('\n'.join(json.dumps(p, ensure_ascii=False) for p in passages), encoding='utf-8')

    print(f"Done. Saved {emb.shape} embeddings to data/embeddings.npy, embeddings.json and metadata to embeddings_meta.json")
