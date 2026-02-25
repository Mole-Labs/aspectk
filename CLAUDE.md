# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AspectK is a Kotlin compiler plugin that provides compile-time Aspect-Oriented Programming (AOP). It uses the K2 IR (Intermediate Representation) transformation API to inject advice code before annotated target functions, with support for Kotlin Multiplatform.

## Common Commands

```bash
# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Run tests with checks (includes spotlessCheck)
./gradlew check

# Run tests for a specific module
./gradlew :core:test
./gradlew :core-tests:jvmTest

# Run a specific test class
./gradlew :core:test --tests "com.mole.core.AspectContextTest"

# Run a specific test method
./gradlew :core:test --tests "com.mole.core.AspectContextTest.add and get AspectContexts for a single FqName"

# Format code (ktlint via Spotless)
./gradlew spotlessApply

# Check formatting without fixing
./gradlew spotlessCheck
```

## Module Architecture

```
plugin
  └── core (compiler plugin implementation)
       └── runtime (public API annotations and runtime classes)

core-tests
  └── runtime (uses annotations in multiplatform tests)
```

### :runtime
Public multiplatform API exposed to users: `@Aspect`, `@Before`, `JoinPoint`, `DefaultJoinPoint`, `MethodSignature`, `MethodParameter`, `AnnotationInfo`. Targets JVM, JS, WASM, and Native (Tier 1–3).

### :core
The Kotlin compiler plugin. Key classes:
- **`AspectKCompilerPluginRegistrar`** – entry point, auto-registered with K2 compiler via Auto Service
- **`AdviceGenerationExtension`** – implements `IrGenerationExtension`; orchestrates the pipeline
- **`AspectVisitor`** – walks the IR tree to collect all `@Aspect` classes and their `@Before` advice methods
- **`AspectLookUp`** – thread-safe `ConcurrentHashMap`-based mapping of target annotation FqNames → `AspectContext` list (many-to-many)
- **`AspectTransformer`** – walks IR functions and injects advice call code at function entry points
- **Generators** (`MethodSignatureGenerator`, `JoinPointGenerator`, `AdviceCallGenerator`) – create the IR nodes that become generated code

### :plugin
Gradle plugin (`AspectKGradleSubPlugin`) implementing `KotlinCompilerPluginSupportPlugin`. Registers the `:core` artifact with the Kotlin compiler for any project applying the plugin.

### :core-tests
Multiplatform test module. Tests use `KotlinCompilation` (kctfork / kotlin-compile-testing) to perform in-memory compilation with the AspectK plugin applied, then verify the compiled output behaves correctly.

### :buildSrc
Convention plugins:
- `kotlin-conventions.gradle.kts` – configures JVM, JS, and WASM multiplatform targets
- `native-conventions.gradle.kts` – configures 16+ native targets (macOS, Linux, iOS, Android, etc.)
- `com.mole.aspectK.build` – AspectK-specific build conventions

## IR Transformation Pipeline

1. `AdviceGenerationExtension.generate()` is called by the K2 compiler
2. `AspectVisitor` scans all IR files, finding `@Aspect` classes and `@Before` methods → populates `AspectLookUp`
3. `InheritableVisitor` (when `inherits = true`) tracks overridden declarations
4. `AspectTransformer` traverses all IR functions; for each function annotated with a target annotation found in `AspectLookUp`, it prepends advice calls using the generators
5. Generators produce IR that instantiates `MethodSignature` and `JoinPoint`, then calls the advice method

## Key Design Decisions

- **Many-to-many advice targeting**: One `@Before` can list multiple target annotation classes; one function can have multiple advice applied.
- **Thread safety**: `AspectLookUp` uses `ConcurrentHashMap` and `Collections.synchronizedList/Set`; tests verify concurrent safety.
- **IR verification in tests**: Compiler args `-Xverify-ir=error` and `-Xverify-ir-visibility` ensure generated IR is valid.
- **Binary compatibility**: `kotlinx.binary-compatibility-validator` runs on `:runtime` and `:plugin` to catch API breaks.
- **Backwards compatibility**: `AspectKBuildExtension.enableBackwardsCompatibility(lowestSupportedKotlinVersion)` controls minimum supported Kotlin version in `:plugin`.

## Code Style

- Formatted with ktlint 1.8.0 via Spotless; indent size 4, no wildcard imports.
- All `.kt` and `.kts` files require the Apache 2.0 license header (see `spotless/LICENSE.txt`).
- Always run `./gradlew spotlessApply` before committing.

## Technology Stack

| Component | Version |
|---|---|
| Kotlin | 2.2.21 |
| JVM target | 17 |
| Kotlin Compile Testing (kctfork) | 0.12.1 |
| JUnit Jupiter | 5.8.1 |
| MockK | 1.13.8 |
| ktlint | 1.8.0 |
