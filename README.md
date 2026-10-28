# AspectK

**Compile-time Aspect-Oriented Programming for Kotlin Multiplatform.**

AspectK is a Kotlin compiler plugin that injects advice into your functions at compile time, using
the K2 IR transformation API. There is no runtime reflection and no proxy. Declare an `@Aspect`,
mark its advice with `@Before`, `@After` or `@Around`, and the calls are compiled into every function
that carries the target annotation.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mole-labs.aspectk/io.github.mole-labs.aspectk.gradle.plugin)](https://central.sonatype.com/artifact/io.github.mole-labs/aspectk.gradle.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/KMP-JVM%20%7C%20JS%20%7C%20WASM%20%7C%20Native-green.svg)](https://kotlinlang.org/docs/multiplatform.html)

> [!WARNING]
> AspectK is in **alpha**. The API can change without notice between releases, and you may run
> into bugs. Pin the version you depend on and read the
> [changelog](https://mole-labs.github.io/aspectk/reference/changelog/) before upgrading.

> [!IMPORTANT]
> **Use 0.4.2 or later. This is strongly recommended.** It is the version
> [validated on the DuckDuckGo Android browser](https://github.com/oungsi2000/Android).

---

## Quick Setup

### 1. Apply the Gradle plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.mole-labs.aspectk") version "LATEST_VERSION"
}
```

For **Kotlin Multiplatform**:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.mole-labs.aspectk") version "LATEST_VERSION"
}
```

### 2. Replace the boilerplate

**Before.** Cross-cutting concerns are wrapped around the call by hand. Each concern adds one
more level of nesting, and the function name and arguments have to be passed into every wrapper
again:

```kotlin
suspend fun loadProfile(id: String, locale: String): Profile =
    traced(name = "loadProfile", args = listOf(id, locale)) {
        retrying(name = "loadProfile", times = 3) {
            api.loadProfile(id, locale)
        }
    }
```

**After.** The concerns are annotations. Nothing is nested, and nothing is passed by hand:

```kotlin
@Traced 
@Retry(times = 3)
suspend fun loadProfile(id: String, locale: String): Profile = api.loadProfile(id, locale)
```

The wrappers become advice, written once. The join point carries the function name, the
arguments and the annotation values, so nothing has to be passed in:

```kotlin
@Aspect
object CallAspect {
    @Around(Traced::class)
    suspend fun trace(pjp: SuspendProceedingJoinPoint): Any? { /* log and time pjp.proceed() */ }

    @Around(Retry::class)
    suspend fun retry(pjp: SuspendProceedingJoinPoint): Any? { /* call pjp.proceed() up to `times` times */ }
}
```

## Validated on the DuckDuckGo Android browser

AspectK 0.4.2, resolved from Maven Central, was applied to
[DuckDuckGo for Android](https://github.com/duckduckgo/Android): an open-source browser with
50M+ downloads on Google Play and 4.8k stars on GitHub. The whole change is public at
**[oungsi2000/Android](https://github.com/oungsi2000/Android)** (branch `aspectk-trial`).

| | |
|---|---|
| **Project** | DuckDuckGo Android 5.297.1: about 200 Gradle modules, 6,100+ Kotlin files, 900k+ lines |
| **Applied to** | 39 modules, 355 files changed |
| **Aspects** | `@OnIo` (`@Around`, about 600 uses), `@LogFailure` on lambdas, `@FireSavedSitesPixel`, and `@Before` advice on the `@WorkerThread`, `@UiThread` and `@MainThread` annotations the code already carried |
| **Unit tests** | 28,749 passed, 0 failed: 22,297 in the library modules and 6,452 in `:app` |
| **R8** | the `play` APK builds minified with the project's internal release rules |
| **Device** | that minified APK was installed and run on a physical Android device |

## Anti-Vibe-Coding

AspectK is built with the help of AI tools, but it is not vibe-coded. The roles are split:

- **A human designs the architecture and the tests.** What the library does, how it is
  structured, which trade-offs it accepts and which cases the tests have to cover are decided by
  a maintainer, and recorded in pull requests: each
  [merged PR](https://github.com/Mole-Labs/aspectk/pulls?q=is%3Apr+is%3Amerged) states what
  changed and why.
- **AI does the legwork.** It reverse-engineers how Gradle and the Kotlin compiler plugin API
  behave, hunts for bugs, researches, and drafts code and documentation to that design.
- **A human reviews every change** and has to understand it before it is merged.
- **Changes are verified by four test suites**, all run in CI:
  - `aspectk-core` tests compile sources with the plugin and check the woven result
  - `aspectk-core-tests` (`commonTest`) run woven code on JVM, JS, Wasm and iOS
  - `aspectk-plugin` `functionalTest` drives real Gradle builds, including incremental ones
  - `sample` tests build a multi-module app against the working tree, on JVM, Android, JS and iOS

---

## Features

| Feature | Details |
|---------|---------|
| **Compile-time injection** | The generated code calls the advice function directly, with no reflection or proxy at runtime |
| **Kotlin Multiplatform** | JVM, Android, JS (IR), WASM/JS, Native Tier 1–3 |
| **K2 IR** | Built on the Kotlin 2.x IR transformation API |
| **`@Before`, `@After`, `@Around`** | Run code before a function, after it (like `finally`), or around it with control over the result |
| **Many-to-many targeting** | One advice can list several target annotations, and one function can match several aspects |
| **Cross-module injection** | An `@Aspect` in one module is injected into targets in a downstream module. Apply the plugin to both and nothing else is needed |
| **Inheritance** | `inherits = true` also intercepts functions that override an annotated one |
| **Join point metadata** | `JoinPoint` exposes the receiver, method signature, parameters, annotations and arguments |

## Supported Function Types

Advice is injected during IR transformation, before any platform-specific lowering, so AspectK
sees functions the way you wrote them:

- Class member functions
- Top-level functions
- Extension functions
- `suspend` functions. Injection happens before coroutine lowering, so no continuation parameter shows up in `args`
- `inline` functions. The advice is part of the body, so it is inlined along with it
- Property getters and setters
- `expect`/`actual` functions across all platforms

---

## Supported Platforms

JVM · Android · JS (IR) · WASM/JS · macOS (arm64, x64) · iOS (arm64, sim, x64) · Linux (arm64, x64) · Windows (x64) · watchOS · tvOS · Android Native

---

## Documentation

Full documentation: **https://mole-labs.github.io/aspectk/**

- [Installation](https://mole-labs.github.io/aspectk/getting-started/installation/)
- [Quick Start](https://mole-labs.github.io/aspectk/getting-started/quick-start/)
- [Core Concepts](https://mole-labs.github.io/aspectk/core-concepts/aspects/)
- [API Reference](https://mole-labs.github.io/aspectk/api/)

---

## License

```
Copyright 2026 Mole Labs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0
```
