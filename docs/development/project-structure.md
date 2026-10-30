---
title: Project structure
parent: Developer Guide
nav_order: 3
---

# Project structure

Single Gradle module (`:app`), root project name `science`, package root
`com.jlindemann.science`. Around 230 Kotlin source files.

## Repository layout

```
.
├── app/
│   ├── build.gradle              # all dependency and SDK config
│   ├── google-services.json      # Firebase config (required to build)
│   ├── lint-baseline.xml
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/           # elements_{lang}.json × 12
│       │   ├── java/com/jlindemann/science/
│       │   └── res/              # layouts, 17 locale variants, themes
│       ├── test/                 # JVM unit tests (~44 classes)
│       └── androidTest/          # stock example only
├── docs/                         # this documentation site
├── scripts/                      # Python data & translation tooling
└── build.gradle, settings.gradle, gradle.properties
```

## Source packages

| Package | Files | What lives there |
|:--|--:|:--|
| `activities` | 40 | All 37 activities, plus `BaseActivity`. Sub-packages `settings/`, `tables/`, `tools/` |
| `adapter` | 23 | One RecyclerView adapter per list-driven screen, plus the chat adapters |
| `ai` | 49 | The on-device assistant — see [AI Agent](../ai) |
| `animations` | 2 | `Anim` (fade helpers), `TitleBarAnimator` (collapsing title bar) |
| `auth` | 1 | `AuthManager` — Firebase Auth + Google Sign-In wrapper |
| `classes` | 2 | `Quadruple` (a 4-tuple), `ZoomLayout` |
| `extensions` | 6 | `TableExtension`, `InfoExtension`, `ContextExtension`, and the crystal-structure geometry (`CrystalStructureView`, `CrystalStructures`, `CrystalMath`) |
| `fragments` | 6 | The five bottom-nav fragments plus `BaseFragment` |
| `model` | 35 | Data classes and their hardcoded `*Model` tables |
| `preferences` | 21 | One class (sometimes several) per SharedPreferences file |
| `quiz` | 8 | Question generators for the learning games |
| `settings` | 1 | `ExperimentalActivity` — a stray, historically placed here rather than in `activities/settings/` |
| `sync` | 2 | `ProgressSyncManager`, `NotesSyncManager` |
| `utils` | 24 | Grab bag — see below |
| `views` | 10 | Custom canvas-drawn views |
| `widgets` | 2 | `InfoPanel`, `IsotopeItem` — small helper models, **not** the app widgets |

The five app-widget providers (`ElementOfTheDayWidget`, `ScienceDailyWidget`,
`AIQuickAskWidget`, `ElementQuickNavWidget`, `ShortCommandWidget`) sit at the
package root, not in `widgets/`. That naming collision is the single most
confusing thing about the layout.

## The `activities` sub-packages

```
activities/
├── BaseActivity.kt          # root of nearly everything
├── MainActivity.kt          # the shell
├── SplashActivity.kt        # LAUNCHER
├── IntroductionActivity.kt  # first-run
├── ElementInfoActivity.kt   # element detail
├── SettingsActivity.kt
├── TableActivity.kt, ToolsActivity.kt, UserActivity.kt
├── SolubilityActivity.kt, IsotopesActivityExperimental.kt
├── settings/                # About, Credits, Licenses, Sources, Submit,
│                            # Favorites, Order, Unit, Pro
├── tables/                  # ph, Electrode, Equations, Ion, Poisson, Nuclide,
│                            # Constants, Geology, Emission, Alloy, Dictionary
└── tools/                   # Calculator, UnitConversion, IdealGas,
                             # ChemicalReactions, FlashCard, LearningGames,
                             # StreakReminderReceiver
```

## What's in `utils`

Bigger than it should be, and worth breaking down:

| Area | Files |
|:--|:--|
| AI chat UI | `AiChatPanelController` |
| Element data | `ElementDataLoader` |
| Learning games | `XpManager`, `StreakManager`, `LivesManager`, `LivesRefillWorker`, `ExamManager`, `FlashcardCatalog`, `GameResultItem` |
| Billing / PRO | `BillingManager`, `ProPlusTimeUtil`, `ProUpgradeDialogFragment` |
| Localisation | `LocaleUtil` |
| Notifications | `NotificationHelper`, `StreakReminderReceiver` |
| Analytics | `AnalyticsHelper` |
| UI helpers | `Utils`, `ToastUtil`, `TabUtil`, `UnifiedTitleBarController`, `EffectOverlayAnimator`, `RenderScriptBlur`, `RenderScriptProvider` |
| Misc | `Pasteur` (logging) |

Note `BillingManager` is in the `utils/` **directory** but declares
`package com.jlindemann.science.billing` — the file path and the package
disagree. Kotlin allows this; it will surprise you when searching.

## The `model` package convention

Most models come in pairs: a data class `X.kt` and an object `XModel.kt` holding
a hardcoded table of instances.

```kotlin
// model/Element.kt          — the data class
// model/ElementModel.kt     — object with all 118 elements as Triples
```

Pairs following this pattern: `Element`, `Ion`, `Constants`, `Geology`,
`Tables`, `Achievement`, `Statistics`, `Series`, `Equation`, `Poisson`,
`Indicator`, `Order`. Standalone: `Dictionary`, `TableItem`, `ToolItem`,
`ChatMessage`, `ChatSession`, `HoverFilterMenu`, `UnitDefinition`,
`UnitConversionFavorite`.

`ElementModel` is the one to know: it holds all 118 elements as
`Triple(elementKey, symbol, arrayOf(number, electronegativity, isotopeCount))`
and its `getList(context)` overlays localised display names pulled from the JSON.

## Resources

```
res/
├── layout/           # phone layouts
├── layout-sw600dp/   # tablet variants — only activity_tables,
│                     # activity_tools, isotope_panel
├── layout-v31/       # Material You widget layouts (Android 12+)
├── values/           # default (English) strings, styles, themes
├── values-{locale}/  # 16 locale variants
├── values-night/     # dark-mode overrides
├── values-v23…v31/   # API-level style overrides
├── drawable/, drawable-v24/
└── xml/              # app widget provider metadata
```

See [Localisation](localization) for the locale list.

## Test layout

```
app/src/test/java/com/jlindemann/science/
├── ai/           # 38 classes — engine, corpus, retrieval, data, cards
├── quiz/         # 4 classes — the generators
├── utils/        # 1 class — ProPlusTimeUtil
└── ExampleUnitTest.kt
```

See [Testing](testing) for what that does and does not cover.
