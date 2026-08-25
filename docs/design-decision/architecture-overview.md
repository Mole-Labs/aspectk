# AspectK Compiler Plugin Architecture Overview

A description of how the current (single-module) weaving pipeline actually works, for reference when designing new features.

## Entry point

`AspectKCompilerPluginRegistrar` (`aspectk-core/.../AspectKCompilerPluginRegistrar.kt`) is auto-registered with the K2 compiler via `@AutoService(CompilerPluginRegistrar::class)`. In `registerExtensions()`, it picks the IR compatibility layer matching the current Kotlin version via `IrCompat.create(KotlinVersion.CURRENT)`, then registers `AdviceGenerationExtension` as an `IrGenerationExtension`.

`AspectKCommandLineProcessor` is the hook for `-P plugin:<id>:<key>=<value>` compiler options, but currently `pluginOptions` is empty — there are no options yet.

## IR generation pipeline (`AdviceGenerationExtension.generate()`)

Runs once per module, in this order (`aspectk-core/.../ir/AdviceGenerationExtension.kt:46-69`):

1. `moduleFragment.acceptChildren(AspectVisitor(...), null)` — scans `@Aspect` declarations and populates `AspectLookUp`.
2. `moduleFragment.acceptChildren(InheritableVisitor(...), null)` — tracks override relationships for targets where `inherits = true`.
3. `moduleFragment.transform(AspectTransformer(...), null)` — actually inserts advice calls into function bodies.

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
    val advice: IrFunction,       // the advice function itself (currently a local IrFunction)
    val aspect: IrClassSymbol,    // the @Aspect object the advice belongs to
    val kind: Kind,                // BEFORE / AFTER / AROUND
    val inherits: Boolean = false,
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

- `IrCompat` (`aspectk-core-compat/`) — a compatibility layer absorbing IR API differences across Kotlin versions. Uses `ServiceLoader` to find version-specific implementations and picks the closest match to `kotlinVersion`. `referenceFunctions(pluginContext, callableId)` / `referenceClass(pluginContext, classId)` can **also resolve symbols from dependencies (including already-compiled modules)** — the technical basis for cross-module weaving being feasible.
- `IrExtension.kt` — IR-builder helpers such as `createIrListOf`, `createKClassExpression`, `withIrBuilder`.
- `Util.kt` — `reportCompilerBug()`: the shared error used when an internal plugin invariant is broken, prompting the user to file an issue.
- `Tracer.kt` — a utility for tracing (logging/timing) the advice-generation stages per module.

## Summary: single-module weaving flow

```
IrGenerationExtension.generate(moduleFragment)
  └─ AspectVisitor          : scan @Aspect -> populate AspectLookUp (target FqName -> AspectContext[])
  └─ InheritableVisitor      : track overrides for inherits=true targets
  └─ AspectTransformer       : visit every IrSimpleFunction
       └─ has target annotation? -> generateInner()
            ├─ generate/cache MethodSignature
            ├─ process AROUND/AFTER contexts (body replacement)
            └─ process BEFORE contexts (prepended last)
```

This entire flow happens **within a single module's IR** — `AspectLookUp` only exists for the lifetime of that one `generate()` call for that module and is never persisted anywhere. That's the root cause of why weaving can't cross module boundaries today.
