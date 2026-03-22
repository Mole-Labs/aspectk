# `@After` Advice

`@After` advice runs **after** the target function body completes, regardless of whether it
returned normally or threw an exception. It is the AspectK equivalent of a `finally` block.

## Basic Usage

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class Audited

@Aspect
object AuditAspect {
    @After(target = [Audited::class])
    fun doAfter(joinPoint: JoinPoint) {
        println("${joinPoint.signature.methodName} finished")
    }
}

class OrderService {
    @Audited
    fun placeOrder(orderId: String) {
        println("Placing order $orderId")
    }
}
```

Calling `OrderService().placeOrder("ORD-001")` prints:

```
Placing order ORD-001
finished
```

## Function Signature Rules

An `@After` advice method **must**:

1. Be declared inside an `@Aspect`-annotated class or object
2. Accept exactly **one parameter** of type `JoinPoint`
3. Return `Unit`

```kotlin
@After(target = [Audited::class])
fun doAfter(joinPoint: JoinPoint) { ... }   // ✅ correct

// ❌ Wrong: no parameter
@After(target = [Audited::class])
fun bad1() { }

// ❌ Wrong: non-Unit return type
@After(target = [Audited::class])
fun bad2(joinPoint: JoinPoint): String = ""
```

## Parameters

```kotlin
@After(
    target = [AnnotationClass::class, AnotherAnnotation::class],
    inherits = false,
)
fun adviceMethod(joinPoint: JoinPoint) { ... }
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `target` | `KClass<out Annotation>` (vararg) | — | One or more annotation classes that identify target functions |
| `inherits` | `Boolean` | `false` | When `true`, also intercepts overriding functions |

## What Gets Compiled

Given this source:

```kotlin
@Audited
fun processPayment(amount: Double): Boolean {
    charge(amount)
    return true
}
```

AspectK transforms it into (pseudocode):

```kotlin
fun processPayment(amount: Double): Boolean {
    fun `$processPayment`(amount: Double): Boolean {
        charge(amount)
        return true
    }
    return try {
        `$processPayment`(amount)
    } catch (e: Throwable) {
        throw e
    } finally {
        AuditAspect.doAfter(
            DefaultJoinPoint(
                target = this,
                signature = $MethodSignatures.ajc$tjp_0,
                args = listOf(amount),
            )
        )
    }
}
```

Key points:

- The original function body is extracted into a **local function** (`$processPayment`).
- The local function is called inside a `try-catch-finally` block.
- `@After` advice fires in the `finally` block — always, whether the body succeeds or throws.
- The `catch` block re-throws the exception so normal exception propagation is preserved.

## Execution Order with Other Advice Types

When `@Before`, `@After`, and `@Around` all target the same function, the execution order is:

```
@Before fires
   ↓
(@Around starts)
   ↓
   [Original body]
   ↓ (finally)
   @After fires
   ↓
(@Around post-proceed logic)
```

`@After` is placed **innermost** — it wraps only the original function body, not the `@Around`
advice call. See [Design Rationale](#design-rationale--after-placement) for why.

## Exception Behaviour

`@After` fires regardless of whether the original body threw:

```kotlin
@Aspect
object CleanupAspect {
    @After(target = [Transactional::class])
    fun cleanup(joinPoint: JoinPoint) {
        // Always runs — even if the body threw
        releaseResources()
    }
}

@Transactional
fun riskyOp() = throw RuntimeException("oops")

// cleanup() still fires; the exception propagates to the caller afterwards
```

!!! warning
    `@After` **cannot suppress** exceptions. The original exception always propagates after
    the `finally` block completes. If you need to catch or replace exceptions, use
    [`@Around`](around-advice.md) instead.

## `@After` on Extension and Top-Level Functions

The `JoinPoint.args` layout follows the same rules as `@Before`:

```kotlin
// Extension function — receiver is args[0]
@TargetAnn
fun MyClass.doWork(x: String) { ... }
// jp.target  → null
// jp.args    → [MyClass instance, x]

// Top-level function
@TargetAnn
fun doWork(x: String) { ... }
// jp.target  → null
// jp.args    → [x]
```

See [Join Points](join-points.md) for the full reference on `target` and `args`.

## Design Rationale — `@After` Placement

`@After` is placed in the `finally` block that wraps **only the original function body** (`$doSomething`),
not the entire `@Around` chain. This is intentional:

1. **`@After`'s contract is "execute after the target function"**, not "execute after all aspects".
   If an `@Around` advice throws before calling `pjp.proceed()`, the original function never ran,
   so `@After` should not fire in that case.

2. **`@Around` is responsible for handling its own exceptions internally.** Wrapping the outer
   `@Around` call with the `finally` block would mean `@After` fires even when `@Around` itself
   fails — which conflates two unrelated concerns.

3. **Predictable execution order**: `@After` fires first (innermost `finally`), then `@Around`'s
   post-`proceed()` logic runs outward. The order is deterministic and mirrors the lexical
   nesting of the generated IR.
