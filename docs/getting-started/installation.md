# Installation

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mole-labs/aspectk-plugin.svg)](https://central.sonatype.com/artifact/io.github.mole-labs/aspectk-plugin)

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
aspectk = { id = "io.github.mole-labs.aspectk", version.ref = "aspectk" }
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
| 0.3.0 ~ 0.3.1 | 2.2.20 ~ 2.4.10 |

!!! note
    AspectK uses the K2 compiler IR API. Each release is tied to a specific Kotlin range —
    check the [compatibility table](../reference/compatibility.md) for the full version history
    before upgrading either dependency.
