# Join Points

A `JoinPoint` represents the context of an intercepted function call. AspectK generates a
`JoinPoint` instance at each call site and passes it to the matching advice functions.

## JoinPoint Interface

```kotlin
interface JoinPoint {
    val target: Any?             // receiver object (null for top-level functions)
    val signature: MethodSignature  // compile-time method metadata
    val args: List<Any?>         // runtime arguments in declaration order
}
```

## `target` — The Receiver

`target` is the object on which the intercepted method is called:

```kotlin
class UserService {
    @Logged
    fun getUser(id: String): User { ... }
}

@Aspect
object LoggingAspect {
    @Before(target = [Logged::class])
    fun log(jp: JoinPoint) {
        val service = jp.target as? UserService  // the UserService instance
        println("Called on: $service")
    }
}
```

For **top-level functions**, `target` is `null`:

```kotlin
@Logged
fun topLevelFunction() { ... }  // jp.target == null
```

## `signature` — Method Metadata

`MethodSignature` provides compile-time metadata about the intercepted function:

```kotlin
data class MethodSignature(
    val methodName: String,             // simple function name
    val annotations: List<AnnotationInfo>, // annotations on the function
    val parameter: List<MethodParameter>,  // parameter descriptors
    val returnType: KClass<*>,          // erased return type
    val returnTypeName: String,         // fully-qualified return type name
)
```

### Example

```kotlin
@Before(target = [Logged::class])
fun inspect(jp: JoinPoint) {
    val sig = jp.signature
    println("name       : ${sig.methodName}")
    println("returnType : ${sig.returnTypeName}")
    println("parameters : ${sig.parameter.map { "${it.name}: ${it.typeName}" }}")
    println("annotations: ${sig.annotations.map { it.typeName }}")
}
```

### Generic Type Erasure

When the return type is a generic type parameter (e.g., `T`), `returnType` and
`returnTypeName` are resolved to the **upper bound** at compile time:

```kotlin
fun <T> identity(value: T): T = value
// sig.returnType     == Any::class
// sig.returnTypeName == "kotlin.Any"
```

## `args` — Runtime Arguments

`args` is a `List<Any?>` of the arguments passed to the intercepted function,
in declaration order:

```kotlin
@Logged
fun transfer(fromId: String, toId: String, amount: Double) { ... }

@Before(target = [Logged::class])
fun log(jp: JoinPoint) {
    val fromId = jp.args[0] as String   // "acc-001"
    val toId   = jp.args[1] as String   // "acc-002"
    val amount = jp.args[2] as Double   // 150.0
}
```

### Nullable Arguments

When a parameter is declared as nullable, the corresponding `args` element may be `null`:

```kotlin
@Logged
fun process(data: String?) { ... }  // jp.args[0] may be null
```

Check `MethodParameter.isNullable` to determine if `null` is expected:

```kotlin
jp.signature.parameter.zip(jp.args).forEach { (param, value) ->
    if (param.isNullable || value != null) {
        println("${param.name} = $value")
    }
}
```

## `AnnotationInfo` — Annotation Details

Annotations on functions and parameters are exposed as `AnnotationInfo`:

```kotlin
data class AnnotationInfo(
    val type: KClass<out Annotation>,  // annotation class
    val typeName: String,              // FQN string
    val args: List<Any?>,              // explicitly provided argument values
    val parameterNames: List<String>,  // corresponding parameter names
)
```

```kotlin
jp.signature.annotations.forEach { info ->
    println("@${info.typeName}")
    info.parameterNames.zip(info.args).forEach { (name, value) ->
        println("  $name = $value")
    }
}
```

!!! note
    Only arguments **explicitly provided** in source appear in `args`. Arguments using
    default values are omitted. Use `parameterNames` to identify which arguments are present.
