# Kotlin Version Compatibility

## Compiler Plugin API

AspectK uses the K2 IR transformation API (`IrGenerationExtension`). This API was stabilized
in Kotlin 2.0 and requires K2 compiler mode.

| AspectK Version | Supported Kotlin Range |
|-----------------|----------------------|
| 0.1.0 | 2.2.20 ~ 2.2.21 |
| 0.1.1 | 2.2.20 ~ 2.3.10 |

!!! note
    Each AspectK release is tested against a specific Kotlin minor series. Using a Kotlin
    version outside the supported range may cause compilation errors due to K2 IR API changes.

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

AspectK is a compiler plugin — aspects and advice are plain Kotlin code, fully
supported by IntelliJ IDEA and Android Studio. No IDE plugin is required.

Code navigation, refactoring, and completion all work as expected for `@Aspect`
and `@Before`-annotated code.
