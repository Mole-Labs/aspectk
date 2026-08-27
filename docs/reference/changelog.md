# Changelog

All notable changes to this project will be documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.1]

### Fixed

- **`@Before`/`@After` now weave correctly into `suspend` functions whose body actually
  suspends** (e.g. calls `delay`) — the generated local wrapper function is now declared
  `suspend` when the target is, matching the target's continuation.

### Added

- **`@Around` on `suspend` functions**, via a new `SuspendProceedingJoinPoint` — advice can be
  declared `suspend` and call `proceed()` to resume the suspending target body.

## [0.3.0]

### Added

- **Cross-module weaving.** An `@Aspect` declared in one module now correctly weaves into
  targets in a different, downstream module that depends on it — no configuration needed
  beyond applying the plugin to both modules. Works transitively through any dependency depth,
  including diamond-shaped graphs, and a module that never uses AspectK doesn't need the
  plugin applied at all, even sitting between two participating modules.
- **Kotlin 2.4.0 / 2.4.10 support**, via a new `aspectk-core-compat:compat-2400` module.
  Supported Kotlin range is now 2.2.20 ~ 2.4.10 (see the
  [compatibility table](compatibility.md)).
- AspectK no longer requires the Android Gradle Plugin on the classpath — applying it to a
  plain (non-Android) Kotlin Multiplatform module now works correctly.

### Fixed

- **Incremental-build correctness.** Real (non-clean) Gradle builds could previously miss
  re-weaving in three cases, all now fixed:
    - Editing only a target file (aspect file untouched) could leave the target unwoven if the
      aspect's file wasn't part of that incremental round.
    - Editing only an aspect file (target file untouched) could leave a stale target unwoven,
      since Kotlin's own incremental compiler wouldn't recompile it — a full recompile is now
      forced only when a file that actually changed declares or drops aspect content.
    - Cross-module hints only propagated one dependency hop; a target reachable only
      transitively (e.g. through a diamond) could miss the aspect's hints entirely.

## [0.2.2]

### Fixed

- **`@Around` on functions with complex bodies now compiles correctly** — the IR deep-copy
  of the local function body was performed statement-by-statement, giving each call its own
  `SymbolRemapper`. References between local variables (e.g. `val b = a + 1`) were left
  pointing to the outer function's symbols, which the JVM lowering phase misidentified as
  closure captures. Once the outer body was cleared the missing frame slot caused
  `IllegalStateException: No mapping for symbol` at codegen. The entire body is now
  deep-copied in a single pass so all intra-body references are correctly remapped.
  ([#AroundLocalFunctionGenerationTest](https://github.com/mole-labs/aspectk))

- **`@Around` on functions containing lambdas or nested local functions no longer returns
  wrong values** — `BodyTransformer.visitReturn` previously redirected every `IrReturn` to
  the generated `$<name>` local function, including returns inside nested lambdas and local
  functions. This caused constructs such as `listOf(1,2,3).sumOf { it + base }` to
  short-circuit after the first element. The transformer now only redirects returns that
  originally targeted the outer function.

### Added

- **Extension functions on `JoinPoint`** (`JoinPointExtensions.kt`):
    - `getArg<T>(name)` — retrieves a named argument, cast to `T`; throws on missing name
    - `getArgOrNull<T>(name)` — same as above but returns `null` on missing name or cast failure
    - `getTarget<T>()` — casts `target` to `T`; throws on `null` or cast failure
    - `getTargetOrNull<T>()` — casts `target` to `T`; returns `null` otherwise
    - `findAnnotation<T>()` — finds `AnnotationInfo` by annotation type on the intercepted function

- **Extension functions on `MethodSignature`** (`MethodSignatureExtensions.kt`):
    - `findAnnotation<T>()` — finds `AnnotationInfo` by annotation type in `annotations`

- **Extension functions on `AnnotationInfo`** (`AnnotationInfoExtensions.kt`):
    - `getArg<T>(paramName)` — retrieves a named annotation argument, cast to `T`
    - `getArgOrNull<T>(paramName)` — same but returns `null` on missing name or cast failure

## [0.2.0]

### Added
- `@After` advice annotation: runs after the target function body in a `finally` block, regardless of whether an exception was thrown
- `@Around` advice annotation: replaces the target function call; the original body is invoked via `ProceedingJoinPoint.proceed()`
- `ProceedingJoinPoint` interface with `proceed()` and `proceed(vararg args)` for argument substitution
- `DefaultProceedingJoinPoint` runtime implementation generated at each `@Around` call site
### Changed
- Advice generation pipeline restructured: each `AspectContext` is now dispatched directly to its generator, eliminating redundant internal lookups

## [0.1.1]

### Added
- Kotlin version validation: AspectK now throws a `GradleException` at configuration time if the project's Kotlin compiler version is not in the supported range
### Changed
- Kotlin version updated to 2.3.10
- `pluginId` in `AspectKCompilerPluginRegistrar` now references `BuildConfig.COMPILER_PLUGIN_ID` instead of a hardcoded string

## [0.1.0] — Initial Release

### Added
- `@Aspect` annotation to mark aspect classes
- `@Before` advice annotation with `target` and `inherits` parameters
- `JoinPoint` interface with `target`, `signature`, and `args`
- `MethodSignature` with full compile-time metadata
- `MethodParameter` with type, name, annotations, and nullability info
- `AnnotationInfo` for runtime-accessible annotation metadata
- Kotlin Multiplatform support (JVM, JS, WASM, Native Tier 1–3)
- K2 IR-based compiler plugin (`AspectKCompilerPluginRegistrar`)
- Gradle plugin (`io.github.mole-labs.aspectk.compiler`)
- Many-to-many aspect-to-target relationships
- Inheritance support via `inherits = true`
- Thread-safe `AspectLookUp` implementation
