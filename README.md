# Atomic — Periodic Table

An offline chemistry reference for Android: all 118 elements in 12 languages, a
zoomable periodic table, 12 reference tables, four calculators, a quiz engine
with XP and streaks, and a natural-language assistant that answers chemistry
questions **entirely on-device**.

[**📖 Documentation**](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/) ·
[Google Play](https://play.google.com/store/apps/details?id=com.jlindemann.science)

---

## Features

- **Periodic table** — zoom and pan the real grid, recolour by any property to
  see periodic trends, search by name, symbol or number in any supported
  language
- **Element detail** — ~80 fields per element plus six purpose-built
  visualisations: electron shells, crystal structure, NFPA 704 diamond,
  ionisation series, isotope decay, abundance
- **12 reference tables** — isotopes, pH, electrochemical series, equations,
  ions, solubility, Poisson ratios, the nuclide chart, physical constants,
  geology, emission spectra, alloys
- **Calculators** — molar mass, unit conversion, ideal gas law, reaction
  balancing
- **Learning games** — procedurally generated quizzes, flashcards, lives, XP,
  streaks, achievements, exam mode
- **AI assistant** — ask in plain language, offline, in any of 12 languages
- **5 home-screen widgets** — including Element of the Day
- **Optional sync** — Google sign-in backs up progress and notes across devices

## The assistant is not an LLM

There is no language model, no API key, and no network call in the answer path.
The assistant parses your question, builds a **typed query plan**, and executes
it against the element data shipped inside the app.

Every number in an answer traces back to a specific field of a specific element.
Nothing is generated — which is why it declines rather than guessing when a
question falls outside what the data supports, and why it works in airplane mode.

It handles property lookups, unit conversion, comparisons, superlatives,
filtered lists, aggregates, isotopes, formula masses, mole conversions,
multi-turn follow-ups and compound questions, in 12 languages, with 38 test
classes behind it.

Read how it works: [AI Agent documentation](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/ai).

## Documentation

| Section | Contents |
|:--|:--|
| [User Guide](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/user-guide) | Every screen and feature |
| [Developer Guide](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/development) | Architecture, data model, persistence, build |
| [AI Agent](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/ai) | NLU, planning, execution, retrieval, cards |
| [Data Pipeline](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/data-pipeline) | The Python tooling in `scripts/` |

Source for the site is in [`docs/`](docs/).

## Building

Requires Android Studio Hedgehog or later, JDK 17, and Android SDK 36.

```bash
git clone https://github.com/JLindemann42/Atomic-Periodic-Table.Android.git
```

You will need a Firebase project and `app/google-services.json` — the build
applies the Google Services plugin and fails at configuration time without it.
Full instructions: [Getting started](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/development/getting-started).

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Note the build ships `arm64-v8a` only, so x86_64 emulator images will not run
the app.

## Tech stack

Kotlin · minSdk 24 · targetSdk 36 · Material 3 · Firebase (Auth, Firestore,
Analytics) · Play Billing · Coroutines. No DI framework, no ViewModels, no Room —
see [Architecture](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/development/architecture)
for why.

## Contributing

See [Contributing](https://jlindemann42.github.io/Atomic-Periodic-Table.Android/development/contributing).

Particularly welcome:

- **Native-speaker review of translations** — most element descriptions are
  machine-assisted and have not been checked by a human
- **Afrikaans element descriptions** — the one language still substantially
  incomplete
- **Tablet layouts** — only three screens have `sw600dp` variants today
- **Tests for the sync merge logic** — currently untested, and a bug there costs
  users their progress

## Data sources

Element data is compiled from IUPAC, NIST, the CRC Handbook of Chemistry and
Physics, and WebElements. Fields with no authoritative published value carry the
`"---"` sentinel and render as "no data" rather than a guess.

Full attributions are on the Sources page in the app.
