# AspectK

**Compile-time Aspect-Oriented Programming for Kotlin Multiplatform.**

AspectK is a Kotlin compiler plugin that injects advice into your functions at compile time. There is no runtime reflection and no proxy. Declare an `@Aspect`, mark its advice with `@Before`, `@After` or `@Around`, and the calls are compiled into every function that carries the target annotation.

## Why AspectK?

<div class="grid cards" markdown>

-   **Compile-Time Injection**

    Advice is injected during K2 IR transformation, with no reflection and no dynamic proxies. The generated code is what you would get by writing the calls by hand.

-   **Kotlin Multiplatform**

    Runs on JVM, JS, WASM and Native. An aspect defined in `commonMain` applies on every target.

-   **K2 IR**

    Built on the K2 compiler's IR API. Supports Kotlin 2.2.20 through 2.4.10.

</div>

## Quick Start

### 1. Add the plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.mole-labs.aspectk") version "LATEST_VERSION"
}
```

> The plugin adds `aspectk-runtime` for you, so there is no dependency to declare.

### 2. Define a target annotation

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Logged
```

### 3. Create an Aspect

```kotlin
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
    println("Processing order $orderId")
}
```

## Supported Platforms

| Platform | Target |
|----------|--------|
| JVM | ✅ |
| Android | ✅ |
| JS (IR) | ✅ |
| WASM/JS | ✅ |
| macOS (arm64, x64) | ✅ |
| iOS (arm64, x64, sim) | ✅ |
| Linux (arm64, x64) | ✅ |
| Windows (x64) | ✅ |

[Get started →](getting-started/installation.md){ .md-button .md-button--primary }
[API Reference →](api/index.html){ .md-button }
