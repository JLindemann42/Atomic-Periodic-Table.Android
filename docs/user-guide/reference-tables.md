---
title: Reference tables
parent: User Guide
nav_order: 3
---

# Reference tables

**Class:** `fragments/TablesFragment.kt`, with one activity per table under
`activities/tables/`

Twelve standalone data tables, listed in a single scrollable list. The list is
drag-reorderable — tap the edit icon, then long-press and drag. Your order is
saved in `preferences/TableOrderPreference.kt`. A "most used" chip row at the
top tracks which tables you open most often.

## The tables

| Table | Activity | Tier |
|:--|:--|:--|
| Isotopes | `IsotopesActivityExperimental` | Free |
| pH and indicators | `tables/phActivity` | Free |
| Electrochemical series | `tables/ElectrodeActivity` | Free |
| Equations | `tables/EquationsActivity` | Free |
| Ionisation / ion charges | `tables/IonActivity` | Free |
| Solubility | `SolubilityActivity` | Free |
| Poisson's ratio | `tables/PoissonActivity` | **PRO** |
| Nuclide chart | `tables/NuclideActivity` | **PRO** |
| Physical constants | `tables/ConstantsActivity` | **PRO** |
| Geology | `tables/GeologyActivity` | **PRO** |
| Emission spectra | `tables/EmissionActivity` | **PRO** |
| Alloys | `tables/AlloyActivity` | **PRO** |

Tapping a PRO table without PRO sends you to the upgrade page rather than
showing a partial or teaser view. The gate is a single check —
`ProVersion(context).getValue() != 100` — applied in
`TablesFragment.onTableItemClick`.

The chemistry **dictionary** (`tables/DictionaryActivity`) is also a reference
table by nature, but it is listed under [Tools](tools) instead.

## What each one contains

**Isotopes** — every isotope in the dataset with mass number, half-life and
decay mode, filterable by element.

**pH and indicators** — common acid–base indicators with their transition
ranges and colour changes.

**Electrochemical series** — standard electrode potentials for half-reactions,
ordered by potential.

**Equations** — a reference sheet of chemistry and physics equations with their
variables identified.

**Ionisation / ion charges** — common cations and anions with their charges and
names, split into the two families.

**Solubility** — the standard solubility rules as a cation × anion grid.

**Poisson's ratio** — elastic constants per element, with a visual band showing
where each element sits in the overall range.

**Nuclide chart** — the chart of nuclides: every known nuclide plotted by proton
and neutron number, coloured by decay mode. This is the densest table in the app.

**Physical constants** — CODATA fundamental constants with values, units and
uncertainties.

**Geology** — mineral and rock-forming data, including crustal abundances.

**Emission spectra** — the visible emission lines for each element. Spectrum
images are fetched from the network, so this is the one table that needs a
connection to render fully.

**Alloys** — common alloys with their constituent elements and proportions.

## Asking the assistant instead

Every one of these tables is also indexed by the [AI assistant](ai-assistant),
so "what is the standard potential of the zinc half cell" or "what is the
Planck constant" returns the answer directly, with a chip that deep-links into
the corresponding table.
