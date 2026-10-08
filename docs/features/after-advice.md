# `@After` Advice

`@After` advice runs after the target function body, whether the body returned normally or
threw. It behaves like a `finally` block.

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

An `@After` advice method must:

1. Be declared inside an `@Aspect` object
2. Take exactly one parameter, of type `JoinPoint`
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
    try {
        charge(amount)
        return true
    } finally {
        AuditAspect.doAfter(
            DefaultJoinPoint(
                target = this,
                signature = $MethodSignatures.ajc$tjp_0,
                args = listOf(this, amount),
            )
        )
    }
}
```

Key points:

- The original body stays where it is and is wrapped in `try { ... } finally { ... }`.
- The advice runs in the `finally` block, so it runs whether the body succeeds or throws.
  There is no `catch`, so exceptions propagate unchanged.
- A `return` anywhere in the body, including a non-local `return` from an inlined lambda such
  as `forEach`, returns from the function after running the `finally`.
- The body stays inside the target function, so an `inline` function keeps its reified type
  parameters, inlined lambda parameters and non-local returns.

### Up to 0.3.1: local function (deprecated)

AspectK 0.3.1 and earlier copied the body into a local function and called it from a
`try-catch-finally`:

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
        AuditAspect.doAfter(DefaultJoinPoint(...))
    }
}
```

This transformation is deprecated and was replaced by the in-place `try/finally` above, for these
reasons:

- **Inline functions.** Kotlin doesn't allow local functions inside inline functions; the plugin
  bypassed that check, and the backend compiled the local function as a plain method. That method
  can't see the reified type arguments, which only exist where the function is inlined, and it
  can't refer to the function's inline lambda parameters, which aren't values. Both failed at
  compile time. Non-local returns from those lambdas were lost for the same reason. A public
  inline function also inlined a call to a synthetic accessor (`access$<name>$_<name>`) into its
  callers.
- **Implementation complexity.** The copy needed its own symbol remapping, a substitution of every
  parameter read and a rewrite of every `return` that targeted the original function. Two bugs
  fixed in 0.2.2 came from this copy step (see the [changelog](../reference/changelog.md#022)).
  Wrapping the body in place needs none of it.
- **Combining advice.** Each `@After` and `@Around` cleared the body and rebuilt it around the
  same local function, so only the last one processed ran.
- The `catch` that only rethrew added nothing over `finally`.

## Execution Order with Other Advice Types

`@After` runs right after the function body, every time the body runs. On its own, that is once
per call.

When the function also has `@Around` advice, the body only runs when that advice calls
`proceed()`, and `@After` follows the body:

- `proceed()` called once: `@After` runs once, before `proceed()` returns to the `@Around` advice.
- `proceed()` never called: the body doesn't run, so `@After` doesn't either.
- `proceed()` called twice: the body and `@After` both run twice.

Several `@After` advices on one function all run, even if an earlier one throws, in the order
their target annotations are written on the function. See [Advice Ordering](advice-ordering.md)
for the full rules.

## Exception Behaviour

`@After` runs even when the original body throws:

```kotlin
@Aspect
object CleanupAspect {
    @After(target = [Transactional::class])
    fun cleanup(joinPoint: JoinPoint) {
        // Runs even if the body threw
        releaseResources()
    }
}

@Transactional
fun riskyOp() = throw RuntimeException("oops")

// cleanup() runs, then the exception propagates to the caller
```

!!! warning
    `@After` cannot suppress exceptions. The original exception propagates once the `finally`
    block completes. To catch or replace exceptions, use [`@Around`](around-advice.md).

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
