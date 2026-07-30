---
title: Chat cards
parent: AI Agent
nav_order: 6
---

# Chat cards

Package: `ai/cards/` — ten files, plus `adapter/ChatCardBinder.kt` for rendering.

A card is the visual attached to an answer: an electron shell diagram beside an
electron configuration, an isotope decay chart beside a half-life.

## The eight kinds

```kotlin
enum class ChatCardKind {
    ELECTRON_SHELL, CRYSTAL_STRUCTURE, EMISSION_SPECTRUM, POISSON_BAND,
    NFPA_DIAMOND, IONIZATION_SERIES, ISOTOPE_DECAY, ABUNDANCE
}
```

Each maps to a custom view already used on the
[element detail page](../user-guide/element-detail), so a chart looks the same
in chat as it does on the element page.

## Cards are derived from results, not from text

`CardSelector.select()` takes the **typed `ExecutionResult`** and the plan — never
the query string.

```
ExecutionResult ──> forField(fieldId)     ──┐
                └─> forCategory(category) ──┴──> ChatCardKind? ──> reducer ──> ChatCard?
```

The distinction matters. Selecting on query text would attach an isotope chart to
any question containing the word "isotope", including one that resolved to
something else entirely. Selecting on the result means the card always depicts
what the answer actually says.

## Reducers can decline

Each kind has a reducer that returns `null` when the data cannot support a
meaningful visual:

| Reducer | Declines when |
|:--|:--|
| `IonizationSeriesReducer` | Too few successive energies to form a series |
| `IsotopeSeriesReducer` | Too few isotopes, or no half-life data |
| `PoissonBandReducer` | No Poisson ratio for the element |
| `AbundanceReducer` | No abundance reservoirs populated |
| `NfpaLabeller` | No NFPA ratings |
| `CrystalSystemResolver` | Unrecognised or absent crystal system |

Returning `null` suppresses the card and the answer is text-only. An empty or
one-point chart is worse than no chart.

`ChartScale.kt` handles axis scaling — log where the values span orders of
magnitude, as abundance does.

## Cards carry references, not data

```kotlin
data class ChatCard(
    val kind: ChatCardKind,
    val elementKey: String,
    val title: String,                       // already localised at compose time
    val args: Map<String, String> = emptyMap()
)
```

No numbers. At render time `adapter/ChatCardBinder.bind()` re-reads the values
from `KnowledgeStore` and hands them to the view.

The reason is size. Chat messages are persisted — through a `Parcel`, and
through **Firestore**, where sessions are stored as an array field on the user
document. A 42-isotope chart costs about thirty bytes as a reference versus
roughly two kilobytes as serialised data.

A side benefit: a card rendered from restored history reflects current data
rather than whatever was true when the message was sent.

## Serialisation

`ChatCardCodec` (and `ChatActionCodec` for deep-link chips) encode onto
`ChatMessage` using a **control-character record format**, not JSON.

This is not stylistic. Under JVM unit tests `org.json` is a stub whose methods
throw, and these codecs are exercised in tests, so they cannot depend on it.

## Binding

`ChatCardBinder(resolveElement, strings, store)`:

```kotlin
fun bind(cardView: View, card: ChatCard): Boolean
fun unbind(cardView: View)
```

`bind` returns false when the card cannot be rendered, and the caller hides the
container. `unbind` is required — these are RecyclerView view holders and stale
state would otherwise leak between rows.

Views used: `ElectronShellView`, `CrystalStructureView`, `PoissonBandView`,
`IonizationSeriesView`, `IsotopeDecayView`, `AbundanceBarsView`, and
`NfpaDiamondBinder`. Colours come from `views/ChartPalette.kt` so chat cards and
element pages match.

## The one network card

`EmissionSpectrumUrl.kt` holds the only remote reference in the whole subsystem:

```
https://www.jlindemann.se/atomic/emission_lines/
```

It fetches a **pre-rendered GIF** via Picasso — an image, not data and not
inference. It fails to a grey box when offline, which is why
`ChatCardPolicy.allowNetworkCards` withholds this one kind when the user has
enabled offline mode.

Every other card renders from local data.

## PRO gating

Cards are gated at render time through `views/ProCardGate.kt`, showing the same
locked-card upsell a PRO field shows on the element page. The card kind is still
selected; only the rendering is replaced. See
[Billing](../development/billing).

## Reachability is tested

`ai/cards/CardReachabilityTest` asserts that **every** `ChatCardKind` is
reachable from some query. Adding a kind that no query can produce fails the
build — which is the correct outcome, since a card no one can see is dead code
that still has to be maintained.

`CardFoundationsTest` covers the reducers and scaling.

## Adding a card

1. Add the value to `ChatCardKind`.
2. Write the reducer. Return `null` when the data is insufficient.
3. Map it in `CardSelector` by `fieldId` or `FieldCategory`.
4. Add the view; bind it in `ChatCardBinder`, re-deriving values from
   `KnowledgeStore`.
5. Use `ChartPalette` for colours.
6. If it needs the network, respect `ChatCardPolicy.allowNetworkCards`.
7. Make sure some query reaches it, or `CardReachabilityTest` fails.
