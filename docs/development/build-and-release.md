---
title: Build and release
parent: Developer Guide
nav_order: 10
---

# Build and release

## Configuration

All build configuration is in `app/build.gradle` — there is no version catalog
and no convention plugin.

| Setting | Value |
|:--|:--|
| `applicationId` | `com.jlindemann.science` |
| `namespace` | `com.jlindemann.science` |
| `versionCode` | 224 |
| `versionName` | 5.0.1m |
| `compileSdkVersion` | 36 |
| `targetSdkVersion` | 36 |
| `minSdkVersion` | 24 |
| `buildToolsVersion` | 36.0.0 |
| Source / target compatibility | Java 8 |
| `jvmTarget` | 1.8 |

Plugins: `com.android.application`, `kotlin-android`, `kotlin-parcelize`,
`com.google.gms.google-services`.

Build features: `viewBinding true` (enabled but unused — see
[UI patterns](ui-patterns)), `buildConfig true`.

Extra source root: `main.java.srcDirs += 'src/main/kotlin'`.

## ABI filtering

```groovy
ndk {
    abiFilters "arm64-v8a"
}
packagingOptions {
    jniLibs { useLegacyPackaging = true }
}
```

Only `arm64-v8a` is shipped, for 16 KB page-size compatibility with the native
dependencies. Practical consequences:

- The app will not install on 32-bit-only ARM devices.
- **x86_64 emulator images will not run it** — use an arm64 emulator image or a
  physical device.

## Dependencies

### AndroidX and UI

`appcompat` 1.7.0 · `core-ktx` 1.15.0 · `activity-ktx` 1.10.1 ·
`constraintlayout` 2.2.1 · `cardview` 1.0.0 · `browser` 1.8.0 ·
`work-runtime-ktx` 2.11.0 · Material Components 1.14.0

### Kotlin

`kotlin-stdlib` · `kotlinx-coroutines-core` / `-android` 1.7.1

### Data and networking

Gson 2.10.1 · Klaxon 5.6 · OkHttp 5.3.2 · Apache Commons Math 3.6.1

Two JSON libraries are in the dependency list alongside the platform
`org.json` — that is more than the project needs, and consolidating would be a
reasonable cleanup.

### Images

Glide 5.0.5 · Picasso 2.71828 · AndroidSvgLoader 1.0.2

Again, two image loaders. Picasso is used for the AI emission-spectrum card;
Glide for profile photos.

### Firebase and Google

Firebase BoM 33.1.2 (`firebase-auth-ktx`, `firebase-firestore-ktx`,
`firebase-analytics-ktx`, `firebase-crashlytics-buildtools` 3.0.3) ·
`play-services-auth` 21.3.0 · `billing-ktx` 8.0.0 · `review-ktx` 2.0.2 ·
`recaptcha` 18.5.1

### JitPack UI libraries

`twowaynestedscrollview` · `sliding-up-panel` 3.4.0 ·
`drag-drop-swipe-recyclerview` 1.2.0 · `zoomlayout` 1.9.0 ·
`realtimeblurview`

Repositories are declared centrally in `settings.gradle` with
`FAIL_ON_PROJECT_REPOS`: `google()`, `mavenCentral()`, JitPack.

## Gradle properties

`gradle.properties` sets `-Xmx1920M`, enables Jetifier, disables the
non-transitive R class, and sets `android.builtInKotlin=false`.

It also carries `android.suppressUnsupportedCompileSdk=31`, a leftover from an
earlier `compileSdk` that has no effect at compileSdk 36 and can be removed.

## Lint

```groovy
lint { baseline = file("lint-baseline.xml") }
```

`app/lint-baseline.xml` suppresses exactly one issue —
`UnspecifiedImmutableFlag` in `ShortCommandWidget.kt`. New findings are
reported normally.

```bash
./gradlew :app:lintDebug
```

## Release build

```groovy
buildTypes {
    release {
        minifyEnabled false
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                      'proguard-rules.pro'
        signingConfig signingConfigs.config
    }
}
```

**`minifyEnabled false`** — R8 shrinking and obfuscation are off for release.
`proguard-rules.pro` contains only two `-keep` rules for RenderScript classes.
Turning minification on would meaningfully reduce APK size but needs keep rules
audited for the reflection-using dependencies first.

### The signing gap

```groovy
signingConfigs {
    config {
        // Remove hardcoded Windows path - use environment variable or gradle property instead
        // storeFile file('C:\\Users\\...\\science_keys.jks')
    }
}
```

The hardcoded keystore path was removed and **nothing replaced it**. The
`signingConfig` block is empty — no `storeFile`, no credentials — while the
release build type still references it. Release builds cannot be signed from the
checked-in configuration alone. There is no `keystore.properties` and no `.jks`
in the repo (correctly — neither should be committed).

To fix it properly, read credentials from a gitignored properties file or from
environment variables:

```groovy
signingConfigs {
    config {
        def props = new Properties()
        def f = rootProject.file("keystore.properties")
        if (f.exists()) {
            props.load(new FileInputStream(f))
            storeFile file(props['storeFile'])
            storePassword props['storePassword']
            keyAlias props['keyAlias']
            keyPassword props['keyPassword']
        }
    }
}
```

with `keystore.properties` added to `.gitignore`.

Until then, release signing has to be done manually or through Play App Signing
with an upload key configured outside the repo.

One oddity to be aware of: `.gitignore` lists `app/build.gradle`. The file is
already tracked, so the rule is inert and changes to it do get committed — but
the entry is almost certainly unintended and would bite anyone who removed and
re-added the file.

## Building

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:bundleRelease
```

## Firebase

`app/google-services.json` is required at configuration time. See
[Getting started](getting-started).
