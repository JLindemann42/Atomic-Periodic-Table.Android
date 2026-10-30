---
title: Home-screen widgets
parent: User Guide
nav_order: 7
---

# Home-screen widgets

Five widgets, added the usual way: long-press the home screen → Widgets → find
*Atomic*.

All five adopt Material You dynamic colours on Android 12 and later, using
`layout-v31/` variants of their layouts, and fall back to the app's own theme
colours on Android 11 and earlier. All of them follow the app's light/dark
setting and display in your chosen language.

| Widget | Class | What it does |
|:--|:--|:--|
| Element of the Day | `ElementOfTheDayWidget` | A different element each day |
| Science Daily | `ScienceDailyWidget` | A random chemistry fact |
| Quick Ask | `AIQuickAskWidget` | Opens the AI assistant |
| Quick Nav | `ElementQuickNavWidget` | Four shortcut tiles |
| Search | `ShortCommandWidget` | Opens the app to search |

## Element of the Day

The largest of the five. Shows one element per day with its symbol at display
size, its name, atomic number, and a scrollable description.

**Which element you get** is derived from the day of the year:

```kotlin
val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
val index = (dayOfYear - 1) % 118
```

So January 1st is hydrogen, January 2nd helium, and after oganesson on day 118
it cycles back round. Over a 365-day year each element comes up roughly three
times.

The widget refreshes every 24 hours and also on `DATE_CHANGED` and
`TIMEZONE_CHANGED` broadcasts, so it turns over at local midnight rather than
whenever the update interval happens to fall.

Tapping it opens that element's [detail page](element-detail).

**Sizing:** minimum 250 × 180 dp, best at 4 × 3 cells, resizable both ways.

## Science Daily

A single chemistry fact, drawn from the localised fact pool in
`ai/AIPersonality.kt`. The text auto-sizes to fit whatever dimensions you give
the widget, and re-fits when you resize it.

## Quick Ask

A pill-shaped search bar that opens `MainActivity` with the AI chat panel
already expanded. Useful if you ask the assistant things often enough that
opening the app and finding the chat button is friction.

## Quick Nav

Four tiles in a grid: **Table**, **Quiz**, **AI** and **Molar mass**. Each fires
a distinct intent action (`OPEN_TABLE`, `OPEN_QUIZ`, `OPEN_AI_CHAT`,
`OPEN_MOLAR_MASS`) that `MainActivity.handleWidgetIntent()` routes to the right
screen.

## Search

The simplest one — a search bar that launches the app.
