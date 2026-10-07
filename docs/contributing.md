# Contributing

## Development Setup

```bash
git clone https://github.com/Mole-Labs/aspectk.git
cd aspectk
./gradlew build
```

Requirements:
- JDK 17+
- Gradle (via wrapper)

## Project Structure

```
aspectk/
├── aspectk-runtime/      # Public API: @Aspect, @Before, @After, @Around, JoinPoint
├── aspectk-core/         # Compiler plugin implementation (K2 IR)
├── aspectk-core-compat/  # Per-Kotlin-version shims for compiler-internal APIs
├── aspectk-plugin/       # Gradle plugin
├── aspectk-core-tests/   # Multiplatform integration tests
├── buildSrc/             # Convention plugins and build logic
└── sample/               # Compose Multiplatform sample app
```

## Running Tests

```bash
# All tests
./gradlew test

# Compiler plugin tests
./gradlew :aspectk-core:test

# Multiplatform integration tests (JVM)
./gradlew :aspectk-core-tests:jvmTest

# Full check (tests + formatting)
./gradlew check
```

## Code Style

AspectK uses **ktlint** via Spotless:

```bash
# Auto-format
./gradlew spotlessApply

# Check only
./gradlew spotlessCheck
```

All `.kt` and `.kts` files require the Apache 2.0 license header (see `spotless/LICENSE.txt`).

## Making Changes

1. Fork the repository and create a branch from `main`
2. Make your changes with tests
3. Run `./gradlew check` to make sure tests and formatting pass
4. Open a Pull Request

## IR Transformation Pipeline

1. K2 calls `AdviceGenerationExtension.generate()` once per module
2. `AspectVisitor` scans the IR for `@Aspect` objects and fills `AspectLookUp`
3. Hints from earlier builds and from upstream modules are merged into `AspectLookUp`
4. `InheritableVisitor` records overrides for advice declared with `inherits = true`
5. `AspectTransformer` walks every function and injects advice into the ones that match
6. The generators (`MethodSignatureGenerator`, `JoinPointGenerator`,
   `ProceedingJoinPointGenerator`, `AdviceCallGenerator`) produce the IR nodes

The [architecture overview](design-decision/architecture-overview.md) covers each stage in
detail. To add a new advice type, start from `AspectTransformer` and `AdviceCallGenerator`.

## Reporting Issues

Open an issue at [GitHub Issues](https://github.com/Mole-Labs/aspectk/issues) and include:
- Kotlin version
- AspectK version
- Minimal reproduction case
- Expected vs actual behavior
