# Quick Start

This guide walks you through creating your first aspect in under 5 minutes.

## Step 1: Define a Target Annotation

Aspects intercept functions by matching annotations. Create a marker annotation:

```kotlin
// Marks functions whose execution should be logged
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Logged
```

!!! tip
    Use `AnnotationRetention.BINARY` — AspectK reads annotations at compile time only,
    so RUNTIME retention is not required.

## Step 2: Create an Aspect

An aspect is a class or object annotated with `@Aspect`. Inside it, define one or more
advice methods annotated with `@Before`:

```kotlin
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint

@Aspect
object LoggingAspect {
    @Before(target = [Logged::class])
    fun log(joinPoint: JoinPoint) {
        val name = joinPoint.signature.methodName
        val args = joinPoint.args.joinToString(", ")
        println("[$name] called with: $args")
    }
}
```

**Rules:**
- The advice function must have exactly **one parameter** of type `JoinPoint`.
- The return type must be `Unit`.
- Use `object` to avoid per-call instantiation (recommended).

## Step 3: Annotate Your Functions

Apply your target annotation to any function you want to intercept:

```kotlin
@Logged
fun placeOrder(userId: String, productId: Long) {
    // AspectK injects LoggingAspect.log() here at compile time
    println("Order placed by $userId for product $productId")
}

@Logged
fun cancelOrder(orderId: String) {
    println("Order $orderId cancelled")
}
```

## Step 4: Build and Run

```bash
./gradlew build
```

AspectK injects the advice during compilation. The output when calling `placeOrder` would be:

```
[placeOrder] called with: user42, 1001
Order placed by user42 for product 1001
```

## Inspecting the JoinPoint

`JoinPoint` gives you full context about the intercepted call:

```kotlin
@Aspect
object DiagnosticAspect {
    @Before(target = [Logged::class])
    fun inspect(joinPoint: JoinPoint) {
        val sig = joinPoint.signature
        println("Method  : ${sig.methodName}")
        println("Returns : ${sig.returnTypeName}")
        println("Receiver: ${joinPoint.target}")
        sig.parameter.forEachIndexed { i, param ->
            println("  arg[$i] ${param.name}: ${param.typeName} = ${joinPoint.args[i]}")
        }
    }
}
```

[Core Concepts →](../core-concepts/aspects.md){ .md-button .md-button--primary }
