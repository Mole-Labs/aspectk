# AspectK

**Compile-time Aspect-Oriented Programming for Kotlin Multiplatform.**

AspectK is a Kotlin compiler plugin that injects advice code at **compile time** via K2 IR transformation —
no runtime reflection, no proxies, zero overhead. Declare an `@Aspect`, annotate your advice with `@Before`,
and AspectK weaves the call directly into the intercepted functions during compilation.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mole-labs.aspectk/io.github.mole-labs.aspectk.gradle.plugin)](https://central.sonatype.com/artifact/io.github.mole-labs/aspectk.gradle.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/KMP-JVM%20%7C%20JS%20%7C%20WASM%20%7C%20Native-green.svg)](https://kotlinlang.org/docs/multiplatform.html)

---

## Quick Setup

### 1. Apply the Gradle plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.mole-labs.aspectk.compiler") version "LATEST_VERSION"
}
```

For **Kotlin Multiplatform**:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.mole-labs.aspectk.compiler") version "LATEST_VERSION"
}
```

### 2. Define a target annotation

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Logged
```

### 3. Create an aspect

```kotlin
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint

@Aspect
object LoggingAspect {
    @Before(target = [Logged::class])
    fun log(joinPoint: JoinPoint) {
        println("→ ${joinPoint.signature.methodName}(${joinPoint.args.joinToString()})")
    }
}
```

### 4. Annotate your functions

```kotlin
@Logged
fun processOrder(orderId: String, amount: Double) {
    // AspectK injects LoggingAspect.log() here at compile time
}
```

---

## Features

| Feature | Details |
|---------|---------|
| **Zero runtime overhead** | Advice is woven at compile time — the generated code calls the advice function directly |
| **Kotlin Multiplatform** | JVM, Android, JS (IR), WASM/JS, Native Tier 1–3 |
| **K2 IR powered** | Built on the Kotlin 2.x IR transformation API |
| **Many-to-many targeting** | One `@Before` can list multiple target annotations; one function can match multiple aspects |
| **Inheritance support** | `@Before(inherits = true)` intercepts overriding functions automatically |
| **Rich join point metadata** | `JoinPoint` exposes receiver, method signature, parameters, annotations, and arguments |

## Supported Function Types

AspectK works with the full range of Kotlin function kinds — including those that other AOP
solutions struggle with. Because advice is injected during the **IR transformation phase**,
before platform-specific lowering, AspectK intercepts functions exactly as Kotlin defines them:

- Class member functions
- Top-level functions
- Extension functions
- **`suspend` functions** — interception happens before coroutine lowering, so the advice sees the function in its original, unsuspended form
- **`inline` functions** — intercepted at the call site before the inliner runs
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
