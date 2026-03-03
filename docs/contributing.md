# Contributing

Thank you for your interest in contributing to AspectK!

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
├── aspectk-runtime/     # Public API: @Aspect, @Before, JoinPoint, etc.
├── aspectk-core/        # Compiler plugin implementation (K2 IR)
├── aspectk-plugin/      # Gradle plugin
├── aspectk-core-tests/  # Multiplatform integration tests
├── buildSrc/            # Convention plugins and build logic
└── sample/              # Example projects
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
3. Run `./gradlew check` to ensure tests and formatting pass
4. Open a Pull Request

## IR Transformation Pipeline

The compiler plugin works as follows:

1. `AdviceGenerationExtension.generate()` is called by K2
2. `AspectVisitor` scans IR for `@Aspect` classes → populates `AspectLookUp`
3. `AspectTransformer` walks IR functions, prepends advice calls for matching functions
4. Generators (`MethodSignatureGenerator`, `JoinPointGenerator`, `AdviceCallGenerator`) produce IR nodes

When adding new advice types (`@After`, `@Around`), start from `AspectTransformer` and
add the corresponding injection logic.

## Reporting Issues

Please open issues at [GitHub Issues](https://github.com/Mole-Labs/aspectk/issues).
Include:
- Kotlin version
- AspectK version
- Minimal reproduction case
- Expected vs actual behavior
