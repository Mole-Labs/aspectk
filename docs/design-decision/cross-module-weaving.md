# Cross-Module Injection Design

How an aspect declared in an upstream module gets applied to targets in a downstream module of a multi-module project. See the [architecture overview](architecture-overview.md) for how the pipeline works.

## 1. Background and problem statement

AspectK originally injected advice only within a single compilation unit. This document is the design that fixed that, and sections 5 and 6 record what shipped. `IrGenerationExtension` runs per module and sees only the IR of the module being compiled, and `AspectLookUp` exists only for that one `generate()` call. Most KMP and Android projects are multi-module, so single-module injection is not enough in practice. It is also where IR-level injection has an edge: AspectJ/ASM-style bytecode tools can't cross module boundaries on KMP targets, Kotlin/Native in particular.

Kotlin compiles modules independently, `A(core)` first and `B(feature)` later. When A is compiled, B hasn't started compiling. When B is compiled, A is already a compiled artifact and no longer goes through IR transformation. So A cannot push advice into B.

### The module boundary itself isn't the barrier

Two things need to be separated to see why.

**(1) Calling the advice does not need A's IR.** Injection means inserting a call to A's advice function into B's function body. As the [architecture overview](architecture-overview.md) explains, `AdviceCallGenerator` uses `context.advice` only through `.symbol`. It needs the call, not the advice function's body, and `IrCompat.referenceFunctions`/`referenceClass` can already resolve compiled symbols from dependencies.

**(2) Delivering the application rule is the actual problem.** B's compiler has to know which of B's functions should get a call to A's advice. A's `@Aspect` declaration was compiled before B, so the design question is how that information reaches B.

## 2. Design: pull-based hints

Each module pulls upstream aspect definitions and injects their advice locally.

```
Compiling module A:
  AspectVisitor collects local @Aspect declarations (same as pipeline stage 1 today)
  -> serializes a per-advice hint record to hints.json
  -> written to A's build directory (not embedded in the artifact, see section 3)

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

A leaves the hints behind and B picks them up. A doesn't need to know B exists. All injection happens inside the module that owns the target code, which keeps to Kotlin's per-module IR compilation model.

## 3. Carrier: a build directory, not an artifact

Where hints are placed and how they're found (the carrier) is a separate decision from the format they're written in (the encoding). Options considered:

| Approach | JVM | Native |
|---|---|---|
| Embed in artifact (`META-INF/aspectk/hints`) | Works. A jar is a zip, so `ClassLoader.getResources()` can scan the whole classpath | **Blocked.** A klib has no equivalent of `ClassLoader.getResources()` for arbitrary embedded resources. It would have to go through the klib's own manifest API, and support for that is unconfirmed |
| **Build directory + compiler plugin option** | Works | Works |

We went with the build directory. Hints aren't needed at runtime, only when a downstream module compiles, so they have no reason to travel with the artifact. A build-time intermediate output is enough.

This removes the need to confirm klib resource-API support, to branch config keys per backend, to handle both packed and unpacked klibs, and to scan the classpath. The carrier has no JVM/Native split, and the injection logic was already target-agnostic (section 1, point (1)), so the feature works end to end on every target.

### Gradle wiring (as built)

Each compilation gets a pair of Gradle `Configuration`s (`AspectKGradleSubPlugin.registerHintsConfigurations`):

- `aspectkHints<Target><Compilation>Elements` is consumable and exposes this compilation's own `hintsDir` as an artifact.
- `...ElementsClasspath` is resolvable. It mirrors this compilation's own project dependencies (`compileDependencyConfigurationName.allDependencies.withType(ProjectDependency)`), but points at the elements configuration of the same name on each dependency project instead of its default variant.

The names are a pure function of `(targetName, compilationName)`, so hints only flow between projects that apply this plugin with a matching target and compilation name.

**Transitivity beyond one hop needed an explicit fix.** By default the elements configuration exposed only this module's own artifact. What the resolvable configuration pulled in from its own dependencies was never re-published. A module reachable only indirectly, as in the diamond `feature -> branch-a -> aspect-module` and `feature -> branch-b -> aspect-module`, never saw `aspect-module`'s hints. The fix is `elementsConfig.extendsFrom(resolvableConfig)`, the pattern Gradle's own `apiElements` uses to propagate `api` dependencies. With it, Gradle's ordinary configuration-graph resolution deduplicates diamond and transitive cases. The diamond functional tests (`ComplexModuleIncrementalWeavingTest`, `HintGenerationTest`) assert that the advice fires exactly once.

- The consumer (B) resolves the classpath configuration to get the path list, and passes it to the compiler plugin via `SubpluginOption("hintsPath", ...)`.
- `AspectKCommandLineProcessor` has the options `hintsOutputDir` (single) and `hintsPath` (repeatable). They are stored in `CompilerConfiguration`, read in `AspectKCompilerPluginRegistrar.registerExtensions()` and merged into `AdviceGenerationExtension`'s `externalHints`.
- Task dependencies come from the outgoing/incoming configuration relationship. Gradle therefore never compiles the producer after, or in parallel with, the consumer, which would leave the consumer reading stale or missing hints.
- **A project dependency doesn't have to apply this plugin.** AspectK is opt-in per module. A plain data module can sit between two participating modules and never register a hints-elements configuration. Resolving the classpath configuration strictly failed the whole build in that case ("no variant with that configuration name exists"), so it is resolved through a lenient `artifactView` and a non-participating dependency is skipped.
- **Associated compilations.** A `test` compilation sees `main`'s declarations without a project dependency, so `main`'s hints never arrive through the configuration. The hints directories of `allAssociatedCompilations` are added to `externalHints` directly.
- **Task inputs.** `externalHints` is declared as an input of the Kotlin compile task (`aspectkExternalHints`, relative path sensitivity), so a changed `hints.json` upstream reruns the consumer's compilation instead of leaving it UP-TO-DATE.
- (Still open) build cache: `hintsPath` passes absolute paths as plain `SubpluginOption` values, so the compiler arguments differ between machines.

### What we're giving up

This only works within one Gradle build. Aspects in a module published to Maven Central or elsewhere aren't visible to consumers, because the producer's build directory doesn't exist for them. Pre-compiled third-party binaries were already out of scope, so little is lost. If it is needed later, artifact embedding or a Gradle variant-based approach can be layered on top.

## 4. Hint schema

**One hint row per advice method.** This maps exactly onto `AspectContext`.

```json
[
  { "package": "com.core", "class": "LoggingAspect", "function": "log", "kind": "BEFORE", "targets": ["com.example.annotations.LogCall"], "inherits": false }
]
```

- There's no separate `aspect` field. The advice's declaring class is the aspect, so `package`+`class` reconstructs a `ClassId` (used to build the `irGetObject` dispatch receiver), and `package`+`class`+`function` reconstructs a `CallableId` (used to resolve the `irCall` target).
- `targets` isn't a glob. It's the same list of exact target annotation FqNames as `@Before(target = X::class)`, and no new pointcut matcher is introduced.
- There's no `order` field. Local advice comes first, which keeps single-module behavior unchanged, and dependency hints are then merged into `AspectLookUp` in whatever order Gradle resolved them. An explicit integer order would make different module authors coordinate integers by hand, and two of them picking `order=1` is a real risk. A single module has no such control today, so adding one across modules would be unnecessary API surface (YAGNI).
- Encoding: a manual writer/parser in `:core` (~20 lines) with no new dependency. The schema is flat, and there is no JSON library anywhere in the project, so adding kotlinx.serialization or similar to a compiler plugin for this isn't justified.

## 5. Core IR changes

- Narrow `AspectContext.advice: IrFunction` to `IrSimpleFunctionSymbol`. `context.advice` is only ever read through `.symbol`, in `AdviceCallGenerator`, so the narrowing is safe.
  - Local advice: `AspectVisitor` passes `func.symbol`, as before.
  - Cross-module advice: the `CallableId` reconstructed from a hint is resolved into a symbol via `IrCompat.referenceFunctions(pluginContext, callableId)`. `aspect: IrClassSymbol` is resolved the same way via `referenceClass`.
  - Both paths end at the same `AspectContext` constructor.
- `AspectTransformer`, `AdviceCallGenerator` and the other generators needed no code changes. They already worked only with symbols, so it makes no difference whether a symbol is local or was resolved across modules.

All of this shipped. Testing against real incremental Gradle builds, which the original plan did not cover, then turned up further correctness gaps. Section 6 covers them.

## 6. Incremental compilation correctness

`AspectVisitor` discovers `@Aspect`/`@Before` by walking the `IrModuleFragment` the K2 compiler hands to `IrGenerationExtension.generate()`, never through a symbol-resolution API. On a non-clean Gradle build, that `moduleFragment` may contain only the files Kotlin's incremental compiler considers dirty this round, not the whole module. The Gradle TestKit functional tests (`aspectk-plugin/src/functionalTest`, real `GradleRunner` builds, not kctfork) were written to catch this. They found three failure modes, all fixed:

1. **Same module, target file edited, aspect file untouched.** The target file is dirty, but the untouched aspect file may not be in this round's `moduleFragment`, so `AspectVisitor` never sees it and the target's annotation goes unmatched. **Fixed**: `AspectKIrCompilerContext.visitedClassIds` records which classes were visited this round. For the rest, this module's previous `hints.json` is read back, resolved via `IrCompat.referenceClass`/`referenceFunctions`, and carried forward into `AspectLookUp`. Entries whose symbols no longer resolve are dropped, so a deleted aspect does not live on in `hints.json`. The same carry-forward feeds what gets written back out, which also stops `hints.json` from shrinking on a partial rebuild.
2. **Same module, aspect file edited, target file untouched.** The target file isn't part of this round's compilation, so the compiler plugin has nothing to inject into. **Fixed** at the Gradle level: `DetectAspectChangeTask`, a Gradle `InputChanges`-based task, inspects only the files that changed since its last successful run. It forces one full (non-incremental) recompile of the compilation when one of them declares or drops an `@Aspect`/`@Before`/`@After`/`@Around`. The check is a comment-stripped text search, compared against the previous `hints.json` so that deleting an unrelated file does not trigger it. Each detected change is applied by one compile and then marked consumed. Disabling incremental compilation for any module that uses AspectK would have been simpler, but it would force a full recompile on every later edit.
3. **Cross-module, hints propagation beyond one dependency hop.** See "Transitivity beyond one hop needed an explicit fix" in section 3.

One alternative to the custom configuration pair was considered and rejected: embedding hints in the compiled artifact (jar/klib) and letting `api` dependency transitivity carry them. The code would be simpler, but it brings back the klib resource-API uncertainty that section 3 avoids. It would also tie the plugin to the Kotlin Gradle Plugin's internal `apiElements`-equivalent configuration per target and compilation, which is not a stable public surface for a plugin that supports several Kotlin versions.
