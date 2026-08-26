# Cross-Module Weaving Design

A design for applying an aspect declared in an upstream module to targets in a downstream module, in a multi-module project. See the [architecture overview](architecture-overview.md) for how the current pipeline works.

## 1. Background and problem statement

AspectK originally only wove **within a single compilation unit** (this doc designs the fix; see §8-9 for what shipped). `IrGenerationExtension` runs per module and can only see the IR of the module currently being compiled, and `AspectLookUp` only exists for the lifetime of that one `generate()` call. Most real KMP/Android projects are multi-module, so single-module weaving alone isn't practical for real use. At the same time, this is exactly where AspectK's core differentiator lives — AspectJ/ASM-style bytecode tools can't cross module boundaries on KMP targets (especially Kotlin/Native), but IR-level access can.

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

### Gradle wiring (as built)

Each compilation gets a pair of Gradle `Configuration`s (`AspectKGradleSubPlugin.registerHintsConfigurations`):

- `aspectkHints<Target><Compilation>Elements` — consumable, exposes this compilation's own `hintsDir` as an artifact.
- `...ElementsClasspath` — resolvable, mirrors this compilation's *own* project dependencies (`compileDependencyConfigurationName.allDependencies.withType(ProjectDependency)`), but pointed at the SAME named elements configuration on each dependency project instead of its default variant.

Naming is a pure function of `(targetName, compilationName)`, so this only propagates automatically to/from other projects that also apply this plugin with a matching target+compilation name.

**Transitivity beyond one hop needed an explicit fix.** The elements config only exposed this module's own artifact by default; the resolvable config's contents (what it pulled in from ITS OWN dependencies) never got re-published onward. A module reachable only indirectly (e.g. a diamond: `feature -> branch-a -> aspect-module` and `feature -> branch-b -> aspect-module`) never saw `aspect-module`'s hints at all. Fixed with `elementsConfig.extendsFrom(resolvableConfig)` — the same pattern Gradle's own `apiElements` uses to propagate `api` dependencies transitively — after which Gradle's ordinary configuration-graph resolution does dedup diamond/transitive cases automatically. Verified with a real diamond-shaped functional test (`DiamondDependencyWeavingFunctionalTest`) asserting the advice fires exactly once, not zero and not twice.

- The consumer (B) resolves the classpath configuration to get the path list, and passes it to the compiler plugin via `SubpluginOption("hintsPath", ...)`.
- `AspectKCommandLineProcessor` has `hintsOutputDir` (single) and `hintsPath` (repeatable) options, stored into `CompilerConfiguration`, read in `AspectKCompilerPluginRegistrar.registerExtensions()` and merged into `AdviceGenerationExtension`'s `externalHints`.
- Task dependencies are wired automatically via the outgoing/incoming configuration relationship — Gradle prevents the classic failure mode of the producer compiling after (or in parallel with) the consumer and the consumer reading stale or missing hints.
- (Lower priority, still open) build cache: `hintsPath` passes absolute paths as plain `SubpluginOption` values, which means (a) no cache hit across machines, and (b) — found during the incremental-compilation work below — the option isn't tracked as a content-sensitive task input, so a consumer's `compileKotlin` can stay UP-TO-DATE and never re-read a producer's changed `hints.json`. `@PathSensitive`/a `FilesSubpluginOption`-style variant would fix both; not yet done.

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

- A multi-module sample where an aspect declared in `:core` correctly weaves into targets in `:feature`, which depends on `:core`. **Done** — `sample/cross-module-core` -> `sample/cross-module-feature`, plus a diamond variant (`cross-module-branch-a`/`b` in between) added later. The diamond sample isn't build-verified yet: it pins the last *published* plugin version, which predates the incremental-compilation and diamond-transitivity fixes in section 9.
- Since the carrier is build-directory-based, there's no JVM/Native split — verify JVM and at least one Kotlin/Native target (e.g. linuxX64) **in the same slice**. **Partially done** — the two-module sample compiles+links on `linuxX64` (execution itself is skipped on a macOS host, expected). The functional-test suite that exercises incremental compilation and the diamond case (section 9) is JVM-only via GradleRunner; it hasn't been re-run against a Native target.
- No modification should be required at the target call site (an annotation is enough — zero configuration on B's side). **Done.**

## 8. Remaining tasks (original plan)

- [x] Serialize advice metadata (`package`/`class`/`function`/`kind`/`targets`/`inherits`) to the build directory as JSON at compile time (manual writer) — `HintRecord`/`HintsCodec`.
- [x] Gradle plugin: register the producer's hints directory as an outgoing configuration/artifact, wire consumer-side resolution and `SubpluginOption` passing — `registerHintsConfigurations`.
- [x] Add a hints-path option to `AspectKCommandLineProcessor`; load and merge in `AspectKCompilerPluginRegistrar`.
- [x] Narrow `AspectContext.advice` to `IrSimpleFunctionSymbol`.
- [x] Implement cross-module advice/aspect symbol resolution (reusing `IrCompat.referenceFunctions`/`referenceClass`).
- [x] Add a `:core` → `:feature` multi-module sample, verify JVM + a Native target.

All of the above shipped, but real Gradle incremental-build testing (not covered by the original plan) surfaced correctness gaps beyond it — see section 9.

## 9. Incremental compilation correctness (found after initial implementation)

`AspectVisitor` discovers `@Aspect`/`@Before` by walking whatever `IrModuleFragment` the K2 compiler hands to `IrGenerationExtension.generate()` — never through a symbol-resolution API. On a real (non-clean) Gradle build, that `moduleFragment` may only contain the files Kotlin's own incremental compiler decided are dirty this round, not the whole module. A Gradle TestKit functional-test suite (`aspectk-plugin/src/functionalTest`, real `GradleRunner` builds, not kctfork) was built specifically to catch this, and found three distinct failure modes, each now fixed:

1. **Same-module, target file edited, aspect file untouched.** The target file is trivially dirty either way, but the untouched aspect file may not be part of this round's `moduleFragment`, so `AspectVisitor` never sees it and the target's own annotation goes unmatched. **Fixed**: `AspectKIrCompilerContext.visitedAspectClassIds` tracks which `@Aspect` classes were actually visited this round; for the ones that weren't, this module's own previously-written `hints.json` is read back and resolved via `IrCompat.referenceClass`/`referenceFunctions` and carried forward into `AspectLookUp`. This also fixes hints.json itself shrinking on a partial incremental rebuild, since the same carry-forward logic feeds what gets written back out.
2. **Same-module, aspect file edited, target file untouched.** The target file isn't even part of this round's compilation unit, so no compiler-plugin-level fix can reach it — nothing to inject into. **Fixed** at the Gradle level instead: `DetectAspectChangeTask` (a Gradle `InputChanges`-based task) inspects only the files that actually changed since its own last successful run, and forces one full (non-incremental) recompile of the compilation when one of them declares or drops an `@Aspect`/`@Before`/`@After`/`@Around` (comment-stripped text search; conservative on added/removed files). This was deliberately scoped narrower than "always disable IC once a module uses AspectK at all" — that would force a full recompile on every future edit, forever, once a module has any aspect content.
3. **Cross-module, hints propagation beyond one dependency hop.** See section 3's "Transitivity beyond one hop needed an explicit fix."

An idea considered and rejected for (2): embedding hints in the compiled artifact (jar/klib) and letting `api` dependency transitivity carry them, instead of the custom Configuration pair. Simpler code, but reopens exactly the klib-resource-API uncertainty section 3 was written to sidestep, and ties us to Kotlin Gradle Plugin's own internal `apiElements`-equivalent configuration per target/compilation, which isn't a stable public surface to build a multi-Kotlin-version-supporting plugin against.
