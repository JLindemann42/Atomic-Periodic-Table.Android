---
title: Learning games
parent: User Guide
nav_order: 5
---

# Learning games

**Classes:** `fragments/FlashcardFragment.kt` (the tab),
`activities/tools/FlashCardActivity.kt` (deck browser),
`activities/tools/LearningGamesActivity.kt` (the quiz runner)

A quiz and flashcard system built on top of the element data, with a lives, XP
and streak layer to structure repeated practice.

## Flashcards

Decks are organised into levels and category rows. Some decks unlock as rewards
for progress rather than being available immediately. The deck catalogue lives in
`utils/FlashcardCatalog.kt`.

## The quiz

`LearningGamesActivity` runs the quiz itself. Questions are generated
procedurally from the element dataset rather than being a fixed hand-written
bank, which means the question pool is effectively unbounded and questions stay
consistent with the data.

### Question types

Generators live in the `quiz/` package, each implementing `QuestionGenerator`
against a shared `GeneratorContext`:

| Generator | Question shape |
|:--|:--|
| `PositionGenerator` | Which group / period / block is this element in? |
| `ClassificationGenerator` | Which series does this element belong to? |
| `OddOneOutGenerator` | Which of these four does not belong? |
| `SuperlativeGenerator` | Which of these has the highest / lowest *property*? |
| `MolarMassGenerator` | What is the molar mass of this compound? |
| `IsotopeNeutronGenerator` | How many neutrons does this isotope have? |
| `IsotopeStabilityGenerator` | Is this isotope stable? |
| `IsotopeHalfLifeGenerator` | Which isotope has the longer half-life? |
| `IsotopeDecayGenerator` | What decay mode does this isotope undergo? |

`QuestionGenerators.kt` is the facade that assembles them.

## Progression systems

**Lives** (`utils/LivesManager.kt`) — a wrong answer costs a life. Running out
ends the session. Lives regenerate over time, and the current count is shown in
the top bar across the app.

**XP and levels** (`utils/XpManager.kt`) — correct answers award XP, which
accumulates into levels.

**Streaks** (`utils/StreakManager.kt`) — consecutive days on which you played.
A local notification can remind you before the streak lapses, delivered by
`activities/tools/StreakReminderReceiver`.

**Achievements** (`model/Achievement.kt`, `adapter/AchievementAdapter.kt`) —
milestone badges, shown on the [profile page](account-sync) and surfaced as a
toast when earned.

**Statistics** (`model/Statistics.kt`) — per-category counters tracking what you
have practised.

**Exam mode** (`utils/ExamManager.kt`) — a fixed-length assessment run rather
than open-ended practice, specified by `FlashcardCatalog.ExamSpec`.

## Feedback

Correct and incorrect answers are animated by `views/AnimatedEffectView.kt`. On
finishing a run, results are handed back to the Learn tab through
`MainActivity.onNewIntent` with a `show_flashcard_results` extra.

## Syncing progress

If you are signed in, XP, level, achievements, statistics and streak all sync to
the cloud — see [Account and sync](account-sync). The merge is
take-the-highest per field, so playing on two devices never loses progress.
