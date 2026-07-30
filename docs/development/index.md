---
title: Developer Guide
nav_order: 3
has_children: true
permalink: /development/
---

# Developer Guide

How the codebase is put together, and why.

## Start here

New to the repo? Read these three in order:

1. **[Getting started](getting-started)** — clone, configure Firebase, build,
   run the tests.
2. **[Architecture](architecture)** — the big structural decisions, including
   several that are unconventional.
3. **[Project structure](project-structure)** — the package-by-package map.

## Reference

| Page | Covers |
|:--|:--|
| [UI patterns](ui-patterns) | Activity inheritance, insets, back handling, adapters, custom views, theming |
| [Element data model](data-model) | The `elements_{lang}.json` schema and how it loads |
| [Persistence and sync](persistence-and-sync) | SharedPreferences wrappers, Firebase Auth, Firestore sync |
| [Billing](billing) | Play Billing, the three SKUs, how gating works |
| [Localisation](localization) | 12 languages, runtime locale switching, the RTL gap |
| [Testing](testing) | What is covered, what is not, and the seam that makes the AI engine unit-testable |
| [Build and release](build-and-release) | SDK levels, dependencies, ABI filtering, the release-signing gap |
| [Contributing](contributing) | How to add a field, a language, a table, a card |
| [Project history](history) | Consolidated record of past work — element data passes, the translation campaign, the abandoned embedding experiment |

The [AI Agent](../ai) section is documented separately because it is large enough
to warrant it — roughly 40 source files and 40 test files.

## Things worth knowing before you read the code

Some conventions in this codebase will look wrong until you know they are
deliberate:

**`getValue() == 100` means "owned".** PRO entitlement is stored as an `Int`
where `1` is not-owned and `100` is owned. You will see this comparison
everywhere.

**Element keys are lowercase English names.** `"tungsten"`, not `"W"` and not
`74`. This is the join key across the JSON files, preferences, widgets and the
AI engine, and it does not change with the display language.

**`"---"` is the no-data sentinel** in the element JSON. It is not a typo and
must not be replaced with `null` or `""` — `ai/data/ValueParser.kt` and the
element detail UI both key off it.

**Activities inherit shared behaviour rather than composing it.**
`BaseActivity` → `TableExtension` / `InfoExtension` is the app's substitute for
what would elsewhere be a ViewModel or a set of delegates.

**The AI engine avoids `org.json` on purpose.** Under JVM unit tests
`org.json` is a stub whose methods throw, so the engine's data layer works in
plain Kotlin maps and only one class (`AssetElementSource`) touches JSON at all.
The same reason is why `ChatActionCodec` and `ChatCardCodec` use a
control-character record format rather than JSON.
