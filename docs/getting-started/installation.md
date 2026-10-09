# Installation

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mole-labs/aspectk-plugin.svg)](https://central.sonatype.com/artifact/io.github.mole-labs/aspectk-plugin)

## Requirements

- Kotlin 2.2.20 through 2.4.10

## Gradle Setup

Apply the plugin. It adds `aspectk-runtime` to your project.

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
    `aspectk-runtime` is added as an `implementation` dependency. You don't need to declare
    it in any source set.

## Kotlin Version Compatibility

| AspectK Version | Supported Kotlin Range |
|-----------------|----------------------|
| 0.3.0 ~ 0.4.0 | 2.2.20 ~ 2.4.10 |

!!! note
    AspectK uses the K2 compiler IR API, so each release supports a fixed Kotlin range. Check
    the [compatibility table](../reference/compatibility.md) before upgrading either one.
