# Cross-Module Weaving Design

A design for applying an aspect declared in an upstream module to targets in a downstream module, in a multi-module project. See the [architecture overview](architecture-overview.md) for how the current pipeline works.

## 1. Background and problem statement

AspectK currently only weaves **within a single compilation unit**. `IrGenerationExtension` runs per module and can only see the IR of the module currently being compiled, and `AspectLookUp` only exists for the lifetime of that one `generate()` call. Most real KMP/Android projects are multi-module, so single-module weaving alone isn't practical for real use. At the same time, this is exactly where AspectK's core differentiator lives — AspectJ/ASM-style bytecode tools can't cross module boundaries on KMP targets (especially Kotlin/Native), but IR-level access can.

Kotlin compiles modules independently (`A(core)` first, `B(feature)` later). At the time A is compiled, B doesn't exist yet — hasn't even started compiling. At the time B is compiled, A is already a compiled artifact and is no longer subject to IR transformation. So **it's fundamentally impossible for A to push weaving into B**.

### The module boundary itself isn't the barrier

Two things need to be separated to see why.

**(1) Invoking advice code — A's IR isn't needed for this.** The essence of weaving is "insert code into B's function body that calls A's advice function." As established in the [architecture overview](architecture-overview.md), `AdviceCallGenerator` only ever uses `context.advice` via `.symbol` — the advice function's body isn't needed, only the call. `IrCompat.referenceFunctions`/`referenceClass` can already resolve compiled symbols from dependencies.

**(2) Delivering the application rule — this is the actual problem.** B's compiler needs to know "which of B's functions should get a call to A's advice inserted." A's `@Aspect` declaration is already in the past by the time B compiles, so how that information reaches B is the crux of the design.

## 2. Design — pull-based hints

Each module pulls upstream aspect definitions and weaves them locally.

```
Compiling module A:
  AspectVisitor collects local @Aspect declarations (same as pipeline stage 1 today)
  -> serializes a per-advice hint record to hints.json
  -> written to A's build directory (not embedded in the artifact — see section 3)

Gradle:
  A's hint output directory is exposed as an outgoing Configuration/artifact
  B resolves a configuration to obtain the path(s)
  -> passed to B's compiler plugin as a compiler plugin option

Compiling module B:
  CompilerPluginRegistrar loads and merges hints from the given paths
  -> injects into AspectLookUp (appended after the local AspectVisitor results)
  -> AspectTransformer inserts advice calls into matching functions exactly as it does today
     (advice symbols resolved via IrCompat.referenceFunctions/referenceClass)
```

The core idea: **A leaves it behind, B picks it up.** A doesn't need to know B exists. All weaving operations happen inside the module that owns the target code, so this doesn't violate Kotlin's per-module IR compilation model.

## 3. Carrier — a build directory, not an artifact

Where hints are placed and how they're found (the **carrier**) is a separate decision from what format expresses them (the **encoding**). Options considered:

| Approach | JVM | Native |
|---|---|---|
| Embed in artifact (`META-INF/aspectk/hints`) | Works — a jar is a zip, so `ClassLoader.getResources()` can scan the whole classpath | **Blocked** — unlike a jar, a klib has no `ClassLoader.getResources()`-equivalent discovery mechanism for arbitrary embedded resources (would have to go through the klib's own manifest API, support unconfirmed) |
| **Build directory + compiler plugin option** | Works | Works |

**We adopt the build-directory approach.** Rationale: hints aren't information needed at runtime — they're only needed **at the time a downstream module compiles**, so there's no reason for them to travel with the artifact. A build-time intermediate output is sufficient.

This choice eliminates the need to confirm klib resource-API support, per-backend config-key branching, handling both packed and unpacked klib formats, and classpath-scanning logic altogether. **At the carrier level, there's no longer a JVM/Native split** — since the weaving logic is already target-agnostic (section 2, point (1)), this opens up end-to-end support for every target.

### Gradle wiring

- The producer (A)'s compile task exposes the hints directory as an outgoing `Configuration`/artifact. Instead of manually walking the `KotlinCompilation` dependency graph, this lets Gradle's own dependency-graph resolution dedup diamond/transitive cases automatically — resolving the "transitive/diamond" item in section 5's task list without extra implementation.
- The consumer (B) resolves this configuration to get the path list, and passes it to the compiler plugin via `SubpluginOption` (the point in `AspectKGradleSubPlugin.applyToCompilation()` that currently returns `emptyList()`).
- A new option is added to `AspectKCommandLineProcessor` (currently zero options), stored into `CompilerConfiguration`, and read in `AspectKCompilerPluginRegistrar.registerExtensions()` to merge the hints before injecting into `AdviceGenerationExtension`.
- Task dependencies are wired automatically via the outgoing/incoming configuration relationship — Gradle prevents the classic failure mode of the producer compiling after (or in parallel with) the consumer and the consumer reading stale or missing hints.
- (Lower priority) build cache: passing absolute paths as options means no cache hit across machines. Cleaning this up with `@PathSensitive` can be ignored for the initial implementation.

### What we're giving up

This only works within the same Gradle build. Aspects in a module published to Maven Central (etc.) aren't visible to consumers, since the producer's build directory doesn't exist for them. This largely overlaps with the previously accepted limitation that "pre-compiled third-party binaries are out of scope," so it's not much of a new loss. If needed later, artifact embedding or a Gradle variant-based approach can be layered on top.

## 4. Hint schema

**One hint row per advice method.** This maps exactly onto `AspectContext`.

```json
[
  { "package": "com.core", "class": "LoggingAspect", "function": "log", "kind": "BEFORE", "targets": ["com.example.annotations.LogCall"], "inherits": false }
]
```

- There's no separate `aspect` field — the advice's declaring class *is* the aspect, so `package`+`class` reconstructs a `ClassId` (used to build the `irGetObject` dispatch receiver), and `package`+`class`+`function` reconstructs a `CallableId` (used to resolve the `irCall` target).
- `targets` isn't a glob — it's the same list of **exact target annotation FqNames** as today's `@Before(target = X::class)`. No new pointcut matcher is introduced.
- There's no `order` field — local advice always comes first (preserving current single-module behavior unchanged), then dependency hints get merged into `AspectLookUp` in whatever order Gradle resolved them. An explicit integer order field would force different module authors to hand-coordinate integers, with real collision risk (both picking `order=1`) — exposing a control cross-module that doesn't even exist in a single module today would be unnecessary API surface (YAGNI). The ordering of multiple advices on one function is handled separately in [Multi-`@Around` and the Ordering Engine](multi-around-and-ordering.md).
- Encoding: a manual writer/parser in `:core` (~20 lines), no new dependency. The schema is flat and simple enough that pulling in kotlinx.serialization or similar as a new multiplatform-compiler-plugin dependency isn't justified (confirmed no JSON library exists anywhere in the project).

## 5. Core IR changes

- Narrow `AspectContext.advice: IrFunction` to `IrSimpleFunctionSymbol`. Confirmed across the whole codebase that `context.advice` has no use besides the 3 `.symbol` access sites in `AdviceCallGenerator.kt:59,126,211` — a safe narrowing.
  - Local advice: `AspectVisitor` plugs in `func.symbol` exactly as it does today.
  - Cross-module advice: the `CallableId` reconstructed from a hint is resolved via `IrCompat.referenceFunctions(pluginContext, callableId)` into a symbol. `aspect: IrClassSymbol` is resolved the same way via `referenceClass`.
  - Both paths converge on the same `AspectContext` constructor.
- `AspectTransformer`/`AdviceCallGenerator`/the generators need **no code changes** — since they already only work with symbols, they behave identically regardless of whether a symbol came from the local module or was resolved cross-module.

## 6. Out of scope (YAGNI)

- Glob-based pointcut matching — the existing exact-annotation-FqName targeting is kept.
- An explicit `order` API — see section 4.
- Aspects in published binaries (Maven Central etc.) — see "What we're giving up" in section 3.
- A new serialization library — see section 4.

## 7. Acceptance criteria

- A multi-module sample where an aspect declared in `:core` correctly weaves into targets in `:feature`, which depends on `:core`.
- Since the carrier is build-directory-based, there's no JVM/Native split — verify JVM and at least one Kotlin/Native target (e.g. linuxX64) **in the same slice**. If adding a target turns out to be more than "flip it on in the sample," that's a signal that target-dependence has crept back into the weaving logic.
- No modification should be required at the target call site (an annotation is enough — zero configuration on B's side).

## 8. Remaining tasks

- [ ] Serialize advice metadata (`package`/`class`/`function`/`kind`/`targets`/`inherits`) to the build directory as JSON at compile time (manual writer)
- [ ] Gradle plugin: register the producer's hints directory as an outgoing configuration/artifact, wire consumer-side resolution and `SubpluginOption` passing
- [ ] Add a hints-path option to `AspectKCommandLineProcessor`; load and merge in `AspectKCompilerPluginRegistrar`
- [ ] Narrow `AspectContext.advice` to `IrSimpleFunctionSymbol`
- [ ] Implement cross-module advice/aspect symbol resolution (reusing `IrCompat.referenceFunctions`/`referenceClass`)
- [ ] Add a `:core` → `:feature` multi-module sample, verify JVM + a Native target
