# Installation

## Requirements

- Kotlin **2.2.20** or later

## Gradle Setup

Applying the plugin is all that's needed. `aspectk-runtime` is added to your project automatically.

### Using Version Catalog (recommended)

Add to `gradle/libs.versions.toml`:

```toml
[versions]
aspectk = "LATEST_VERSION"

[plugins]
aspectk = { id = "io.github.mole-labs.aspectk.compiler", version.ref = "aspectk" }
```

Then in your `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.aspectk)
}

```

!!! note
    The plugin automatically adds `aspectk-runtime` as an `implementation` dependency.
    You do not need to declare it manually in any source set.

## Kotlin Version Compatibility

| AspectK Version | Supported Kotlin Range |
|-----------------|----------------------|
| 0.1.0 | 2.2.20 ~ 2.2.21 |

!!! note
    AspectK uses the K2 compiler IR API. Only Kotlin **2.2.x** is supported.
    Each release is tied to a specific Kotlin minor series — check the [compatibility table](../reference/compatibility.md)
    before upgrading either dependency.
