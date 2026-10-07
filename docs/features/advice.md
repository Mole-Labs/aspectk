# Advice

Advice is the code AspectK injects into an intercepted function. You declare it as a method
inside an `@Aspect` object, annotated with `@Before`, [`@After`](after-advice.md) or
[`@Around`](around-advice.md). This page covers `@Before`, and the other two share its
`target` and `inherits` parameters.

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

A `@Before` advice function must:

1. Be declared inside an `@Aspect` object
2. Take exactly one parameter, of type `JoinPoint`
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

One `@Before` can target several annotation classes:

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

It behaves like one `@Before` method per annotation, without the duplication.

## Inheritance (`inherits = true`)

With `inherits = true`, advice also applies to any function that overrides an annotated
function:

```kotlin
abstract class BaseService {
    @RequiresAuth
    abstract fun getData(): String
}

class ConcreteService : BaseService() {
    // No @RequiresAuth here, but inherits = true still intercepts it
    override fun getData(): String = "data"
}

@Aspect
object AuthAspect {
    @Before(target = [RequiresAuth::class], inherits = true)
    fun check(joinPoint: JoinPoint) { /* runs for ConcreteService.getData too */ }
}
```

See [Inheritance](inheritance.md) for details.

## Other Advice Types

| Annotation | Runs | Controls return value | Can skip body |
|---|---|---|---|
| `@Before` | Before the function body | No | No |
| [`@After`](after-advice.md) | After the function body (always, like `finally`) | No | No |
| [`@Around`](around-advice.md) | Wraps the entire call | Yes | Yes |
