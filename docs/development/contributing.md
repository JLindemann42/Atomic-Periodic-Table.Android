---
title: Contributing
parent: Developer Guide
nav_order: 12
---

# Contributing

## Before you start

Read [Architecture](architecture) — several conventions in this codebase look
like mistakes until you know they are deliberate (`getValue() == 100`, the
`"---"` sentinel, activity inheritance instead of composition).

Run the tests first, so you know they were green before your change:

```bash
./gradlew :app:testDebugUnitTest
```

## Branches and commits

Branch from `master`. Name branches for what they do — `fix/urdu-tokenisation`,
`feat/tablet-element-layout`.

Keep data changes and code changes in separate commits. A translation pass
touching twelve 32,000-line JSON files should not share a commit with a Kotlin
change, or neither is reviewable.

## Recipes

### Add a field to the element data

1. Add the key to all twelve `app/src/main/assets/elements_*.json` files. Use a
   script — see [Data Pipeline](../data-pipeline) — not twelve manual edits.
   Use `"---"` where no authoritative value exists.
2. Register it in `ai/data/FieldRegistry.kt`:
   ```kotlin
   add(spec("thermal_conductivity", "thermal_conductivity",
            FieldKind.NUMERIC, FieldCategory.THERMO,
            R.string.thermal_conductivity_colon,
            Dimension.THERMAL_CONDUCTIVITY, "W/(m·K)"))
   ```
   Set `tier = Tier.PRO` if it should be gated.
3. Add `R.string.…` label in `values/strings.xml` **and every
   `values-{locale}/strings.xml`**. `StringCoverageTest` fails otherwise, and it
   is right to.
4. Display it on the element page in `extensions/InfoExtension.kt`.
5. Run `./gradlew :app:testDebugUnitTest` — `PropertyCoverageTest` and
   `StringCoverageTest` verify the field is answerable and labelled.
6. Verify the data with `python3 scripts/verify_element_jsons.py`.

The field becomes queryable by the assistant automatically once it is in the
registry — the label you added in step 3 doubles as its query alias.

### Add a language

See [Localisation](localization#adding-a-language). Short version: string
resources, an element JSON file, the settings picker, then `ai/nlu/Lexicon.kt`
if you want assistant support. The first three make the app fully localised; the
fourth is most of the work.

### Add a reference table

1. Create the activity under `activities/tables/`, extending `BaseActivity`.
2. Add the model pair in `model/` (`X.kt` + `XModel.kt`).
3. Add an adapter in `adapter/`.
4. Add a `TableItem` to `TablesFragment.getTableItems()` with a unique
   three-letter id, and map that id in `onTableItemClick`.
5. Declare the activity in `AndroidManifest.xml`.
6. Add strings for title and description in every locale.
7. To make it queryable, add it to `ai/data/DatasetIndex.kt` and give it a
   `DeepLinkTarget` in `ai/compose/DeepLinkNavigator.kt`.

The three-letter id is what gets persisted in `TableOrderPreference`, so it must
be unique and stable — changing it resets users' saved ordering for that entry.

### Add an AI chat card

1. Add a value to `ChatCardKind` in `ai/cards/ChatCard.kt`.
2. Write a reducer that returns `null` when the data cannot support a meaningful
   visual — suppressing the card is the correct outcome, not drawing an empty one.
3. Map it in `ai/cards/CardSelector.kt` by `fieldId` or `FieldCategory`.
4. Add the view and bind it in `adapter/ChatCardBinder.kt`. Re-derive values
   from `KnowledgeStore` at bind time; do not serialise numbers onto the card.
5. If it needs the network, respect `ChatCardPolicy.allowNetworkCards`.
6. `CardReachabilityTest` will fail until some query can actually produce it.

See [Chat cards](../ai/cards).

### Add an overlay panel to MainActivity

Add it to the back-handling priority chain in `MainActivity.handleBack()`.
Nothing else will close it.

## Style

Kotlin official style (`kotlin.code.style=official`).

Match the surrounding code:

- `findViewById`, not ViewBinding — the flag is on but nothing uses it
- Theme attributes via `getColorFromAttr`, not `R.color` references
- Extend `BaseActivity`, never `AppCompatActivity` directly
- Comments explain *why*, not *what*. The `ai/` package is the model here —
  several of its comments record why an approach was rejected, which is the
  most useful kind.

## Testing expectations

| Change | Expected |
|:--|:--|
| AI engine | Tests required. The pattern exists; follow it. |
| Quiz generator | Tests required. |
| Element data | Run `verify_element_jsons.py`. |
| UI | No harness exists; manual verification is accepted. |
| Sync / billing / preferences | Untested today. Tests welcome but not required. |

If you extract sync merge logic into something testable and cover it, that is the
single most valuable test contribution available — see [Testing](testing).

## Reporting data errors

Incorrect element values are best reported through the *submit data issue* link
in the app, which captures the element and field. Include a source (IUPAC, NIST,
CRC) — values without a citation cannot be applied.

## Documentation

This site lives in `docs/` and is published by GitHub Pages. Pages are Markdown
with just-the-docs front matter:

```yaml
---
title: Page title
parent: Developer Guide
nav_order: 5
---
```

If your change alters documented behaviour, update the page in the same PR.
