---
title: Project history
parent: Developer Guide
nav_order: 11
---

# Project history

This page consolidates roughly twenty work-session reports that previously lived
as loose markdown files in the repository root. They were internal progress logs,
they overlapped heavily, and several contradicted each other.

**Everything here is historical record.** Where a report made a claim about
current state, that claim has been re-verified against the code and data as they
stand today, and the verified figure is the one given.

---

## Element data expansion

Two rounds of work extended the element dataset beyond the original field set.

**Filling placeholders.** Many fields across the 118 elements carried the `"---"`
no-data sentinel. A pass driven by `scripts/populate_element_data.py` filled
these from IUPAC, NIST, CRC Handbook and WebElements values, with per-element
lookup tables hardcoded in the script. Electrical type and magnetic type were the
largest gaps closed.

**Fourteen new properties.** A second round added fields across all 118 elements
and all 12 language files, explicitly aimed at matching the field coverage of
competing periodic table apps:

`thermal_conductivity` · `electron_affinity` · `molar_heat_capacity` ·
`molar_volume` · `thermal_expansion` · `electronegativity_allen` ·
`work_function` · `space_group_name` · `space_group_number` ·
`refractive_index` · `curie_point` · `neel_point` · `meteorites` ·
`human_body`

Several of these became [PRO fields](../user-guide/pro-tiers).

**Verification.** `scripts/verify_element_jsons.py` was written as the
authoritative checker — JSON validity, structural consistency against the
English reference, field presence, and translation completeness. It supersedes
the earlier ad-hoc `check_*.py` scripts.

---

## The translation campaign

A sustained effort through late 2025 to translate element descriptions and
interface strings. It produced a large number of one-off batch scripts (visible
in `scripts/` as `push_toward_60.py`, `push_60_plus.py`, `continue_toward_65.py`,
`push_toward_70.py`, `mega_batch_update.py` and similar) — each a successive pass
aimed at raising a completion percentage.

### The contradictory numbers

The historical reports disagreed:

| Report | Claim |
|:--|:--|
| `FINAL_TRANSLATION_STATUS.md` | 812/1,298 descriptions — 62.6% |
| `TRANSLATION_PROGRESS_UPDATE.md` | Same 62.6% figure |
| `TRANSLATION_FINAL_SUMMARY.md` | 947 strings, 76–99% per language |

They were measuring different things — element descriptions versus interface
strings — at different dates, without saying so.

### Verified current state

Element **descriptions**, measured directly against `elements_en.json`:

| Language | Descriptions translated |
|:--|--:|
| German | 118/118 (100%) |
| Spanish | 118/118 (100%) |
| French | 118/118 (100%) |
| Swedish | 118/118 (100%) |
| Urdu | 118/118 (100%) |
| Chinese | 118/118 (100%) |
| Filipino | 118/118 (100%) |
| Hindi | 117/118 (99%) |
| Italian | 117/118 (99%) |
| Portuguese | 116/118 (98%) |
| **Afrikaans** | **38/118 (32%)** |

So the 62.6% figure is long superseded — descriptions are essentially complete
in ten of eleven languages, with Afrikaans the single remaining gap.

Element **names** are harder to measure automatically, because many are
legitimately identical to English (helium, lithium, neon, argon, titanium in
several languages). A raw string comparison reports German at 35% and French at
45%, but much of that is correct-by-identity rather than untranslated. Use
`scripts/verify_element_jsons.py --detailed`, which applies better heuristics.

Interface string completeness varies by language and is checked with
`scripts/check_translations.py`.

### Machine translation

Much of this work was machine-assisted. `docs/ai/translation-review.md` tracks
Urdu queries flagged for native-speaker review. Chinese, Hindi and Urdu received
specific attention because their scripts exposed bugs in the AI engine's
tokenisation — see below.

---

## The abandoned embedding experiment

The AI assistant was originally built around on-device semantic search: a corpus
of passages extracted from the element data, embedded with a sentence
transformer, with cosine similarity at query time.

**It never worked.** The query embedder returned a hash-seeded pseudo-random
vector rather than a real embedding, so cosine scores hovered near zero and the
0.65 similarity threshold gating retrieval never fired. The path was effectively
dead in production while appearing to be the core of the system.

It was replaced by **Okapi BM25** lexical retrieval (`ai/retrieval/Bm25Index.kt`,
k1 = 1.2, b = 0.6), fused with exact entity matching in `HybridRetriever`. The
index is built at runtime from live app data — roughly 780 documents — rather
than from a shipped artifact. See [Retrieval](../ai/retrieval).

What this left behind, all now removed:

- `data/corpus.jsonl`, `data/passages.jsonl`, `data/embeddings.json`,
  `data/embeddings.npy`, `data/embeddings_meta.json` — about 19 MB of generated
  artifacts referenced by nothing in `app/src/main/java`
- A section in `scripts/README.md` documenting `prepare_corpus.py` and
  `build_embeddings.py` — **scripts that did not exist in the repository**
- `scripts/requirements.txt`, listing `sentence-transformers` and `numpy` for
  that pipeline alone
- 42 MB of per-language `assets/data/**` passages, deleted earlier when it was
  found that every passage file was byte-identical to the `description` field
  already present in `elements_{lang}.json`

There is no TensorFlow Lite model in the repository and no ML dependency in
`app/build.gradle`. The assistant has never shipped a neural model.

---

## The Indic-script tokenisation bug

Worth recording because the fix is load-bearing and looks arbitrary otherwise.

Text normalisation originally stripped all combining marks. For Latin scripts
that correctly folds diacritics. For Devanagari it destroys vowel signs, which
are combining marks that carry meaning — so Hindi queries normalised into
different words entirely, and matching failed silently rather than erroring.

The fix in `ai/retrieval/TextMatching.kt` strips combining marks **only after
Latin base letters**. Related handling: NFKC/NFD/NFC round-tripping for Urdu
(Arabic-script words decompose to a different character count), Han unigram *and*
bigram emission for dictionary-free Chinese segmentation, and Arabic letter-variant
folding. See [Retrieval](../ai/retrieval).

---

## Home-screen widgets

Five widgets were added, with the Element of the Day widget the most developed:
day-of-year element rotation, Material You dynamic colour on Android 12+ through
`layout-v31/` variants, localised content, and refresh on `DATE_CHANGED` /
`TIMEZONE_CHANGED` so it turns over at local midnight.

A security review at the time confirmed correct `PendingIntent` immutability
flags for Android 12+, no exported receivers beyond the necessary
`ShortCommandWidget`, and no sensitive data in widget content.

See [Home-screen widgets](../user-guide/widgets).

---

## Security reviews

Several reviews were run on data-only changes (element JSON translations and
data population). All passed. Their common findings:

- Changes were confined to data files; no executable code modified
- No new dependencies introduced
- No credentials, tokens or personal data in the data files
- No change to permissions or exported components

`app/lint-baseline.xml` carries one suppressed finding —
`UnspecifiedImmutableFlag` in `ShortCommandWidget.kt`.

---

## Superseded documents

These files were consolidated into this page and the rest of this site, then
removed from the repository root. They remain in git history.

<details markdown="block">
<summary>Full list</summary>

`ELEMENT_DATA_ENHANCEMENTS.md` · `ELEMENT_DATA_UPDATE_SUMMARY.md` ·
`ELEMENT_JSON_VERIFICATION_REPORT.md` · `ELEMENT_NAME_TRANSLATION_SUMMARY.md` ·
`ELEMENT_OF_THE_DAY_WIDGET.md` · `FINAL_TRANSLATION_STATUS.md` ·
`SECURITY_SUMMARY.md` · `SECURITY_SUMMARY_ELEMENT_DATA.md` ·
`TESTING_VALIDATION_REPORT.md` · `TRANSLATION_COMPLETION_GUIDE.md` ·
`TRANSLATION_COMPLETION_SUMMARY.md` · `TRANSLATION_FINAL_SUMMARY.md` ·
`TRANSLATION_GUIDE.md` · `TRANSLATION_IMPROVEMENTS_SUMMARY.md` ·
`TRANSLATION_PROGRESS_UPDATE.md` · `TRANSLATION_SESSION_SUMMARY.md` ·
`TRANSLATION_WORK_COMPLETED.md` · `TRANSLATION_WORK_SUMMARY.md` ·
`WIDGET_DESIGN.md` · `WIDGET_SECURITY_SUMMARY.md`

Plus the generated status artifacts: `element_json_verification.json`,
`element_json_verification_updated.json`, `translation_needs_report.json`,
`translation_progress_report.json`, `translation_status.json`,
`untranslated_strings.csv`.

</details>

Regenerate any of the status data with the scripts described in
[Data Pipeline](../data-pipeline) rather than relying on a stored snapshot.
