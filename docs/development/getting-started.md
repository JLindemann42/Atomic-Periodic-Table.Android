---
title: Getting started
parent: Developer Guide
nav_order: 1
---

# Getting started

## Requirements

| | |
|:--|:--|
| Android Studio | Hedgehog (2023.1) or later |
| JDK | 17 (the project targets JVM 1.8 bytecode, but AGP needs 17) |
| Android SDK | Platform 36, Build Tools 36.0.0 |
| Gradle | Wrapper included — do not install Gradle separately |

The `minSdkVersion` is 24, so an emulator image of API 24 or later works. Note
the build filters native ABIs to `arm64-v8a` only, so an x86_64 emulator image
will not run the app — use an arm64 image, or a physical device.

## Clone

```bash
git clone https://github.com/JLindemann42/Atomic-Periodic-Table.Android.git
```

## Firebase configuration

The build applies the `com.google.gms.google-services` plugin, which requires
`app/google-services.json`. Without it the build fails at configuration time.

If you are building a fork, create your own Firebase project and download its
config:

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package name `com.jlindemann.science`.
3. Enable **Authentication** → Google sign-in provider, and **Cloud Firestore**.
4. Download `google-services.json` into `app/`.

Google Sign-In additionally needs the OAuth web client ID from that project. It
is read from string resources and passed to
`AuthManager.buildGoogleSignInClient`.

You can skip all of this if you only intend to work on the AI engine or the quiz
generators — those have no Firebase dependency and their tests run without it.

## Build

```bash
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat`. The first build downloads a lot; budget several
minutes.

Install to a connected device:

```bash
./gradlew :app:installDebug
```

## Run the tests

The JVM unit tests are the fast, useful ones — around 44 test classes, almost all
of them exercising the AI engine and quiz generators, all runnable without a
device or emulator:

```bash
./gradlew :app:testDebugUnitTest
```

To run a single class:

```bash
./gradlew :app:testDebugUnitTest --tests "com.jlindemann.science.ai.core.AiEngineTest"
```

The instrumented test source set contains only the stock generated example, so
there is nothing useful to run there. See [Testing](testing).

## Compile-only check

Faster than a full build when you only want to know whether Kotlin compiles:

```bash
./gradlew :app:compileDebugKotlin
```

## Lint

```bash
./gradlew :app:lintDebug
```

The project carries a baseline at `app/lint-baseline.xml` with one suppressed
issue. New findings are reported; existing baselined ones are not.

## Where the data lives

Element data is not generated at build time — it is committed as
`app/src/main/assets/elements_{lang}.json`, twelve files of roughly 32,000 lines
each. If you need to modify it, use the Python tooling described in the
[Data Pipeline](../data-pipeline) section rather than hand-editing twelve files.
