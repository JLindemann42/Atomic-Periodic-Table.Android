---
title: Tools
parent: User Guide
nav_order: 4
---

# Tools

**Class:** `fragments/ToolsFragment.kt`, with one activity per tool under
`activities/tools/`

Four calculators and reference utilities. Like the reference tables, the list is
drag-reorderable (saved in `preferences/ToolOrderPreference.kt`) and has a
"most used" chip row.

| Tool | Activity | Tier |
|:--|:--|:--|
| Molar mass calculator | `tools/CalculatorActivity` | Free |
| Unit converter | `tools/UnitConversionActivity` | Free |
| Ideal gas calculator | `tools/IdealGasCalculatorActivity` | **PRO+** (see note) |
| Chemistry dictionary | `tables/DictionaryActivity` | Free |

A fifth tool, the **chemical reaction balancer**
(`tools/ChemicalReactionsActivity`), exists in the codebase and is reachable,
but is not listed in the default tools list.

## Molar mass calculator

Enter a chemical formula and get its molar mass, with a per-element breakdown
showing how much each constituent contributes. The parser handles nested
parentheses and hydrates.

The same formula parsing is available through the [AI assistant](ai-assistant) —
asking "molar mass of Ca(OH)2" gives the same answer without leaving the chat.
Note that there are two separate implementations: `ai/MolarMassCalculator.kt`
serves the assistant's legacy handler path, and `ai/data/ChemistryMath.kt`
serves the structured engine.

## Unit converter

Converts between units within a dimension — length, mass, energy, pressure,
temperature, volume, and others. Conversions you use often can be pinned as
favourites (`model/UnitConversionFavorite`).

Temperature is handled separately from the other dimensions because it is an
affine conversion (offset plus scale) rather than a simple multiplication. The
assistant shares this distinction in `ai/data/UnitConverter.kt`.

## Ideal gas calculator

Solves *PV = nRT* for whichever variable you leave blank, with unit selection on
each input.

**On the PRO+ gate:** this tool is gated behind PRO+ only until a cutoff date
implemented in `utils/ProPlusTimeUtil.isBeforeJanuary2026()`. Past that date the
check `proPlusValue != 100 && isBeforeDeadline` evaluates false, so the tool is
free for everyone. That date has now passed, meaning the tool is currently
unlocked for all users regardless of tier — the gating code remains in place but
no longer has any effect.

## Chemistry dictionary

A searchable glossary of chemistry terms with definitions. Listed under Tools
rather than Tables because you use it by looking things up rather than by
scanning it. Definitions are also indexed by the assistant, so asking "what is
an isotope" answers from the same source.

## Chemical reaction balancer

Balances chemical equations — enter reactants and products, get the stoichiometric
coefficients.
