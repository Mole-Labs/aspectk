# Installation

## Requirements

- Kotlin **2.2.20** or later

## Gradle Setup

### Using Version Catalog (recommended)

Add to `gradle/libs.versions.toml`:

```toml
[versions]
aspectk = "LATEST_VERSION"

[plugins]
aspectk = { id = "io.github.mole-labs.aspectk.compiler", version.ref = "aspectk" }

[libraries]
aspectk-runtime = { module = "io.github.mole-labs:aspectk-runtime", version.ref = "aspectk" }
```

Then in your `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.aspectk)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.aspectk.runtime)
        }
    }
}
```

### Direct Dependency

```kotlin
// build.gradle.kts
plugins {
    id("io.github.mole-labs.aspectk.compiler") version "LATEST_VERSION"
}

dependencies {
    // For JVM-only projects
    implementation("io.github.mole-labs:aspectk-runtime:LATEST_VERSION")
}
```

### Kotlin Multiplatform

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.github.mole-labs.aspectk.compiler") version "LATEST_VERSION"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mole-labs:aspectk-runtime:LATEST_VERSION")
        }
    }
}
```

## Kotlin Version Compatibility

| AspectK Version | Supported Kotlin Range |
|-----------------|----------------------|
| 0.1.0 | 2.2.20 ~ 2.2.21 |

!!! note
    AspectK uses the K2 compiler IR API. Only Kotlin **2.2.x** is supported.
    Each release is tied to a specific Kotlin minor series — check the [compatibility table](../reference/compatibility.md)
    before upgrading either dependency.
