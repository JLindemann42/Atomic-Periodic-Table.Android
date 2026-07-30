---
title: UI patterns
parent: Developer Guide
nav_order: 4
---

# UI patterns

## BaseActivity and its template methods

`activities/BaseActivity.kt` is the root of nearly every activity. Rather than
composing helpers, subclasses override template methods.

The one you will implement most often:

```kotlin
override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
    // apply padding/margins for edge-to-edge
}
```

`BaseActivity` installs a single `setOnApplyWindowInsetsListener` and fans the
resolved values out through this method, so no activity should be registering
its own inset listener.

Other things `BaseActivity` provides:

| Member | Purpose |
|:--|:--|
| `attachBaseContext` | Wraps the context with the user's chosen locale via `LocaleUtil` |
| `getColorFromAttr(attr)` | Resolves a theme attribute to a colour — use this instead of hardcoded colours |
| `goToProPage()` | Standard route to the upgrade screen from any gate |
| `onResume` hook | Checks for newly-earned achievements and toasts them |

Do not extend `AppCompatActivity` directly for a new screen — you lose locale
handling, insets and theming.

## Fragments

`fragments/BaseFragment.kt` is thin: it exposes `getMainActivity()` and forwards
`onApplySystemInsets` from the host. All five bottom-nav fragments extend it.

Fragment switching in `MainActivity.switchFragment()` swaps a single
`FrameLayout` container with cross-fade animations. There is no Navigation
component and no back stack for the tabs.

## Back handling

Because overlays are `<include>`d views rather than destinations, back has to be
dispatched manually. `MainActivity.handleBack()` implements an explicit priority
chain:

```
AI chat panel open?    → close it
PRO popup open?        → close it
Hover menu open?       → close it
Search open?           → close it
Not on Home tab?       → go to Home
otherwise              → let the system handle it
```

This is registered both as an `OnBackPressedCallback` and, on API 34+, a
platform `OnBackInvokedCallback` — matching `android:enableOnBackInvokedCallback="true"`
in the manifest. `setBackInterceptionEnabled()` toggles the whole chain.

**If you add a new overlay, add it to this chain.** Nothing else will close it.

## Adapters

23 files in `adapter/`, one per list-driven screen. Most reference-table and
tool lists use `DragDropSwipeRecyclerView` from
`com.ernestoyaquello.dragdropswiperecyclerview` for drag reordering, gated
behind an explicit "reorder mode" toggle so that a long-press does not start a
drag during normal browsing:

```kotlin
recyclerView.orientation = ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
recyclerView.longPressToStartDragging = true
recyclerView.dragListener = object : OnItemDragListener<T> {
    override fun onItemDropped(from: Int, to: Int, item: T) { saveOrder() }
}
```

Order is persisted as a list of short string IDs (`"iso"`, `"phi"`, `"ele"`, …)
in `TableOrderPreference` / `ToolOrderPreference`. Unknown IDs in a saved order
are dropped and unlisted items appended, so adding a new table does not break
existing users' saved orders.

## Custom views

Ten canvas-drawn views in `views/`, plus `CrystalStructureView` in
`extensions/`. These render scientific visualisations that no off-the-shelf
chart library would produce well:

| View | Draws |
|:--|:--|
| `ElectronShellView` | Bohr-model concentric electron shells |
| `IonizationSeriesView` | Successive ionisation energies as a series |
| `IsotopeDecayView` | Isotopes by mass vs half-life, coloured by decay mode |
| `AbundanceBarsView` | Abundance across six reservoirs, log scale |
| `PoissonBandView` | Where an element sits in the Poisson-ratio range |
| `AnimatedEffectView` | Quiz correct/incorrect feedback |
| `ScienceBackgroundView` | Decorative background |
| `CrystalStructureView` | Unit cell, with geometry in `CrystalStructures`/`CrystalMath` |

Plus two stateless helper objects that are not views: `NfpaDiamondBinder` (binds
NFPA ratings into an existing layout) and `ProCardGate` (renders the locked-card
state over a PRO visualisation). `ChartPalette` holds the shared colour ramp —
use it rather than picking colours per view, so charts stay consistent between
the element pages and the AI chat cards.

## Theming and dark mode

Base theme is Material 3 `Theme.Material3.DayNight.NoActionBar`, defined as
`AppTheme` / `AppThemeDark` in `values/styles.xml`.

Dark mode is **tri-state**, not a boolean. `preferences/ThemePreference.kt`
stores:

| Value | Meaning |
|:--:|:--|
| `0` | Force light |
| `1` | Force dark |
| `100` | Follow the system (default) |

The theme is applied manually in `onCreate` **before** `setContentView`. If you
add an activity that does not extend `BaseActivity`, you must replicate this or
it will ignore the user's choice.

Version-qualified resource sets (`values-v23` through `values-v31`) layer in
per-API-level overrides, with `values-v31` carrying Material You dynamic colour
for the widgets.

Always resolve colours through theme attributes (`getColorFromAttr`) rather than
`R.color` references, or the screen will not follow the theme.

## Animations

`animations/Anim.kt` holds the fade in/out helpers used across `MainActivity`.
`animations/TitleBarAnimator.kt` drives the collapsing title bar shared by
`TablesFragment` and `ToolsFragment`.

Note that `utils/Utils.kt` duplicates some of the fade helpers
(`fadeInAnim`, `fadeOutAnim`, `fadeInAnimBack`) that also exist in `Anim.kt`,
and both sets are in use. Prefer `Anim.kt` for new code.

## Edge-to-edge

`MainActivity` calls `WindowCompat.setDecorFitsSystemWindows(window, false)`.
All inset handling then flows through `BaseActivity`'s single listener into
`onApplySystemInsets`. IME insets feed `AiChatPanelController`'s
keyboard-gap logic so the chat input tracks the keyboard.

## Large screens and orientation

Only three layouts have `layout-sw600dp` tablet variants: `activity_tables`,
`activity_tools` and `isotope_panel`. Everything else uses the phone layout at
all sizes. There are no landscape-specific layouts;
`LearningGamesActivity` declares `configChanges` for orientation so it handles
rotation itself rather than being recreated.

This is a genuine gap rather than a decision — tablet layouts for the element
detail page and the periodic table would be a worthwhile contribution.

## Accessibility

`contentDescription` is set on icon buttons in the main layouts, but coverage is
ad hoc rather than audited, and there are no accessibility tests. If you touch a
layout, adding missing content descriptions is always welcome.

## ViewBinding

`viewBinding true` is set in `app/build.gradle`, but no generated binding class
is used anywhere — the codebase is `findViewById` throughout. Match the
surrounding style rather than introducing bindings in a single file.
