# Changelog

All notable changes to this project will be documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
