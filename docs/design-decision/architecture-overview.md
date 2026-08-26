# AspectK Compiler Plugin Architecture Overview

A description of how the weaving pipeline actually works, for reference when designing new features.

## Entry point

`AspectKCompilerPluginRegistrar` (`aspectk-core/.../AspectKCompilerPluginRegistrar.kt`) is auto-registered with the K2 compiler via `@AutoService(CompilerPluginRegistrar::class)`. In `registerExtensions()`, it picks the IR compatibility layer matching the current Kotlin version via `IrCompat.create(KotlinVersion.CURRENT)`, then registers `AdviceGenerationExtension` as an `IrGenerationExtension` through `irCompat.registerIrGenerationExtension(...)` rather than calling the compiler API directly — `IrGenerationExtension.Companion`'s own supertype changed in a binary-incompatible way at Kotlin 2.4.0 (KT-83341), so each `IrCompat` implementation module needs its own compiled bytecode bound against its own pinned `kotlin-compiler` version.

`AspectKCommandLineProcessor` is the hook for `-P plugin:<id>:<key>=<value>` compiler options: `hintsOutputDir` (where this module writes its own `hints.json`) and `hintsPath` (repeatable — a dependency's `hints.json` directory to read). See [cross-module weaving](cross-module-weaving.md).

## IR generation pipeline (`AdviceGenerationExtension.generate()`)

Runs once per module, in this order (`aspectk-core/.../ir/AdviceGenerationExtension.kt:46-69`):

1. `moduleFragment.acceptChildren(AspectVisitor(...), null)` — scans `@Aspect` declarations, populates `AspectLookUp`, records one `HintRecord` per advice into `localHints`, and tracks which `@Aspect` classes were actually visited this round (`visitedAspectClassIds`).
2. Hints merge: `@Aspect` classes this round didn't visit are carried forward by re-reading this module's own previous `hints.json` and resolving them via `IrCompat.referenceClass`/`referenceFunctions`; hints from `hintsPath` (other modules) are merged the same way. Both get added into `AspectLookUp` after the local results. See [cross-module weaving](cross-module-weaving.md) for why this exists.
3. `moduleFragment.acceptChildren(InheritableVisitor(...), null)` — tracks override relationships for targets where `inherits = true`.
4. `moduleFragment.transform(AspectTransformer(...), null)` — actually inserts advice calls into function bodies.

All three stages share one `AspectKIrCompilerContext` (pluginContext + irCompat + `AspectLookUp`).

## Stage 1 — `AspectVisitor`: declaration collection

Only classes annotated `@Aspect` are processed (`canSkip`). It walks the functions inside the class, finds `@Before`/`@After`/`@Around` annotations, unpacks each annotation's `target` vararg argument (an array of class references), and registers one `AspectContext` per target annotation FqName into `AspectLookUp`.

**Pointcut model**: AspectK doesn't do glob/path matching — it matches on the **exact target annotation FqName**. `@Before(target = [Logged::class])` means "any function annotated `@Logged`", regardless of the function's package path.

## Stage 2 — `AspectLookUp` / `AspectContext`: data model

`aspectk-core/.../ir/AspectContext.kt`. An n:m relationship — one target annotation can have multiple advices, and one advice can have multiple targets.

```kotlin
internal class AspectLookUp {
    // FqName(target annotation) -> List<AspectContext>
    private val aspectContexts: ConcurrentHashMap<FqName, MutableList<AspectContext>>
    // attributeOwnerId of an overriding function -> set of inherited target FqNames
    private val overriddenDeclarations: ConcurrentHashMap<IrElement, MutableSet<FqName>>
}

internal data class AspectContext(
    val advice: IrSimpleFunctionSymbol,  // the advice function's symbol (not the IrFunction itself --
                                          // AdviceCallGenerator only ever needs .symbol, so this
                                          // resolves the same way whether the advice is local or
                                          // came from another module's hints.json)
    val aspect: IrClassSymbol,    // the @Aspect object the advice belongs to
    val kind: Kind,                // BEFORE / AFTER / AROUND
    val inherits: Boolean = false,
    val methodSignature: IrExpression? = null, // unused (TODO in source) -- always null today
)
```

When multiple `AspectContext`s target the same annotation, their order is determined by **the order in which `AspectVisitor` walks the IR** — there's no way for a user to control it explicitly (it's a private implementation detail).

Thread-safe via `ConcurrentHashMap` + `Collections.synchronizedList/Set` — needed for parallel compilation.

## Stage 3 — `InheritableVisitor`: inheritance tracking

For target annotations that have at least one advice declared with `inherits = true`, it walks each function's `allOverridden()` and records, in `AspectLookUp.overriddenDeclarations`, which targets an override function inherited (via inheritance). This is the mechanism that lets override functions without a direct annotation still receive advice.

## Stage 4 — `AspectTransformer`: actual weaving

Visits every `IrSimpleFunction` as an `IrElementTransformerVoidWithContext` (`aspectk-core/.../ir/AspectTransformer.kt`).

- Fake overrides are skipped (`declaration !is IrFunctionImpl`).
- Finds the target annotations present on the function (`targetAnnotations`); if any, calls `generateInner()`.
- The inheritance case is handled separately in `generateIfOverridden()` (reuses `generateInner()` with `checkInherits = true`).

The order inside `generateInner()` matters:

1. Generate (once per module/file) the `MethodSignature` as a static property — cached in a `$MethodSignatures` inner object.
2. `contexts.forEach` handles AROUND/AFTER — this **completely replaces the body** (`statement.clear()` then rebuild).
3. **`@Before` is always prepended last.** As the comment states: "the body structure is finalized first, so it appears first in the executed statement list when prepended last."

Because of this ordering dependency, **when a single function has 2+ `@Around` advices, each one after the first calls `statement.clear()` on statements the previous one had just written, wiping them out — so effectively only the last-processed one survives.** Chaining multiple `@Around` advices is not currently supported — a known limitation. See [Multi-`@Around` and the Ordering Engine](multi-around-and-ordering.md) for details and the proposed future design.

## Generators (`ir/generator/`)

| Class | Responsibility |
|---|---|
| `MethodSignatureGenerator` | Generates a function's signature as a `MethodSignature` IR expression, cached as a static property |
| `JoinPointGenerator` | Generates the `DefaultJoinPoint` constructor call IR used by `@Before`/`@After` |
| `ProceedingJoinPointGenerator` | Generates the `DefaultProceedingJoinPoint` for `@Around` — builds the lambda that `proceed()` uses to call back into the original function (`localFunc`) |
| `LocalFunctionGenerator` | Copies the original function body into a local function named `$<name>` — a precondition for `@Around`/`@After` to be able to wrap the original logic |
| `TryCatchWrapperGenerator` | Generates the try/finally wrapper used by `@After` |
| `AdviceCallGenerator` | Assembles the pieces above into the final `irCall(context.advice.symbol)` call site |

**Important**: `AdviceCallGenerator` uses `context.advice` **only via `.symbol`** (`aspectk-core/.../generator/AdviceCallGenerator.kt:59,126,211`), and builds the `irGetObject` dispatch receiver from `context.aspect` (already an `IrClassSymbol`). In other words, nothing about the advice function's body — or anywhere else it might come from — is needed: as long as a symbol is resolvable, it works identically regardless of where that symbol came from. This property is the central premise of the [cross-module weaving design](cross-module-weaving.md).

## Utilities

- `IrCompat` (`aspectk-core-compat/`) — a compatibility layer absorbing IR API differences across Kotlin versions: one implementation module per API-shape break (`compat-2220`/`2310`/`2320`/`2400`), each compiled against its own pinned `kotlin-compiler` version, picked at runtime via `ServiceLoader` + `KotlinVersion.CURRENT`. `referenceFunctions(pluginContext, callableId)` / `referenceClass(pluginContext, classId)` can **also resolve symbols from dependencies (including already-compiled modules)** — the technical basis for cross-module weaving being feasible.
- `IrExtension.kt` — IR-builder helpers such as `createIrListOf`, `createKClassExpression`, `withIrBuilder`.
- `Util.kt` — `reportCompilerBug()`: the shared error used when an internal plugin invariant is broken, prompting the user to file an issue.
- `Tracer.kt` — a utility for tracing (logging/timing) the advice-generation stages per module.

## Summary: one module's weaving flow

```
IrGenerationExtension.generate(moduleFragment)
  └─ AspectVisitor          : scan @Aspect -> populate AspectLookUp, record localHints, track visitedAspectClassIds
  └─ hints merge             : carry-forward (same-module, unvisited @Aspect classes) + hintsPath (other modules)
  └─ InheritableVisitor      : track overrides for inherits=true targets
  └─ AspectTransformer       : visit every IrSimpleFunction
       └─ has target annotation? -> generateInner()
            ├─ generate/cache MethodSignature
            ├─ process AROUND/AFTER contexts (body replacement)
            └─ process BEFORE contexts (prepended last)
```

`AspectLookUp` itself still only exists for one `generate()` call — but `hints.json` (see [cross-module weaving](cross-module-weaving.md)) now persists enough per-advice metadata across modules and builds that `AspectLookUp` can be reconstructed for advice this round's IR never directly saw, whether that's a dependency's aspect or this module's own aspect from a file an incremental round didn't touch. Kotlin's incremental compilation only hands `generate()` the dirty subset of a module's files, which is the thing this whole mechanism exists to work around; see cross-module-weaving.md's incremental-compilation section for the two failure modes that motivated it and how each is (or isn't) fixed.
