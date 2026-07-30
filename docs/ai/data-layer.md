---
title: Data layer
parent: AI Agent
nav_order: 4
---

# Data layer

Package: `ai/data/` — eleven files. This layer turns the authored strings in
`assets/elements_{lang}.json` into a typed, queryable index.

## `FieldRegistry.kt`

The catalogue of everything the assistant can answer about — around 80
`FieldSpec` entries.

```kotlin
add(spec("young_modulus", "young_modulus",
         FieldKind.NUMERIC, FieldCategory.MECHANICAL,
         R.string.element_young_modulus,
         Dimension.PRESSURE, "GPa",
         allowsRange = true, tier = Tier.PRO))
```

Each spec carries:

| Property | Purpose |
|:--|:--|
| `id` | Canonical field id used throughout the engine |
| JSON key(s) | Where to read it — sometimes more than one |
| `kind` | `NUMERIC`, `ENUM`, `TEXT`, `STRUCT`, `ID`, `LINK` |
| `category` | `IDENTITY`, `ATOMIC`, `THERMO`, `ELECTROMAGNETIC`, `MECHANICAL`, `CRYSTAL`, `NUCLEAR`, `ABUNDANCE`, `SAFETY`, `IDS` |
| `labelRes` | The app's localised label — also the query alias |
| `dimension` + canonical unit | Enables unit conversion on request |
| `deepLink` | Which screen a citation chip opens |
| `tier` | `FREE`, `PRO`, `PRO_PLUS` |
| `allowsRange` | Whether `"2.1–2.4"` is a legal value |
| ordinal range | For banked fields such as successive ionisation energies |

**Four fields have no JSON key at all** and are derived: `period`,
`group_number`, `valence_electrons` and `synthetic`. They are computed from
atomic number and configuration.

The registry is the single point of extension. Adding a spec makes a field
queryable, unit-convertible, deep-linkable and tier-gated in one step — see
[Extending](extending).

## `Quantity.kt` — modelling values

```kotlin
sealed class FieldValue {
    object Missing : FieldValue()
    data class Num(val quantity: Quantity) : FieldValue()
    data class Text(...) : FieldValue()
    data class Enum(...) : FieldValue()
    data class Struct(...) : FieldValue()
    object Trace : FieldValue()
}
```

Absence is a **type**, not a sentinel string or a null. This propagates all the
way to `ExecutionResult.NoData`, so the composer can say "we don't have a
published value" rather than printing an empty string or the literal `---`.

`Quantity` carries more than a number:

| Field | Why |
|:--|:--|
| `value` | The number |
| `high` | Upper bound, for range-valued fields |
| `unit` | Canonical unit |
| `display` | The **verbatim authored string** |
| `approximate` | Whether the source hedged |
| `note` | Any qualifier attached in the source |
| `unitAuthored` | The unit as written, before canonicalisation |

Keeping `display` alongside the parsed value means an answer can quote the source
formatting exactly when no conversion was requested, and show a converted number
only when the user asked for one.

## `ValueParser.kt`

Turns raw JSON scalars into `FieldValue`. The element data is authored by hand
from multiple reference sources, so the parser has to cope with a lot of forms:

| Input | Handling |
|:--|:--|
| `"---"`, `"N/A"`, `""`, `null` | → `Missing` |
| `"1.00784 (u)"` | Value + unit in parentheses |
| `"0.12 (H2) (kJ/mol)"` | Allotrope-form prefix stripped |
| `"1310 (m/s) [27°C]"` | Bracketed condition captured as `note` |
| `"2.8 × 10^10"` | Scientific notation |
| `"2.1–2.4"` | Range → `value` + `high`, only where `allowsRange` |
| `"1.5(3)"` | Glued uncertainty |
| `"~200"` | → `approximate = true` |

Ranges are only accepted on fields whose spec sets `allowsRange`. Elsewhere a
dash is a data error, not a range, and parsing it as one would silently corrupt
comparisons.

`ValueParserTest` is the test to read if you are extending this.

## `UnitConverter.kt`

Two mechanisms, deliberately separate:

**Multiplicative** — length, density, mass, energy, pressure, velocity,
resistivity, molar volume, concentration, area. A factor table keyed by
dimension.

**Affine** — temperature. Kelvin, Celsius and Fahrenheit need an offset as well
as a scale, so folding them into the multiplicative table would be wrong. They
get their own converter.

`canonical()` normalises unit spelling through NFKC, so `"cm³"`, `"cm^3"` and
`"cm3"` resolve to the same unit.

This is what makes "melting point of iron in Fahrenheit" work: the operator
extractor picks up the target unit, and the composer converts from the field's
canonical unit before rendering.

## `KnowledgeStore.kt` — the index

Built once per process by `KnowledgeStore.get()`.

The important optimisation: it parses **English only**. Of roughly 124 fields,
only seven differ between language files — `element`, `element_group`,
`description`, `element_appearance`, `element_phase`, `electrical_type`,
`magnetic_type`. A `LocalizedView` overlay supplies those seven per language.

So switching language does not reparse a 32,000-line JSON file; it swaps a
seven-field overlay.

If you add a **localised** element field, you must add it to that overlay list or
it will show English in every language.

## `DatasetIndex.kt`

Flattens the app's non-element data — the dictionary, equations, constants,
Poisson ratios, geology, ions, indicators, the electrode series, solubility, the
unit catalogue and alloys — into one uniform `DatasetRow` list.

This is what lets "what is the Planck constant" be answered by the same engine
that answers "density of gold".

**English only.** These tables are not translated. A foreign-language query is
glossed to English for searching via `Lexicon.DATASET_GLOSS`, and the answer is
framed in the user's language, but the row content stays English. This is the
subsystem's most visible localisation gap.

## `AssetElementSource.kt` and `ElementSource.kt`

`ElementSource` is the interface; `AssetElementSource` is the only implementation
that touches JSON.

It reads `assets/elements_{lang}.json` through the app-wide
`utils/ElementDataLoader` and converts immediately to plain Kotlin maps
(`ElementRow = Map<String, Any?>`).

**This isolation is deliberate.** Under JVM unit tests `org.json` is a stub whose
methods throw, so confining it to one class is what lets the other ten files in
this package — and the whole engine above them — be tested without Android.
Tests substitute a fixture source (`ai/data/TestAssets.kt`).

## `IsotopeParser.kt`

Reassembles the numbered flat keys (`iso_1`, `iso_mass_1`, `iso_half_1`,
`decay_type_1`, `iso_Z_1`, `iso_N_1`, `iso_A_1`, then `_2`, `_3`, …) into
structured isotope records. See the
[element data model](../development/data-model#isotopes).

Half-lives are parsed across their full range of authored units, from
microseconds to billions of years, which is what makes "which is more stable,
uranium-235 or uranium-238" comparable.

## `SeriesCanon.kt`

Canonical definitions of element families — alkali metals, alkaline earths,
transition metals, halogens, noble gases, lanthanides, actinides, metalloids.
Maps a `SeriesId` to its member elements, and backs both `FILTER_LIST` queries
and the quiz's classification generator.

## `ChemistryMath.kt`

Formula parsing and stoichiometry: `parseFormula` handles nested parentheses and
hydrates, and molar mass and mole conversions build on it.

Note this is **not** the same code as `ai/MolarMassCalculator.kt`, which serves
the legacy handler path. Two implementations exist; this is the one the
structured engine uses.
