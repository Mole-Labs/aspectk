# Quick Start

This guide builds a logging aspect and applies it to two functions.

## Step 1: Define a Target Annotation

AspectK picks the functions to intercept by annotation. Create a marker annotation:

```kotlin
// Marks functions whose execution should be logged
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Logged
```

!!! tip
    `AnnotationRetention.BINARY` is enough. AspectK reads annotations at compile time, so
    `RUNTIME` retention is not required.

## Step 2: Create an Aspect

An aspect is an `object` annotated with `@Aspect`. Inside it, define one or more advice
methods annotated with `@Before`:

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

Rules for `@Before` advice:

- It takes exactly one parameter, of type `JoinPoint`.
- It returns `Unit`.
- The aspect must be an `object`, not a `class`.

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

AspectK injects the advice during compilation. Calling `placeOrder("user42", 1001)` prints:

```
[placeOrder] called with: user42, 1001
Order placed by user42 for product 1001
```

## Inspecting the JoinPoint

`JoinPoint` describes the intercepted call:

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
