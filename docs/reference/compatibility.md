# Kotlin Version Compatibility

## Compiler Plugin API

AspectK uses the K2 IR transformation API (`IrGenerationExtension`). This API was stabilized
in Kotlin 2.0 and requires K2 compiler mode.

| AspectK Version | Supported Kotlin Range |
|-----------------|----------------------|
| 0.1.0 | 2.2.20 ~ 2.2.21 |
| 0.1.1 ~ 0.2.0 | 2.2.20 ~ 2.3.10 |
| 0.2.1 ~ 0.2.3 | 2.2.20 ~ 2.3.20 |
| 0.3.0 ~ 0.4.0 | 2.2.20 ~ 2.4.10 |

!!! note
    Each AspectK release is tested against every Kotlin version in its range. Outside that
    range the K2 IR API may differ, and the plugin fails the build at configuration time.

## iOS / Native ABI Compatibility

iOS and other native targets are stricter because of Kotlin/Native ABI conventions. A JVM
runtime resolves symbols dynamically, but a native binary is linked against one Kotlin
version's ABI, and a library compiled with a different version may not be binary compatible.

For iOS targets, use the exact Kotlin version listed below:

| AspectK Version | Compiled with Kotlin |
|-----------------|---------------------|
| 0.1.0 ~ 0.1.1 | 2.2.20 |
| 0.2.0 | 2.3.10 |
| 0.2.1 ~ 0.4.0 | 2.3.20 |

!!! warning
    On iOS targets, using a Kotlin version different from the one listed above is not supported
    and may result in linker errors or unexpected runtime behavior.

## Kotlin Multiplatform Compatibility

AspectK supports all stable Kotlin Multiplatform targets:

| Target Category | Supported |
|-----------------|-----------|
| JVM / Android | ✅ |
| JS (IR mode) | ✅ |
| WASM/JS | ✅ |
| Native Tier 1 | ✅ |
| Native Tier 2 | ✅ |
| Native Tier 3 | ✅ (best-effort) |

!!! warning "Kotlin JS Legacy"
    Kotlin/JS in **Legacy** mode is not supported. Use IR mode only.

## Multi-Module Projects

Since 0.3.0, an `@Aspect` declared in one module is injected into targets in another module that
depends on it. This holds through diamond-shaped dependency graphs and across incremental
Gradle builds. See [Cross-Module Injection](../features/cross-module-weaving.md).

## IDE Support

!!! note "Planned"
    There is no IDE plugin yet. Gutter icons and navigation between advice and target in
    IntelliJ IDEA and Android Studio are planned.
