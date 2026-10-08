# `@Around` Advice

`@Around` advice wraps the whole target function. Unlike `@Before` and `@After`, it decides
whether the original body runs and what the caller gets back. The advice runs the original
body by calling `pjp.proceed()` on the `ProceedingJoinPoint` it receives.

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
svc.fetch("42")   // cached, prints nothing
```

## Function Signature Rules

An `@Around` advice method must:

1. Be declared inside an `@Aspect` object
2. Take exactly one parameter, of type `ProceedingJoinPoint`
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
    return CachingAspect.doAround(
        DefaultProceedingJoinPoint(
            target    = this,
            signature = $MethodSignatures.ajc$tjp_0,
            args      = listOf(id),
            onProceedListener = { __args ->
                val id = __args[1] as String   // [1] because this is a member function; [0] for top-level
                "result-$id"                   // the original body, moved here
            }
        )
    ) as String
}
```

Key points:

- The original body is moved into the `onProceedListener` lambda, and the function body becomes
  a single call to the `@Around` advice.
- Each regular parameter is re-declared at the top of the lambda from `__args`, so
  `proceed(newArgs)` changes what the body sees.
- A `return` from the function body returns from the lambda instead, and its value becomes the
  result of `proceed()`. Returns from nested lambdas and local functions keep their targets.
- The lambda receives the full args list (receiver, then regular parameters) in the same
  order as `pjp.args`.
- The lambda is regenerated wherever an `inline` target is inlined, so the body can still use
  the target's reified type parameters.

### Top-level functions

For top-level functions there is no receiver, so the wrapper lambda uses index `0` for the
first regular parameter instead of `1`:

```kotlin
@Cached
fun fetch(id: String): String { ... }

// generated lambda:
{ __args ->
    val id = __args[0] as String
    ...                              // the original body
}
```

### Up to 0.3.1: local function (deprecated)

AspectK 0.3.1 and earlier copied the body into a local function and called it from the listener:

```kotlin
fun fetch(id: String): String {
    fun `$fetch`(id: String): String {
        return "result-$id"
    }
    return CachingAspect.doAround(
        DefaultProceedingJoinPoint(
            ...
            onProceedListener = { __args -> `$fetch`(__args[1] as String) }
        )
    ) as String
}
```

This transformation is deprecated and was replaced by moving the body into the listener, for these
reasons:

- **Inline functions.** Kotlin doesn't allow local functions inside inline functions; the plugin
  bypassed that check, and the backend compiled the local function as a plain method. That method
  can't see the reified type arguments, which only exist where the function is inlined, so
  `@Around` on a reified inline function failed to compile. The listener lambda, by contrast, is
  regenerated at every call site along with the inlined body. A public inline function also
  inlined a call to a synthetic accessor (`access$<name>$_<name>`) into its callers.
- **Implementation complexity.** The copy needed its own symbol remapping on top of the parameter
  substitution and `return` rewriting that moving the body still needs. Two bugs fixed in 0.2.2
  came from the copy step (see the [changelog](../reference/changelog.md#022)).
- **Combining advice.** Each `@After` and `@Around` cleared the body and rebuilt it around the
  same local function, so only the last one processed ran.

## `proceed()`: Invoke the Original Body

Calling `pjp.proceed()` runs the original function body with the original arguments:

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

You can call `proceed()` more than once. Each call runs the original body again:

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

If the advice never calls `pjp.proceed()`, the original body does not run, and the advice's
return value becomes the result of the intercepted call:

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

## `proceed(vararg args)`: Argument Substitution

Pass arguments to `proceed()` to replace what the original body sees. They stand for the
regular parameters only. The receiver, if there is one, is kept from `pjp.args`:

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
    Pass only the regular parameters and leave the receiver out. AspectK prepends the
    receiver from `pjp.args` itself.

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

When the target function returns `Unit`, the advice's return value is ignored. Returning
`pjp.proceed()` or `null` both work, and neither throws a `ClassCastException`:

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

doWork()  // no ClassCastException
```

## Execution Order with `@Before` and `@After`

`@Before` runs once, outside every `@Around`. `@After` runs inside every `@Around`, once per
`proceed()`. Several `@Around` advices on one function nest in the order their target
annotations are written on it, the first one outermost. See
[Advice Ordering](advice-ordering.md) for the full rules.

## `@Around` on `suspend` functions

When the intercepted function is `suspend`, `proceed()` has to resume the original
suspending body, so it must itself `suspend`. For these targets the advice takes a
`SuspendProceedingJoinPoint` instead of a `ProceedingJoinPoint`
and must be declared `suspend`:

```kotlin
@Aspect
object TimingAspect {
    @Around(target = [Timed::class])
    suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? {
        val start = timeSource.markNow()
        val result = pjp.proceed()            // suspends
        println("${pjp.signature.methodName} took ${start.elapsedNow()}")
        return result
    }
}

class Repo {
    @Timed
    suspend fun load(id: String): Row {
        delay(10)
        return db.fetch(id)
    }
}
```

`SuspendProceedingJoinPoint` has the same members as `ProceedingJoinPoint` (`target`,
`signature`, `args`, `proceed()`, `proceed(vararg args)`), except that `proceed` is `suspend`.
The plugin picks the join-point type from whether the target is `suspend`. A non-suspending
target still uses `ProceedingJoinPoint`.

`@Before` and `@After` need nothing special. They work on `suspend` functions with the
ordinary `JoinPoint`.

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
