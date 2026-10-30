---
title: Element detail
parent: User Guide
nav_order: 2
---

# Element detail

**Class:** `activities/ElementInfoActivity.kt`, extending
`extensions/InfoExtension.kt`

The page you reach by tapping any element. It is the densest screen in the app —
roughly 80 data fields plus six purpose-built visualisations.

## How you get here

Every route into this screen goes through the same mechanism: the selected
element's key (its lowercase English name, e.g. `"tungsten"`) is written to
`preferences/ElementSendAndLoad.kt`, then `ElementInfoActivity` is started. This
is true whether you arrived from the periodic table, the search bar, the random
element button, a home-screen widget, or a deep link in an AI answer.

## Data sections

Fields are grouped by category. The same category taxonomy drives the AI
assistant's field registry, so the groupings match between the two.

| Section | Examples |
|:--|:--|
| **Identity** | Symbol, atomic number, group, period, block, category, appearance, discovery year and discoverer, CAS and EC numbers |
| **Atomic** | Atomic mass, electron configuration, shell occupancy, electronegativity, ionisation energies, electron affinity, atomic / covalent / van der Waals radii, oxidation states |
| **Thermodynamic** | Melting and boiling points (in K, °C and °F), density, heat of fusion, heat of vaporisation, specific heat capacity, phase at room temperature |
| **Electromagnetic** | Electrical type, resistivity, magnetic type, Curie point, Néel point, superconducting point, refractive index |
| **Mechanical** | Young's, bulk and shear moduli, Poisson's ratio, Mohs / Vickers / Brinell hardness, speed of sound in solid, liquid and gas |
| **Crystal** | Crystal system, space group name and number, lattice parameters |
| **Nuclear** | Isotope list with mass, half-life, decay mode, Z / N / A; neutron cross-section |
| **Abundance** | Abundance in the universe, the sun, meteorites, the Earth's crust, the oceans and the human body |
| **Safety** | NFPA 704 health, flammability and instability ratings |

Fields with no authoritative published value show as "no data" rather than
being hidden, so you can tell the difference between *the app doesn't have this*
and *this quantity does not exist for this element*.

Several of the mechanical, crystal, electromagnetic and safety fields are
[PRO features](pro-tiers) — they render as a locked card with an upgrade prompt
until PRO is owned.

## Visualisations

Six custom-drawn views, all rendered on-device from the element's own data:

**Electron shell diagram** (`views/ElectronShellView.kt`) — the Bohr-style
concentric shell model, with the electron count per shell drawn from
`element_shells_electrons`.

**Crystal structure** (`extensions/CrystalStructureView.kt`, with geometry in
`extensions/CrystalStructures.kt` and `extensions/CrystalMath.kt`) — a rotatable
unit cell for the element's crystal system.

**NFPA 704 diamond** (`views/NfpaDiamondBinder.kt`) — the standard four-quadrant
fire diamond, coloured by the health / flammability / instability ratings.

**Ionisation energy series** (`views/IonizationSeriesView.kt`) — successive
ionisation energies plotted as a series, which makes the jumps at closed-shell
boundaries visible.

**Isotope decay chart** (`views/IsotopeDecayView.kt`) — isotopes plotted by mass
and half-life, coloured by decay mode.

**Abundance bars** (`views/AbundanceBarsView.kt`) — abundance across the six
reservoirs on a log scale, since the values span many orders of magnitude.

## Notes

Each element has a free-text notes field. Notes are stored locally in
`preferences/NotesPreference.kt` as a single blob keyed by element, and for
signed-in PRO users they sync across devices — see
[Account and sync](account-sync).

## Favourites

The star toggles the element into your favourites list, reachable from
Settings → Favourites (`activities/settings/FavoritePageActivity.kt`).

## Asking about this element

The chat button opens the [AI assistant](ai-assistant) with this element already
in context, so follow-up questions like "how does its melting point compare to
molybdenum?" resolve without you naming it again.

## Reporting a data problem

The page has a *submit data issue* link that opens
`activities/settings/SubmitActivity.kt`, a form for reporting an incorrect or
missing value.
