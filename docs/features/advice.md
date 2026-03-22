# Advice

**Advice** is the code that runs at an intercepted function's call site. In AspectK, advice
is declared using the `@Before` annotation on a method inside an `@Aspect` class.

## `@Before` Annotation

```kotlin
@Before(
    target = [AnnotationClass::class, AnotherAnnotation::class],
    inherits = false,
)
fun adviceMethod(joinPoint: JoinPoint) { ... }
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `target` | `KClass<out Annotation>` (vararg) | — | One or more annotation classes that identify target functions |
| `inherits` | `Boolean` | `false` | When `true`, also intercepts overriding functions |

## Function Signature Rules

An advice function **must**:

1. Be declared inside an `@Aspect`-annotated class or object
2. Accept exactly **one parameter** of type `JoinPoint`
3. Return `Unit`

```kotlin
@Before(target = [Logged::class])
fun log(joinPoint: JoinPoint) {   // ✅ correct
    println(joinPoint.signature.methodName)
}

// ❌ Wrong: no parameter
@Before(target = [Logged::class])
fun bad1() { }

// ❌ Wrong: wrong parameter type
@Before(target = [Logged::class])
fun bad2(name: String) { }

// ❌ Wrong: extra parameters
@Before(target = [Logged::class])
fun bad3(joinPoint: JoinPoint, extra: Int) { }
```

## Multiple Targets

A single `@Before` can target multiple annotation classes simultaneously:

```kotlin
@Aspect
object ObservabilityAspect {
    @Before(target = [Logged::class, Traced::class, Metered::class])
    fun observe(joinPoint: JoinPoint) {
        val method = joinPoint.signature.methodName
        logger.info("$method invoked")
        tracer.start(method)
        metrics.increment(method)
    }
}
```

This is equivalent to writing separate `@Before` methods for each annotation, but
avoids duplication.

## Inheritance (`inherits = true`)

When `inherits = true`, advice is applied not only to directly annotated functions
but also to any function that **overrides** an annotated function:

```kotlin
abstract class BaseService {
    @RequiresAuth
    abstract fun getData(): String
}

class ConcreteService : BaseService() {
    // No @RequiresAuth here — but will still be intercepted if inherits = true
    override fun getData(): String = "data"
}

@Aspect
object AuthAspect {
    @Before(target = [RequiresAuth::class], inherits = true)
    fun check(joinPoint: JoinPoint) { /* runs for ConcreteService.getData too */ }
}
```

See [Inheritance](../features/inheritance.md) for a full guide.

## Other Advice Types

| Annotation | Runs | Controls return value | Can skip body |
|---|---|---|---|
| `@Before` | Before the function body | No | No |
| [`@After`](after-advice.md) | After the function body (always, like `finally`) | No | No |
| [`@Around`](around-advice.md) | Wraps the entire call | Yes | Yes |
