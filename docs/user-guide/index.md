---
title: User Guide
nav_order: 2
has_children: true
permalink: /user-guide/
---

# User Guide

What the app does, screen by screen. Every section names the underlying class so
that this guide doubles as an index into the codebase.

## Screen map

The app opens on `SplashActivity`, which routes first-time users through
`IntroductionActivity` and everyone else into `MainActivity`. `MainActivity` is
the shell: a bottom navigation bar switching between five fragments, with the
search bar, filter controls and the AI chat panel layered on top.

```
SplashActivity
  └─ IntroductionActivity   (first run only)
       └─ MainActivity      (bottom navigation, 5 tabs)
            ├─ HomeFragment       — the periodic table
            ├─ TablesFragment     — 12 reference tables
            ├─ ToolsFragment      — 4 calculators
            ├─ FlashcardFragment  — learning games
            └─ ProFragment        — upgrade
```

Everything else is a standalone activity launched from those tabs, from the top
bar, or from a home-screen widget. There are 37 activities in total.

## The five tabs

| Tab | Fragment | What it is |
|:--|:--|:--|
| Table | `HomeFragment` | [The periodic table](periodic-table) — zoomable, colourable by property |
| Tables | `TablesFragment` | [Reference tables](reference-tables) — 12 data tables, reorderable |
| Tools | `ToolsFragment` | [Calculators](tools) — molar mass, units, ideal gas, dictionary |
| Learn | `FlashcardFragment` | [Learning games](learning) — flashcards, quizzes, XP, streaks |
| PRO | `ProFragment` | [Upgrade](pro-tiers) — what each tier unlocks |

## Reached from elsewhere

- **[Element detail](element-detail)** — tap any element anywhere in the app.
- **[The AI assistant](ai-assistant)** — the chat button in `MainActivity` and
  on every element page.
- **[Home-screen widgets](widgets)** — five widgets, added from the launcher.
- **[Account and sync](account-sync)** — the profile icon in the top bar.
- **[Settings](settings-languages)** — the gear icon in the top bar.

## Common questions

The [FAQ](faq) covers offline behaviour, where the data comes from, analytics
opt-out, and known gaps such as the absence of right-to-left layout mirroring
for Urdu.
