---
title: FAQ
parent: User Guide
nav_order: 11
---

# Frequently asked questions

## Does the app work offline?

Almost entirely, yes. All element data, reference tables, dictionary entries and
quiz questions ship inside the app. The [AI assistant](ai-assistant) answers
offline too, because it runs on-device.

Three things need a connection: sign-in and cloud sync, emission-spectrum
images, and Play Billing for purchases.

## Is the AI assistant an LLM? Are my questions sent to a server?

No, and no. It is a deterministic pipeline that parses your question and looks
the answer up in the data installed with the app. There is no model, no API key
and no request. Nothing you type in the chat leaves your device.

Your chat *history* is uploaded — but only if you are signed in, and only so it
can be restored on your other devices. Signed out, it never leaves the phone.

## Where does the element data come from?

IUPAC, NIST, the CRC Handbook of Chemistry and Physics, and WebElements. The
Sources page in Settings lists attributions in full.

Where no authoritative published value exists, the field carries a placeholder
and the app displays "no data" rather than a guess or an interpolation.

## Why does an element page show "no data" for some fields?

Because the value genuinely is not in the dataset. This is deliberate — the
alternative would be hiding the row, which makes a missing value
indistinguishable from a quantity that does not apply to that element. Elements
with no stable isotopes, or synthetic elements that have only ever existed as a
handful of atoms, have a lot of these.

If you know a published value for a blank field, the *submit data issue* link on
the element page reports it.

## Why isn't the layout mirrored in Urdu?

The app sets `android:supportsRtl="false"` in its manifest, which disables
right-to-left layout mirroring app-wide. Urdu strings are fully translated and
render correctly as text, but the interface stays left-to-right.

This is a known gap rather than an intentional design choice.

## Can I turn off analytics?

Yes — there is a toggle in Settings, backed by
`preferences/AnalyticsPreference.kt`. Analytics is limited to Firebase screen-view
logging; the app does not log your queries, notes or quiz answers.

## What happens to my progress if I don't sign in?

It stays on the device and works normally. Signing in only adds cross-device
sync. Nothing is gated behind having an account.

## I play on two devices — will I lose progress?

No. The merge takes the higher value per field: the greater XP, the further
achievement progress, the longer streak. See [Account and sync](account-sync).

The corollary is that a deliberate reset on one device gets undone by the
other's higher numbers.

## Are PRO purchases subscriptions?

No. They are one-time purchases, tied to your Google Play account and restored
automatically on reinstall or on a new device.

## Why does the assistant sometimes refuse to answer?

It declines rather than guessing. If your question needs something the dataset
cannot support — a causal explanation, a judgement, or a topic outside chemistry
— it says so. Every number it does give traces back to a specific field in the
data.

## Which Android versions are supported?

Android 7.0 (API 24) and later. Material You dynamic colouring on widgets needs
Android 12 (API 31) or later; on earlier versions widgets use the app's own
theme colours.

## The app only installs on some devices — why?

The build ships native libraries for `arm64-v8a` only. That covers essentially
all Android phones sold in recent years, but excludes 32-bit-only ARM devices
and x86 emulator images.

## How do I report a bug or an incorrect value?

Data problems go through the *submit data issue* link on the element page.
Anything else, use the
[GitHub issue tracker](https://github.com/JLindemann42/Atomic-Periodic-Table.Android/issues).
