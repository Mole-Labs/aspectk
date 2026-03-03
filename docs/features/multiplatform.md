# Kotlin Multiplatform Support

AspectK is built for Kotlin Multiplatform from the ground up. The compiler plugin and runtime
library support all major Kotlin targets.

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
    id("io.github.mole-labs.aspectk.compiler") version "LATEST_VERSION"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser() }
}
```

The AspectK Gradle plugin automatically activates the compiler plugin for all configured
targets and adds `aspectk-runtime` as a dependency. No per-target configuration is required.

## Shared Aspects Across Platforms

Define your aspects in `commonMain` as a regular `object` — they compile and run on every platform:

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

Use `expect`/`actual` for platform-specific behavior. Annotate the `expect` declaration —
the annotation propagates to all `actual` implementations and AspectK intercepts each of them:

```kotlin
// commonMain
@Target(AnnotationTarget.FUNCTION)
annotation class Traced

// Target function — @Traced on the expect declaration propagates to all actuals
@Traced
expect fun fetchData(endpoint: String): String

// commonMain — aspect intercepts @Traced on all platforms
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
| Aspect discovery | Only aspects within the same compilation unit are discovered |
