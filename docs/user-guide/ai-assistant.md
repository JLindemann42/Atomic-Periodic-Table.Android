---
title: The AI assistant
parent: User Guide
nav_order: 6
---

# The AI assistant

**Classes:** `utils/AiChatPanelController.kt` (the panel),
`ai/AIAgentManager.kt` (the entry point)

A chat panel that answers chemistry questions in plain language. It slides up
from the bottom of the periodic table screen and from every element page.

For how it works internally, see the [AI Agent](../ai) section. This page is
about using it.

## It runs on your device

The assistant is not a chatbot calling a server. There is no LLM, no API key and
no request leaving your phone when you ask a question. It parses your question,
looks up the answer in the element data already installed with the app, and
composes a response.

Two practical consequences:

- **It works with no connection.** Airplane mode, no signal, anywhere.
- **Your questions are not sent anywhere.** The only thing that ever leaves the
  device is your chat *history*, and only if you are signed in — see
  [Account and sync](account-sync).

The one exception is the emission-spectrum card, which loads a spectrum image
from the web. If you have enabled offline mode in
[Settings](settings-languages), that card is withheld rather than showing a
broken image.

## What you can ask

**Look up a property**
: *"density of tungsten"*, *"what is the melting point of gallium"*,
  *"electron configuration of iron"*

**Convert as you ask**
: *"melting point of iron in Fahrenheit"* — the answer is converted to the unit
  you asked for.

**Compare two elements**
: *"is gold denser than lead"*, *"compare the boiling points of nitrogen and
  oxygen"*

**Superlatives**
: *"which element has the highest melting point"*, *"the three least dense
  metals"*

**Filtered lists**
: *"which noble gases are liquid at room temperature"*, *"transition metals with
  density above 15"*

**Aggregates**
: *"average atomic mass of the halogens"*, *"how many lanthanides are there"*

**Isotopes and nuclides**
: *"isotopes of caesium"*, *"how many neutrons in carbon-14"*, *"which is more
  stable, uranium-235 or uranium-238"*

**Formulas and moles**
: *"molar mass of Ca(OH)2"*, *"how many moles in 25 g of NaCl"*

**Reference data**
: *"what is the Planck constant"*, *"standard potential of the zinc half cell"*,
  *"define allotrope"*

**Two things at once**
: *"density of gold and how does it compare to lead"* — compound questions get
  split and both parts answered.

**Follow-ups**
: Once you have asked about an element, *"and its boiling point?"* works. The
  assistant remembers the element and the property you were last discussing.

## Answers come with cards and links

Where a visual helps, the answer carries one: an electron shell diagram, a
crystal structure, an ionisation series, an isotope decay chart, abundance bars,
an NFPA diamond, a Poisson band, or an emission spectrum. There are eight card
types and the assistant picks one only when the underlying data actually
supports it.

Answers also carry chips that deep-link into the relevant screen — a constant
links to the constants table, an isotope answer links to the nuclide chart, an
element property links to that element's page.

## Ask in your own language

The assistant works in twelve languages: Afrikaans, Chinese, English, Filipino,
French, German, Hindi, Italian, Portuguese, Spanish, Swedish and Urdu. You do not
need to switch anything — it detects the language of each message and answers in
kind.

One current limitation: the non-element reference tables (constants, equations,
dictionary, ions, alloys, geology) hold English content. A question about them
asked in another language will be understood and the answer framed in your
language, but the table content itself stays English.

## When it doesn't know

The assistant declines rather than guessing. If a question falls outside what
the data can support — asking why an element is reactive, or something not about
chemistry at all — it says so instead of producing a plausible-sounding answer.
This is deliberate: every number in an answer traces back to a specific field in
the dataset.

## Daily message limits

| Tier | Messages per day |
|:--|:--|
| Free | 16 |
| PRO | 64 |
| PRO+ | Unlimited |

The counter is keyed to your device's calendar day and resets at local midnight
— so if you run out at 23:50, you get them back in ten minutes, not the
following evening. Implemented in `ai/AIRateLimiter.kt`.

Some data fields are themselves PRO features. Asking a free account about, say,
Vickers hardness returns an upgrade prompt rather than the value — the same gate
that applies on the [element detail](element-detail) page.

## Chat history

Conversations persist while the app is running. If you are signed in with
Google, your last 20 sessions are saved to your account and restored on any
device (`ai/ChatHistoryManager.kt`). Signed out, history stays in memory only.

## Widgets

Two [home-screen widgets](widgets) open the assistant directly: a pill-shaped
"ask" bar, and one tile of the quick-navigation grid.
