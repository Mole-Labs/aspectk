# aspectk-core-compat

Compatibility layer that abstracts breaking changes in the Kotlin compiler's IR API across versions.

## Overview

The Kotlin compiler's internal IR API is not stable — method signatures and return types can change
even in minor patch releases. For example, `IrDeclarationOrigin` property accessors changed their
return type from `IrDeclarationOriginImpl` to `IrDeclarationOrigin` between 2.3.10 and 2.3.20,
causing `NoSuchMethodError` at runtime when the plugin was compiled against one version but run
with another.

`aspectk-core-compat` solves this by providing a stable interface (`IrCompat`) that `aspectk-core`
calls at runtime, with each implementation compiled against its specific Kotlin compiler version.

## Architecture

```
aspectk-core
    └── IrCompat (interface)          ← stable contract, compiled once
         ├── compat-2220/
         │    └── IrCompatImpl2220    ← compiled with Kotlin 2.2.20 compiler
         ├── compat-2310/
         │    └── IrCompatImpl2310    ← compiled with Kotlin 2.3.10 compiler
         └── compat-2320/
              └── IrCompatImpl2320    ← compiled with Kotlin 2.3.20 compiler
```

### IrCompat interface

Wraps the IR API calls that differ across Kotlin versions:

| Method | Description |
|--------|-------------|
| `instanceReceiverOrigin()` | `IrDeclarationOrigin` for instance receivers |
| `propertyBackingFieldOrigin()` | `IrDeclarationOrigin` for property backing fields |
| `localFunctionOrigin()` | `IrDeclarationOrigin` for local functions |
| `localFunctionForLambdaOrigin()` | `IrDeclarationOrigin` for lambda local functions |
| `catchParameterOrigin()` | `IrDeclarationOrigin` for catch block parameters |
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

Each `compat-XXXX` submodule is compiled against its corresponding Kotlin compiler version.
This ensures that API calls which only exist in that version (e.g., `finderForBuiltins()` added
in 2.3.20) compile correctly without breaking older implementations.

