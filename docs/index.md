---
title: Home
layout: home
nav_order: 1
description: "Documentation for Atomic — Periodic Table, an offline Android chemistry reference app."
permalink: /
---

# Atomic — Periodic Table
{: .fs-9 }

An offline chemistry reference for Android: all 118 elements in 12 languages, a
zoomable periodic table, 13 reference tables, four calculators, a quiz engine
with spaced repetition, and a natural-language assistant that answers chemistry
questions **entirely on-device**.
{: .fs-6 .fw-300 }

[User Guide](user-guide){: .btn .btn-primary .mr-2 }
[Developer Guide](development){: .btn .mr-2 }
[AI Agent](ai){: .btn }

---

## At a glance

| | |
|:--|:--|
| **Package** | `com.jlindemann.science` |
| **Version** | 5.0.1m (versionCode 224) |
| **Min / target SDK** | 24 (Android 7.0) / 36 |
| **Language** | Kotlin |
| **Elements** | 118, with isotope, physical, atomic and safety data |
| **Localisation** | 12 languages, 17 locale variants |
| **Assistant** | On-device, no LLM API, no network for answers |
| **Screens** | 37 activities, 5 home-screen widgets |

## What this documentation covers

<div class="code-example" markdown="1">

### [User Guide](user-guide)

What the app does, screen by screen — the periodic table, element detail pages,
reference tables, calculators, the learning games, the AI assistant, home-screen
widgets, account sync, settings, and what the PRO tiers unlock.

### [Developer Guide](development)

How the codebase is put together — the inheritance-based activity architecture,
the SharedPreferences persistence layer, the element-data model, Firebase sync,
billing, localisation, the test suite, and how to build and release.

### [AI Agent](ai)

The largest and most unusual subsystem: a typed query planner and executor over
structured element data, with BM25 retrieval as a fallback. Covers the NLU
layer, the data layer, retrieval, the chat card system, and the evaluation
corpus.

### [Data Pipeline](data-pipeline)

The Python tooling in `scripts/` that populates, translates and verifies the
element JSON files and the Android string resources.

</div>

## Design principles

The app makes a few deliberate architectural choices that are worth knowing up
front, because they explain a lot of the code you will read:

**Everything works offline.** All element data ships in the APK as
`assets/elements_{lang}.json`. There is no backend for content. The only
network calls in the whole app are Firebase (optional sign-in and progress
sync), a remote GIF for emission spectra, and Play Billing.

**The assistant is not an LLM.** It is a deterministic pipeline: entity and
field resolution, an operator extractor, a query planner producing a typed
plan, an executor reading structured values, and a composer rendering the
answer. Nothing is generated; every number in an answer traces back to a field
in the element JSON. See [How the assistant works](ai).

**No dependency injection, no ViewModels, no Room.** State lives in
SharedPreferences (one small wrapper class per preference file), data lives in
assets, and shared behaviour is provided by activity inheritance
(`BaseActivity` → `TableExtension` / `InfoExtension`). This is unusual for a
codebase of this size and is described honestly in
[Architecture](development/architecture).

## Where the data comes from

Element values are compiled from IUPAC, NIST, the CRC Handbook of Chemistry and
Physics, and WebElements. Fields with no authoritative value carry the sentinel
`"---"` rather than a guess, and the app renders those as "no data" rather than
hiding the row. The [element data model](development/data-model) page documents
the full schema.
