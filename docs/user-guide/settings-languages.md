---
title: Settings and languages
parent: User Guide
nav_order: 9
---

# Settings and languages

**Class:** `activities/SettingsActivity.kt`, with sub-pages under
`activities/settings/`

## Appearance

**Theme** — three states, not two: force light, force dark, or follow the system
setting. Following the system is the default. Backed by
`preferences/ThemePreference.kt`, which stores `0`, `1` or `100` respectively.

## Language

The app can be set to a language independently of your system language. Changing
it takes effect immediately without sending you to Android settings, because
locale is applied per-activity in `BaseActivity.attachBaseContext` via
`utils/LocaleUtil.kt`.

Twelve languages ship, across seventeen locale variants:

| Language | Variants |
|:--|:--|
| English | default |
| Afrikaans | `af` |
| Chinese | `zh-rCN` |
| Filipino | `b+fil` |
| French | `fr` |
| German | `de` |
| Hindi | `hi` |
| Italian | `it-rIT` |
| Portuguese | `pt-rBR` |
| Spanish | `es`, `es-rAR`, `es-rES`, `es-rMX` |
| Swedish | `sv-rSE` |
| Urdu | `ur`, `ur-rIN`, `ur-rPK` |

Element names and descriptions are translated too, not just the interface —
those come from `assets/elements_{lang}.json`.

**Known limitation:** the app sets `android:supportsRtl="false"`, so Urdu
displays translated text in a left-to-right layout rather than a mirrored one.
See the [FAQ](faq).

## Units

`activities/settings/UnitActivity.kt` sets your preferred units for temperature,
density, energy and the other dimensions. Element pages and assistant answers
both honour the choice.

## Favourites bar

Choose which properties appear in the quick-view bar on the periodic table. Each
property is an independent toggle — density, melting point, boiling point,
electronegativity, atomic radii (empirical, calculated, covalent, van der Waals),
specific heat, heat of fusion, heat of vaporisation, resistivity, radioactivity,
phase and molar mass.

## Reordering lists

`activities/settings/OrderActivity.kt` sets the order of the Tables and Tools
lists. The same reordering is also available inline in those tabs via their edit
button.

## Offline mode

`preferences/SettingPreferences.kt` holds an offline preference. With it on, the
app withholds features that need the network — most visibly the assistant's
emission-spectrum card, which would otherwise render as an empty box.

## Notifications

Streak reminders are delivered as local notifications. On Android 13 and later
this requires the `POST_NOTIFICATIONS` permission, which the app requests when
you first enable them.

## Favourite elements

`activities/settings/FavoritePageActivity.kt` lists elements you have starred,
plus favourite compounds.

## Information pages

- **About** (`AboutActivity`) — version and app information
- **Credits** (`CreditsActivity`) — contributors
- **Sources** (`SourcesActivity`) — where the element data comes from
- **Licenses** (`LicensesActivity`) — open-source attributions
- **Submit data issue** (`SubmitActivity`) — report an incorrect value
- **Experimental** (`settings/ExperimentalActivity`) — in-development features
