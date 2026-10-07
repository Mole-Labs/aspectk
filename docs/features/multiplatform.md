# Kotlin Multiplatform Support

The compiler plugin works at the IR level, before any platform backend runs, and the runtime
library is published for every target below.

## Supported Platforms

| Platform | Target | Tier |
|----------|--------|------|
| JVM | `jvm` | — |
| Android JVM | `android` | — |
| JavaScript (IR) | `js` | — |
| WebAssembly/JS | `wasmJs` | — |
| macOS ARM64 | `macosArm64` | Native Tier 1 |
| macOS x64 | `macosX64` | Native Tier 1 |
| iOS ARM64 | `iosArm64` | Native Tier 1 |
| iOS Simulator ARM64 | `iosSimulatorArm64` | Native Tier 1 |
| iOS x64 | `iosX64` | Native Tier 1 |
| Linux x64 | `linuxX64` | Native Tier 1 |
| Linux ARM64 | `linuxArm64` | Native Tier 2 |
| Windows x64 | `mingwX64` | Native Tier 2 |
| watchOS / tvOS | Various | Native Tier 2 |
| Android Native | arm32/arm64/x86/x64 | Native Tier 3 |

## Setup for KMP

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.github.mole-labs.aspectk") version "LATEST_VERSION"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser() }
}
```

The Gradle plugin enables the compiler plugin for every configured target and adds
`aspectk-runtime` as a dependency. There is nothing to configure per target.

## Shared Aspects Across Platforms

Define an aspect in `commonMain` as a regular `object` and it applies on every platform:

```kotlin
// commonMain
@Aspect
object CommonLoggingAspect {
    @Before(target = [Logged::class])
    fun log(joinPoint: JoinPoint) {
        println("[${joinPoint.signature.methodName}] called")
    }
}
```

## Platform-Specific Advice

Use `expect`/`actual` for platform-specific behavior. Annotate the `expect` declaration. The
annotation carries over to every `actual` implementation, and AspectK intercepts each one:

```kotlin
// commonMain
@Target(AnnotationTarget.FUNCTION)
annotation class Traced

// @Traced on the expect declaration carries over to every actual
@Traced
expect fun fetchData(endpoint: String): String

// commonMain: the aspect intercepts @Traced on all platforms
@Aspect
object TracingAspect {
    @Before(target = [Traced::class])
    fun trace(joinPoint: JoinPoint) = platformLog(joinPoint.signature.methodName)
}

expect fun platformLog(methodName: String)

// jvmMain
actual fun fetchData(endpoint: String): String = httpClient.get(endpoint)
actual fun platformLog(methodName: String) { openTelemetry.startSpan(methodName) }

// iosMain
actual fun fetchData(endpoint: String): String = NSURLSession.data(endpoint)
actual fun platformLog(methodName: String) { OSLog.log(methodName) }
```

## Platform Constraints

| Constraint | Details |
|-----------|---------|
| Reflection (`KClass`) | Available on all platforms; generic erasure applies everywhere |
| `JoinPoint.target` | Always available; `null` for top-level functions on all platforms |
| Aspect discovery | Works across the modules of one Gradle build. See [Cross-Module Injection](cross-module-weaving.md) |
