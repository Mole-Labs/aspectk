# Kotlin Version Compatibility

## Compiler Plugin API

AspectK uses the K2 IR transformation API (`IrGenerationExtension`). This API was stabilized
in Kotlin 2.0 and requires K2 compiler mode.

| AspectK Version | Supported Kotlin Range |
|-----------------|----------------------|
| 0.1.0 | 2.2.20 ~ 2.2.21 |
| 0.1.1 ~ 0.2.0 | 2.2.20 ~ 2.3.10 |
| 0.2.1 | 2.2.20 ~ 2.3.20 |

!!! note
    Each AspectK release is tested against a specific Kotlin minor series. Using a Kotlin
    version outside the supported range may cause compilation errors due to K2 IR API changes.

## iOS / Native ABI Compatibility

iOS and other native targets follow stricter compatibility constraints due to **Kotlin/Native ABI conventions**. Unlike JVM targets where the runtime resolves symbols dynamically, native binaries are linked against a specific Kotlin version's ABI. Using a different Kotlin version than the one the library was compiled with may cause binary incompatibilities.

For iOS targets, use the **exact Kotlin version** listed below:

| AspectK Version | Compiled with Kotlin |
|-----------------|---------------------|
| 0.1.0 ~ 0.1.1 | 2.2.20 |
| 0.2.0 | 2.3.10 |
| 0.2.1 | 2.3.20 |

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

## IDE Support

!!! note "Planned"
    A dedicated IDE plugin is not yet available. Support for IntelliJ IDEA and Android Studio
    (gutter icons, navigation from advice to target, and vice versa) is planned for a future release.
