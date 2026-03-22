# `@Around` Advice

`@Around` advice **wraps** the target function call entirely. Unlike `@Before` and `@After`,
an `@Around` advice controls *whether* the original body runs and *what* is returned to the
caller. The original body is invoked by calling `pjp.proceed()` on the injected
`ProceedingJoinPoint`.

## Basic Usage

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class Cached

@Aspect
object CachingAspect {
    private val cache = mutableMapOf<String, Any?>()

    @Around(target = [Cached::class])
    fun doAround(pjp: ProceedingJoinPoint): Any? {
        val key = pjp.signature.methodName + pjp.args.toString()
        return cache.getOrPut(key) { pjp.proceed() }
    }
}

class DataService {
    @Cached
    fun fetch(id: String): String {
        println("fetching $id from DB")
        return "result-$id"
    }
}
```

```kotlin
val svc = DataService()
svc.fetch("42")   // prints "fetching 42 from DB"
svc.fetch("42")   // cached — prints nothing
```

## Function Signature Rules

An `@Around` advice method **must**:

1. Be declared inside an `@Aspect`-annotated class or object
2. Accept exactly **one parameter** of type `ProceedingJoinPoint`
3. Return `Any?`

```kotlin
@Around(target = [Cached::class])
fun doAround(pjp: ProceedingJoinPoint): Any? { ... }  // ✅ correct

// ❌ Wrong: no parameter
@Around(target = [Cached::class])
fun bad1(): Any? = null

// ❌ Wrong: wrong parameter type
@Around(target = [Cached::class])
fun bad2(jp: JoinPoint): Any? = null
```

## Parameters

```kotlin
@Around(
    target = [AnnotationClass::class, AnotherAnnotation::class],
    inherits = false,
)
fun adviceMethod(pjp: ProceedingJoinPoint): Any? { ... }
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `target` | `KClass<out Annotation>` (vararg) | — | One or more annotation classes that identify target functions |
| `inherits` | `Boolean` | `false` | When `true`, also intercepts overriding functions |

## `ProceedingJoinPoint` Interface

```kotlin
interface ProceedingJoinPoint : JoinPoint {
    // Invoke the original function with the original arguments
    fun proceed(): Any?

    // Invoke the original function with substituted regular parameters
    fun proceed(vararg args: Any?): Any?
}
```

`ProceedingJoinPoint` extends `JoinPoint`, so `target`, `signature`, and `args` are all
available.

## What Gets Compiled

Given this source:

```kotlin
@Cached
fun fetch(id: String): String {
    return "result-$id"
}
```

AspectK transforms it into (pseudocode):

```kotlin
fun fetch(id: String): String {
    fun `$fetch`(id: String): String {
        return "result-$id"
    }
    return CachingAspect.doAround(
        DefaultProceedingJoinPoint(
            target    = this,
            signature = $MethodSignatures.ajc$tjp_0,
            args      = listOf(id),
            onProceedListener = { __args ->
                `$fetch`(__args[1] as String)   // [1] because this is a member function; [0] for top-level
            }
        )
    ) as String
}
```

Key points:

- The original body is extracted into a **local function** (`$fetch`).
- The function body is **replaced** with a single call to the `@Around` advice.
- `proceed()` delegates to the local function via the `onProceedListener` lambda.
- The lambda receives the **full args list** (receiver + regular params) in the same order
  as `pjp.args`.

### Top-level functions

For top-level functions there is no receiver, so the wrapper lambda uses index `0` for the
first regular parameter instead of `1`:

```kotlin
@Cached
fun fetch(id: String): String { ... }

// generated lambda:
{ __args -> `$fetch`(__args[0] as String) }
```

## `proceed()` — Invoke the Original Body

Calling `pjp.proceed()` runs the original function body with the **original arguments**:

```kotlin
@Around(target = [Logged::class])
fun doAround(pjp: ProceedingJoinPoint): Any? {
    println("before")
    val result = pjp.proceed()
    println("after")
    return result
}
```

### Calling `proceed()` Multiple Times

`proceed()` can be called any number of times. Each call executes the original body once:

```kotlin
@Around(target = [Retryable::class])
fun retry(pjp: ProceedingJoinPoint): Any? {
    repeat(3) {
        try { return pjp.proceed() } catch (_: IOException) {}
    }
    throw RetryExhaustedException()
}
```

### Not Calling `proceed()`

If you never call `pjp.proceed()`, the original body is **never executed**. The return value
of the advice method becomes the result of the intercepted function call:

```kotlin
@Around(target = [FeatureFlag::class])
fun stub(pjp: ProceedingJoinPoint): Any? = "stubbed"

@FeatureFlag
fun realWork(): String {
    println("this never runs")
    return "real"
}

realWork()  // → "stubbed", println not called
```

## `proceed(vararg args)` — Argument Substitution

Pass modified arguments to `proceed()` to override what the original body sees. The varargs
correspond to the **regular parameters** only (the receiver, if any, is kept from `pjp.args`):

```kotlin
@Aspect
object SanitizeAspect {
    @Around(target = [Sanitized::class])
    fun sanitize(pjp: ProceedingJoinPoint): Any? {
        val dirty = pjp.args.last() as String   // last regular arg
        return pjp.proceed(dirty.trim())         // replace with trimmed value
    }
}

class UserService {
    @Sanitized
    fun save(name: String): String = name
}

UserService().save("  alice  ")  // → "alice"
```

!!! note "Arg ordering in `proceed(vararg)`"
    Pass **only the regular parameters** — do not include the receiver. AspectK reconstructs
    the full args list (prepending the receiver from `pjp.args`) internally.

    ```kotlin
    // member function: pjp.args = [receiver, param0, param1]
    pjp.proceed(newParam0, newParam1)   // ✅ 2 args

    // extension function: pjp.args = [extReceiver, param0]
    pjp.proceed(newParam0)              // ✅ 1 arg (extension receiver is kept)

    // top-level: pjp.args = [param0, param1]
    pjp.proceed(newParam0, newParam1)   // ✅ 2 args
    ```

## Exception Handling

Exceptions thrown by `pjp.proceed()` propagate normally through the advice:

```kotlin
// Let exceptions pass through
@Around(target = [Safe::class])
fun passThrough(pjp: ProceedingJoinPoint): Any? = pjp.proceed()

// Catch and suppress
@Around(target = [Safe::class])
fun suppress(pjp: ProceedingJoinPoint): Any? =
    try { pjp.proceed() } catch (e: IOException) { null }

// Replace with a different exception
@Around(target = [Safe::class])
fun rethrow(pjp: ProceedingJoinPoint): Any? =
    try { pjp.proceed() } catch (e: RuntimeException) {
        throw AppException("wrapped", e)
    }
```

## `@Around` on Unit-Returning Functions

When the target function returns `Unit`, the advice return value is ignored. You can safely
return `pjp.proceed()` or `null` — no `ClassCastException` is thrown:

```kotlin
@Aspect
object LogAspect {
    @Around(target = [Traced::class])
    fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
}

@Traced
fun doWork() {   // returns Unit
    println("working")
}

doWork()  // works — no ClassCastException
```

## Execution Order with `@Before` and `@After`

When multiple advice types target the same function, they execute in this order:

```
@Before fires
   ↓
@Around advice starts
   ↓  (only if pjp.proceed() is called)
      Original body executes
      ↓ (finally)
      @After fires
   ↓  (control returns to @Around after proceed())
@Around post-proceed logic
   ↓
Return value delivered to caller
```

`@After` is **innermost** and wraps only the original body — not the `@Around` call.
This means `@After` fires if and only if `pjp.proceed()` was called and the original body ran.

See [`@After` Advice](after-advice.md) for details on this design decision.

## Current Limitation — One `@Around` Per Target Annotation

At most **one** `@Around` advice can be applied per target annotation. If multiple `@Around`
methods target the same annotation, only the first one found in the `AspectLookUp` is applied.

Support for chained (nested) `@Around` advices is planned in a future release.

```kotlin
// ⚠️ Only one of these will fire — do not define multiple @Around for the same target
@Aspect
object AspectA {
    @Around(target = [MyAnn::class])
    fun first(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
}

@Aspect
object AspectB {
    @Around(target = [MyAnn::class])
    fun second(pjp: ProceedingJoinPoint): Any? = pjp.proceed()  // ⚠️ may not fire
}
```

## `@Around` on Extension and Top-Level Functions

`pjp.args` follows the same layout as `JoinPoint.args`:

```kotlin
// Extension function
@TargetAnn
fun MyClass.doWork(x: String): String = x
// pjp.target  → null
// pjp.args    → [MyClass instance, x]
// pjp.proceed(newX) replaces x only; receiver is kept

// Top-level function
@TargetAnn
fun doWork(x: String): String = x
// pjp.target  → null
// pjp.args    → [x]
// pjp.proceed(newX) replaces x
```

See [Join Points](join-points.md) for the full reference on `target` and `args`.
