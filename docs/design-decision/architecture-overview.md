# AspectK Compiler Plugin Architecture Overview

How the injection pipeline works, for reference when designing new features.

## Entry point

`AspectKCompilerPluginRegistrar` (`aspectk-core/.../AspectKCompilerPluginRegistrar.kt`) is auto-registered with the K2 compiler via `@AutoService(CompilerPluginRegistrar::class)`. In `registerExtensions()`, it picks the IR compatibility layer for the current Kotlin version with `IrCompat.create(KotlinVersion.CURRENT)`, then registers `AdviceGenerationExtension` as an `IrGenerationExtension` through `irCompat.registerIrGenerationExtension(...)` instead of calling the compiler API directly. The supertype of `IrGenerationExtension.Companion` changed in a binary-incompatible way at Kotlin 2.4.0 (KT-83341), so each `IrCompat` implementation module needs bytecode compiled against its own pinned `kotlin-compiler` version.

`AspectKCommandLineProcessor` handles the `-P plugin:<id>:<key>=<value>` compiler options: `hintsOutputDir` (where this module writes its own `hints.json`) and `hintsPath` (repeatable, a dependency's `hints.json` directory to read). See [cross-module injection](cross-module-weaving.md).

## IR generation pipeline (`AdviceGenerationExtension.generate()`)

Runs once per module, in this order:

1. `moduleFragment.acceptChildren(AspectVisitor(...), null)` scans `@Aspect` declarations, populates `AspectLookUp`, records one `HintRecord` per advice into `localHints`, and records every class it visited this round in `visitedClassIds`.
2. Hints merge. Entries in this module's previous `hints.json` whose class was not visited this round are carried forward, as long as their symbols still resolve through `IrCompat.referenceClass`/`referenceFunctions`. Hints from `hintsPath` (other modules) are resolved the same way. Both are added to `AspectLookUp` after the local results. See [cross-module injection](cross-module-weaving.md) for why this exists.
3. `moduleFragment.acceptChildren(InheritableVisitor(...), null)` tracks override relationships for targets where `inherits = true`.
4. `moduleFragment.transform(AspectTransformer(...), null)` inserts the advice calls into function bodies.

All of these share one `AspectKIrCompilerContext` (pluginContext + irCompat + `AspectLookUp`).

## Stage 1: `AspectVisitor`, declaration collection

Only classes annotated `@Aspect` are processed (`canSkip`). It walks the functions inside the class, finds `@Before`/`@After`/`@Around` annotations, unpacks each annotation's `target` vararg argument (an array of class references), and registers one `AspectContext` per target annotation FqName into `AspectLookUp`.

**Pointcut model**: AspectK does no glob or path matching. It matches on the exact FqName of the target annotation. `@Before(target = [Logged::class])` means "any function annotated `@Logged`", whatever package the function is in.

## Stage 2: `AspectLookUp` / `AspectContext`, data model

`aspectk-core/.../ir/AspectContext.kt`. The relationship is n:m. One target annotation can have several advices, and one advice can have several targets.

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

When several `AspectContext`s target the same annotation, their order is the order in which `AspectVisitor` walks the IR. Users cannot control it, and it is an implementation detail.

Thread safety comes from `ConcurrentHashMap` plus `Collections.synchronizedList/Set`, which parallel compilation needs.

## Stage 3: `InheritableVisitor`, inheritance tracking

For target annotations that have at least one advice declared with `inherits = true`, it walks each function's `allOverridden()` and records in `AspectLookUp.overriddenDeclarations` which targets the override inherited. This is how an override without the annotation still receives advice.

## Stage 4: `AspectTransformer`, injection

Visits every `IrSimpleFunction` as an `IrElementTransformerVoidWithContext` (`aspectk-core/.../ir/AspectTransformer.kt`).

- Fake overrides are skipped (`declaration !is IrFunctionImpl`).
- Finds the target annotations present on the function (`targetAnnotations`); if any, calls `generateInner()`.
- The inheritance case is handled separately in `generateIfOverridden()` (reuses `generateInner()` with `checkInherits = true`).

The order inside `generateInner()` matters:

1. Generate the `MethodSignature` as a static property, once, cached in a `$MethodSignatures` inner object.
2. `contexts.forEach` handles AROUND/AFTER, and each one wraps the body as built so far. `@After` wraps it in place in `try { ... } finally { after }`; `@Around` moves it into the `proceed` listener lambda and replaces it with the advice call.
3. **`@Before` is always prepended last**, so it stays outside everything `@After`/`@Around` wrapped.

Ordering is only supported for multiple `@Before` advices. Combining `@After` with `@Around`, or applying more than one `@After` or `@Around` to a function, is not supported yet and may run in an unexpected order.

## Generators (`ir/generator/`)

| Class | Responsibility |
|---|---|
| `MethodSignatureGenerator` | Generates a function's signature as a `MethodSignature` IR expression, cached as a static property |
| `JoinPointGenerator` | Generates the `DefaultJoinPoint` constructor call IR used by `@Before`/`@After` |
| `ProceedingJoinPointGenerator` | Generates the `DefaultProceedingJoinPoint` for `@Around`. Moves the function body into the `proceed` listener lambda, reading parameters back from `args` and retargeting returns to the lambda |
| `AdviceCallGenerator` | Assembles the pieces above into the final `irCall(context.advice.symbol)` call site, and wraps the body in `try/finally` for `@After` |

**Important**: `AdviceCallGenerator` uses `context.advice` only through `.symbol`, and builds the `irGetObject` dispatch receiver from `context.aspect` (already an `IrClassSymbol`). It never needs the advice function's body. If the symbol resolves, the call is generated the same way wherever the symbol came from. The [cross-module injection design](cross-module-weaving.md) rests on this.

## Utilities

- `IrCompat` (`aspectk-core-compat/`) is a compatibility layer over IR API differences between Kotlin versions. There is one implementation module per API-shape break (`compat-2220`/`2310`/`2320`/`2400`), each compiled against its own pinned `kotlin-compiler` version and picked at runtime via `ServiceLoader` + `KotlinVersion.CURRENT`. `referenceFunctions(pluginContext, callableId)` and `referenceClass(pluginContext, classId)` also resolve symbols from dependencies, including already-compiled modules, which is what makes cross-module injection possible.
- `IrExtension.kt` holds IR-builder helpers such as `createIrListOf`, `createArgsListOf`, `createKClassExpression` and `withIrBuilder`.
- `Util.kt` holds `reportCompilerBug()`, the shared error raised when an internal plugin invariant breaks. It asks the user to file an issue.
- `Tracer.kt` traces (logs and times) the advice-generation stages per module.

## Summary: one module's injection flow

```
IrGenerationExtension.generate(moduleFragment)
  └─ AspectVisitor          : scan @Aspect -> populate AspectLookUp, record localHints, track visitedClassIds
  └─ hints merge             : carry-forward (same-module, unvisited classes) + hintsPath (other modules)
  └─ InheritableVisitor      : track overrides for inherits=true targets
  └─ AspectTransformer       : visit every IrSimpleFunction
       └─ has target annotation? -> generateInner()
            ├─ generate/cache MethodSignature
            ├─ process AROUND/AFTER contexts (each wraps the body so far)
            └─ process BEFORE contexts (prepended last)
```

`AspectLookUp` still only exists for one `generate()` call. `hints.json` (see [cross-module injection](cross-module-weaving.md)) persists enough per-advice metadata across modules and builds to rebuild `AspectLookUp` for advice this round's IR never saw, whether that is a dependency's aspect or this module's own aspect in a file an incremental round didn't touch. Kotlin's incremental compilation hands `generate()` only the dirty subset of a module's files, and that is what this mechanism works around. The [incremental-compilation section](cross-module-weaving.md#6-incremental-compilation-correctness) of the cross-module design describes the failure modes behind it and how each was fixed.
