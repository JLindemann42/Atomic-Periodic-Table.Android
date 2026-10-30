---
title: The periodic table
parent: User Guide
nav_order: 1
---

# The periodic table

**Class:** `fragments/HomeFragment.kt`, hosted by `activities/MainActivity.kt`

The default tab. A full periodic table of all 118 elements, rendered as a real
grid rather than a scrolling list, so the shape of the table — the blocks, the
gap where the lanthanides pull out — stays visible.

## Moving around

The table sits inside a `ZoomLayout` (the `com.otaliastudios:zoomlayout`
library), so it supports pinch-to-zoom and two-finger pan. Two rulers run along
the top and left edges showing group numbers and period numbers; they are
scroll-synced to the table, so they stay aligned as you pan.

Tapping any element tile opens its [detail page](element-detail).

## Colouring the table by property

The table can recolour itself to show a property gradient across all elements at
once. This is the fastest way to see a periodic trend — electronegativity
climbing toward fluorine, atomic radius growing down a group.

Available colour modes include element category (alkali metal, noble gas,
lanthanide, …), electronegativity, phase at room temperature, and the other
numeric properties exposed through the favourites bar. Selecting a mode
recolours every tile and updates the legend.

Internally this is `HomeFragment`'s `initTableChange` / `initElectro` family of
functions, with the shared colour helpers living in
`extensions/TableExtension.kt`.

## Search

The search bar at the top of `MainActivity` matches on element name, symbol and
atomic number. It searches in the language the app is currently displaying, so
"Eisen" finds iron when the app is set to German. Matching tiles stay
highlighted in the grid while non-matches dim.

Search state persists via `preferences/SearchPreferences.kt`.

## The filter and hover menus

The floating action button opens a filter panel that narrows the table to a
subset — a specific block, a category, a phase. The hover quick-menu (the
overlay reached from the top bar) provides fast actions including jumping to a
random element, which is a surprisingly good way to browse.

Both are overlay views inside `activity_main.xml` (`filter_box`,
`hover_menu_include`) that fade in and out rather than being separate screens,
which is why the back button closes them one at a time instead of leaving the
table. That ordering is handled by `MainActivity.handleBack()`.

## The favourites bar

You can pin a set of properties to a quick-view bar so they are always visible
without opening an element page. Which properties appear is controlled by the
group of preference classes in `preferences/FavoriteBarPreferences.kt` — there
is one small class per property (density, melting point, electronegativity,
specific heat, resistivity, and so on), each a simple on/off toggle configured
in [Settings](settings-languages).
