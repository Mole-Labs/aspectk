# Multi-`@Around` and the Ordering Engine (draft / sketch)

> **Status: draft.** Not a finalized design — this transcribes and organizes TODOs/comments already left in the code (`AdviceCallGenerator.buildAroundCallBlock`). A separate brainstorming pass is needed before implementation.

## 1. Current limitation

As described in stage 4 of the [architecture overview](architecture-overview.md), `AspectTransformer.generateInner()` iterates the list of `AspectContext`s targeting the same annotation with `forEach`, calling `generateAroundAdviceCalls`/`generateAfterAdviceCalls` for each `AROUND`/`AFTER` context. Both functions behave like this:

```kotlin
(declaration.body as? IrBlockBody)?.statements?.let { statement ->
    statement.clear()                    // wipes out all existing statements
    statement.add(localFunction)
    statement.add(aroundCallback)        // replaced with this context's callback
}
```

**When a function has 2+ `@Around` advices, each iteration's `statement.clear()` wipes out the previous iteration's result.** `generateLocalFunction` does reuse the local function by name, but what ultimately survives is **only the last `@Around` processed** — the rest disappear silently, with no compile error or warning. This is explicitly noted in `AdviceCallGenerator.buildAroundCallExpression`'s doc comment: *"Only one @Around advice is invoked per target annotation. Supporting multiple chained @Around advices requires an ordering engine (TODO)."*

## 2. Target behavior (draft)

When multiple `@Around` advices target one function, they should be wrapped as a **nested interceptor chain**, the way AspectJ-style tools do. The original draft left in the code:

```
@Target
fun doSomething(arg1: String): String {
    fun $doSomething(arg1: String = arg1): String {
        println("hello aspectk")
        return ""
    }
    return SomeAspect.doAround1(
        ProceedingJoinPoint(...) { args: List<Any?> ->
            SomeAspect.doAround2(
                ProceedingJoinPoint(...) { args: List<Any?> ->
                    try {
                        $doSomething(args[0] as String)
                    } catch (e: Exception) {
                        throw e
                    } finally {
                        SomeAspect.doAfter1(JoinPoint(...))
                    }
                }
            )
        }
    )
}
```

The outermost `@Around` (`doAround1`) runs first; calling `pjp.proceed()` steps into the next `@Around` (`doAround2`); eventually it reaches the original body (`$doSomething`) at the center. Same onion structure as a standard middleware/interceptor chain.

## 3. `@After` placement — an already-settled design principle

Transcribing the rationale already in the comment (`AdviceCallGenerator.kt:190-204`):

> **`@After` sits in the innermost finally block, wrapping only the original function body (`$doSomething`) — not the entire `@Around` chain.** Reasons:
>
> 1. `@After`'s contract is "run after the target function," not "run after all aspects." If an `@Around` throws before calling `pjp.proceed()`, the original function never ran, so `@After` shouldn't fire either.
> 2. `@Around` advice is responsible for handling its own exceptions. Wrapping the outer `@Around` call in the finally block would make `@After` fire even when `@Around` itself fails — conflating two unrelated concerns.
> 3. Right after the original code runs, `@After` fires first (innermost finally), then `@Around`'s post-proceed logic runs outward. The order is deterministic and mirrors the lexical nesting of the generated IR.

This principle should hold as-is when extended to a multi-`@Around` chain: `@After` stays pinned at the innermost position, directly wrapping the original body.

## 4. The ordering engine — what determines wrapping order

Building the chain requires deciding somewhere that "`doAround1` is outer, `doAround2` is inner." A related decision was already made in the [cross-module weaving design](cross-module-weaving.md), the doc that prompted this one:

- No explicit integer `order` field (cross-module integer collisions, YAGNI).
- Instead, **discovery order**: local advice first, then dependency hints merged into `AspectLookUp` in whatever order Gradle resolved them. Since the incremental-compilation fix (cross-module-weaving.md §9), "local" itself can split into two sub-groups on an incremental round — advice from `@Aspect` classes this round's IR walk actually visited, added first, then same-module advice carried forward from a previous round's `hints.json` for classes it didn't. On any clean build the second group is empty, so this only matters if a single target function ends up with `@Around` advices from both groups in the same incremental round — worth a test case whenever this feature is implemented.

Applying the same principle here is the consistent choice: **the order of the `AspectContext` list in `AspectLookUp[target]` = the chain's wrapping order.** No separate ordering concept needs inventing — the essence of this feature is changing `generateInner()` from "each iteration overwrites via `statement.clear()`" to "each iteration folds/nests into the previous one."

```
current: contexts.forEach { context -> ...; statement.clear(); statement.add(callback) }  // only the last one survives
target:  contexts.filter { it.kind == AROUND }.fold(innermost) { acc, context -> wrap(context, acc) }  // nested accumulation
```

In other words, this feature isn't a new sorting algorithm — it's narrowly about fixing `generateInner`/`AdviceCallGenerator` to consume the ordering `AspectLookUp` already has as an **accumulation** instead of "last write wins."

**`@Before` already works this way.** `AdviceCallGenerator.generateAdviceCalls`/`buildCallBlock` is structured as `irBlock { aspectLookUp[target].forEach { context -> ...; +irCall(...) } }` — no `statement.clear()`, so each context's call is **accumulated** (`AdviceCallGenerator.kt:50-66`). So when multiple `@Before`s target one function, they already all run in sequence, in discovery order, today — only `@Around`/`@After` have the "overwrite" bug; `@Before` had the correct "accumulate" pattern from the start. Section 4's `fold` target is really just shaping `@Around`/`@After` to match what `@Before` already does.

## 5. Scope notes

This document is a pre-brainstorming draft. Things still to decide before implementation:

- Whether multiple `@After`s need the same chaining approach, or whether simple sequential execution (the same pattern `@Before` uses) is sufficient.
- The interaction between the `checkInherits` flag (override-function handling) and a chained structure when both apply together.

These items need to be worked through in a separate brainstorming session before implementation starts.
