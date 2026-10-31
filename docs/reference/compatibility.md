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
| 0.4.1 ~ 0.4.4 | 2.2.20 ~ 2.4.20 |

!!! note
    Each AspectK release is tested against every Kotlin version in its range. Outside that
    range the K2 IR API may differ, and the plugin fails the build at configuration time.

## iOS / Native ABI Compatibility

iOS and other native targets have a higher minimum Kotlin version than the JVM. `aspectk-runtime`
is published as a klib stamped with the ABI version of the compiler that built it, and a Kotlin
compiler can only read klibs whose ABI version is not newer than its own. The project's Kotlin
therefore has to be at least the minimum listed below. Newer versions in the supported range work.

| AspectK Version | Compiled with Kotlin | klib ABI version | Minimum Kotlin on Native |
|-----------------|---------------------|------------------|--------------------------|
| 0.1.0 ~ 0.1.1 | 2.2.20 | 2.2.0 | 2.2.20 |
| 0.2.0 | 2.3.10 | 2.3.0 | 2.3.0 |
| 0.2.1 ~ 0.4.4 | 2.3.20 | 2.3.0 | 2.3.0 |

!!! warning
    Below the minimum, the plugin's version check still passes and compilation fails instead:

    ```
    w: KLIB resolver: Skipping '.../aspectk-runtime-iosSimulatorArm64Main.klib' having incompatible ABI version '2.3.0'.
    The current Kotlin compiler can consume libraries having ABI version <= '2.2.0'.
    e: KLIB resolver: Could not find ".../aspectk-runtime-iosSimulatorArm64Main.klib"
    ```

    The fix is to upgrade Kotlin. Setting `languageVersion` does not change a klib's ABI version.

The JS and Wasm klibs carry the same ABI version as the native ones.

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
