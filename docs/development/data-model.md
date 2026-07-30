---
title: Element data model
parent: Developer Guide
nav_order: 5
---

# Element data model

All element data ships in the APK. There is no backend, no database and no
build-time generation step — the JSON files are committed and read directly.

## Files

```
app/src/main/assets/
├── elements_af.json    elements_fr.json    elements_pt.json
├── elements_de.json    elements_hi.json    elements_sv.json
├── elements_en.json    elements_it.json    elements_ur.json
├── elements_es.json    elements_fil.json   elements_zh.json
```

Twelve files, one per language, each around 32,000 lines. `elements_en.json` is
the reference; the others are structurally identical and differ only in the
handful of localised fields.

## Top-level shape

A flat object keyed by the **element key** — the lowercase English element name.

```json
{
  "hydrogen": { "element": "Hydrogen", "short": "H", ... },
  "helium":   { "element": "Helium",   "short": "He", ... },
  ...
}
```

118 keys per file. The element key is the join key used everywhere — in
`ElementSendAndLoad`, in widget intents, in AI deep links — and it does **not**
change with the display language. `elements_de.json` still keys hydrogen under
`"hydrogen"`, with `"element": "Wasserstoff"` inside.

## Field inventory

Each element object is flat, with all values as strings except two structured
fields. There are **418 distinct keys** across the English file; 61 appear on
every element, and the remainder are either optional or part of the numbered
isotope blocks.

An element carries roughly **80 non-isotope fields**, grouped as follows.

### Identity and provenance

| Key | Example |
|:--|:--|
| `element` | `"Hydrogen"` — localised display name |
| `short` | `"H"` |
| `element_atomic_number` | `"1"` |
| `element_group` | `"Other Nonmetals"` |
| `element_block` | `"s - block"` |
| `element_appearance` | `"Colorless Gas"` |
| `element_year` | `"1766"` |
| `element_discovered_name` | `"Henry Cavendish"` |
| `description` | Prose paragraph — localised |
| `wikilink`, `link`, `element_model`, `spectral_img` | External URLs |
| `element_code` | Firestore-style document id, unused for local lookup |

### Atomic

`element_electrons`, `element_protons`, `element_neutron_common`,
`element_shells_electrons` (`"K1 L0 M0 N0 O0 P0 Q0 R0"`),
`element_electron_config` (`"1s^1"`), `element_atomicmass`,
`element_electronegativty` *(sic — the misspelling is in the data)*,
`electronegativity_allen`, `element_ionization_energy1`,
`element_ionization_energy2`, `electron_affinity`, `element_ion_charge`,
`oxidation_state_neg`, `oxidation_state_pos`, `element_atomic_radius`,
`element_atomic_radius_e`, `element_covalent_radius`, `element_van_der_waals`,
`work_function`.

### Thermodynamic

`element_density`, `element_phase`, melting and boiling points in all three
scales (`element_{melting,boiling}_{kelvin,celsius,fahrenheit}`),
`element_fusion_heat`, `element_vaporization_heat`,
`element_specific_heat_capacity`, `molar_heat_capacity`, `molar_volume`,
`thermal_conductivity`, `thermal_expansion`, `debye_temperature`.

### Electromagnetic

`electrical_type`, `element_electrical_conductivity`, `resistivity`,
`resistivity_mult`, `magnetic_type`, `element_magnetic_type`,
`element_volume_magnetic_susceptibility`, `curie_point`, `neel_point`,
`superconducting_point`, `refractive_index`.

### Mechanical

`mohs_hardness`, `vickers_hardness`, `brinell_hardness`,
`speed_of_sound_solid`, `speed_of_sound_liquid`, `speed_of_sound_gas`, plus the
elastic moduli and Poisson's ratio.

### Crystal

`crystal_structure` and `element_crystal_structure` (duplicated),
`space_group_name` (`"P6₃/mmc"`), `space_group_number`, `lattice_constants`.

### Abundance

`urban_soils`, `sea_water`, `crustal_rocks`, `sun`, `solar_system`,
`meteorites`, `human_body`.

### Nuclear and safety

`radioactive`, `neutron_cross_sectional`, plus the NFPA ratings.

### The two structured fields

Everything is a string except these, which are nested objects:

```json
"lattice_constants": { "a": "3.75 Å", "c": "6.12 Å" },
"debye_temperature": { "low_temperature_limit": "104 K",
                       "room_temperature": "90 K" }
```

## Isotopes

Isotopes are stored as **numbered flat keys**, not as an array — a repeating
seven-field block suffixed `_1`, `_2`, … up to that element's isotope count:

| Key | Meaning |
|:--|:--|
| `iso_N` | Isotope name/label |
| `iso_mass_N` | Isotopic mass |
| `iso_half_N` | Half-life |
| `decay_type_N` | Decay mode |
| `iso_Z_N` | Proton number |
| `iso_N_N` | Neutron number |
| `iso_A_N` | Mass number |

Carbon has 190 keys in total, most of them isotope blocks. This is why the
distinct-key count across the file (418) is so much larger than the per-element
field count (~80).

Parsing is handled by `ai/data/IsotopeParser.kt`.

## Value conventions

Values are authored strings, not numbers, and carry their formatting. Anything
consuming them has to parse.

| Convention | Example |
|:--|:--|
| Unit in parentheses | `"1.00784 (u)"`, `"53 (pm)"` |
| No-data sentinel | `"---"` |
| Also seen for absent | `""`, `"N/A"`, JSON `null` |
| Scientific notation | `"2.8 × 10^10"` |
| Allotrope qualifier | `"0.12 (H2) (kJ/mol)"` |
| Condition qualifier | `"1310 (m/s) [27°C]"` |
| Superscripts in units | `"11.42 cm³/mol"` |

**`"---"` is load-bearing.** It means "no authoritative published value exists",
and the UI renders it as an explicit no-data state rather than hiding the row.
Do not normalise it to `null` or `""`.

The AI engine's `ai/data/ValueParser.kt` handles all of these forms and turns
them into a typed `FieldValue` — see the [AI data layer](../ai/data-layer).

## Loading at runtime

`utils/ElementDataLoader.kt` is the single entry point.

```kotlin
// One element, in the app's current language
ElementDataLoader.loadElementData(context, "tungsten", language)

// All elements — used by the AI knowledge index and quiz generators
ElementDataLoader.getAllElements(assets, language)

// Which languages are available (scans assets for elements_*.json)
ElementDataLoader.getAvailableLanguages(assets)

// The user's chosen language, falling back to the system locale
ElementDataLoader.getAppLanguage(context)
```

Two behaviours worth knowing:

**Per-language caching in a `ConcurrentHashMap`.** Parsed `JSONObject`s are
cached. The map is concurrent rather than a plain `HashMap` because it is read
from the IO dispatcher during AI engine initialisation and from the main thread
by the UI at the same time.

**Fallback to English.** If a language file is missing, or an element key is
absent from it, the loader falls back to `elements_en.json`. A partially
translated language degrades field by field rather than failing.

## Which fields are actually localised

Only seven fields differ between language files:

`element`, `element_group`, `description`, `element_appearance`,
`element_phase`, `electrical_type`, `magnetic_type`

Everything else — every number, every unit, every isotope — is identical across
all twelve files. The AI engine exploits this directly: `KnowledgeStore` parses
the English file once and overlays just those seven fields per language through
a cheap `LocalizedView`, rather than parsing twelve full indexes.

## `ElementModel` — the hardcoded table

`model/ElementModel.kt` holds all 118 elements as
`Triple(elementKey, symbol, arrayOf(atomicNumber, electronegativity, isotopeCount))`.

This duplicates data that is also in the JSON. It exists so the periodic table
grid can be built synchronously without touching assets.
`ElementModel.getList(context)` overlays the localised display name from the
JSON when a `Context` is available, and capitalises the English key when it is
not.

If you add a field that the grid needs, consider whether it belongs here or
whether the grid should read the JSON.

## Editing the data

Do not hand-edit twelve files. The Python tooling in `scripts/` exists for
exactly this — see the [Data Pipeline](../data-pipeline) section, and run
`verify_element_jsons.py` afterwards.
