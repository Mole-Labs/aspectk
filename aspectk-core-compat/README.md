# aspectk-core-compat

Compatibility layer that abstracts breaking changes in the Kotlin compiler's IR API across versions.

## Overview

The Kotlin compiler's internal IR API is not stable. Method signatures and return types can change
even in patch releases. For example, the `IrDeclarationOrigin` property accessors changed their
return type from `IrDeclarationOriginImpl` to `IrDeclarationOrigin` between 2.3.10 and 2.3.20, so
a plugin compiled against one version threw `NoSuchMethodError` when run with the other.

`aspectk-core-compat` puts a stable interface (`IrCompat`) in front of those calls. `aspectk-core`
calls the interface, and each implementation is compiled against its own Kotlin compiler version.

## Architecture

```
aspectk-core
    └── IrCompat (interface)          ← stable contract, compiled once
         ├── compat-2220/
         │    └── IrCompatImpl2220    ← compiled with Kotlin 2.2.20 compiler
         ├── compat-2310/
         │    └── IrCompatImpl2310    ← compiled with Kotlin 2.3.10 compiler
         ├── compat-2320/
         │    └── IrCompatImpl2320    ← compiled with Kotlin 2.3.20 compiler
         └── compat-2400/
              └── IrCompatImpl2400    ← compiled with Kotlin 2.4.0 compiler
```

### IrCompat interface

Wraps the IR API calls that differ across Kotlin versions:

| Method | Description |
|--------|-------------|
| `instanceReceiverOrigin()` | `IrDeclarationOrigin` for instance receivers |
| `propertyBackingFieldOrigin()` | `IrDeclarationOrigin` for property backing fields |
| `localFunctionForLambdaOrigin()` | `IrDeclarationOrigin` for lambda local functions |
| `valueParameterOrigin()` | `IrDeclarationOrigin` for value parameters |
| `referenceFunctions()` | Resolves IR function symbols by `CallableId` |
| `referenceClass()` | Resolves an IR class symbol by `ClassId` |

### Version selection

`IrCompat.create()` uses `ServiceLoader` to discover all implementations on the classpath at
runtime, then selects the one with the highest `kotlinVersion` that is still `<=` the running
compiler version:

```kotlin
fun create(version: KotlinVersion): IrCompat = ServiceLoader
    .load(IrCompat::class.java, IrCompat::class.java.classLoader)
    .filter { it.kotlinVersion <= version }
    .maxByOrNull { it.kotlinVersion }
    ?: error("No IrCompat found for Kotlin $version")
```

### Why separate submodules?

Each `compat-XXXX` submodule is compiled against its own Kotlin compiler version. A call that
only exists from one version on, such as `finderForBuiltins()` (added in 2.3.20), then compiles
in that submodule without breaking the older ones.

