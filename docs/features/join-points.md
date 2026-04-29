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

## Supported Function Types

AspectK can intercept all of the following function kinds. The `target` and `args` layout
varies by function type:

### Class member function

```kotlin
class UserService {
    @Logged
    fun getUser(id: String): User { ... }
}
// jp.target  → UserService instance
// jp.args    → [id]
```

### Top-level function

```kotlin
@Logged
fun process(data: String) { ... }
// jp.target  → null
// jp.args    → [data]
```

### Extension function

The extension receiver is prepended to `args`; `target` is `null`.

```kotlin
@Logged
fun String.process(suffix: String) { ... }
// jp.target  → null
// jp.args    → [receiverString, suffix]
```

### `suspend` function

Suspension machinery is transparent — `args` contains only the declared parameters.

```kotlin
@Logged
suspend fun fetchData(url: String): String { ... }
// jp.target  → receiver instance (or null for top-level)
// jp.args    → [url]
```

### Property getter

The receiver object is passed as `args[0]`; `target` is `null`.

```kotlin
class Config {
    val name: String
        @Logged get() = "aspectk"
}
// jp.target  → null
// jp.args    → [Config instance]
```

### Property setter

The receiver is `args[0]` and the incoming value is `args[1]`; `target` is `null`.

```kotlin
class Config {
    var name: String = ""
        @Logged set(value) { field = value }
}
// jp.target  → null
// jp.args    → [Config instance, newValue]
```

### `expect`/`actual` function

Advice is woven into the `actual` declaration on each platform; behaviour mirrors a
top-level function.

```kotlin
// commonMain
@Logged
expect fun platformGreet(name: String)
// jp.target  → null
// jp.args    → [name]
```

## Extension Functions

AspectK ships a set of inline extension functions on `JoinPoint`, `MethodSignature`, and
`AnnotationInfo` to reduce boilerplate when reading intercept context at runtime.

### `JoinPoint` extensions

#### `getArg<T>(name: String): T`

Returns the argument value for the parameter named `name`, cast to `T`.
Throws `NoSuchElementException` if the parameter does not exist, or `ClassCastException`
if the value cannot be cast.

```kotlin
@Around(target = [Transactional::class])
fun doAround(pjp: ProceedingJoinPoint): Any? {
    val userId = pjp.getArg<String>("userId")
    return pjp.proceed()
}
```

#### `getArgOrNull<T>(name: String): T?`

Returns the argument value for the parameter named `name`, cast to `T`, or `null` if
the parameter does not exist or the value cannot be cast.

```kotlin
@Before(target = [Logged::class])
fun doBefore(jp: JoinPoint) {
    val label = jp.getArgOrNull<String>("label") ?: "unknown"
}
```

#### `getTarget<T>(): T`

Returns `JoinPoint.target` cast to `T`. Useful when advice code needs to interact with
the receiver beyond the generic `Any?` type.
Throws `ClassCastException` if the cast fails, or `NullPointerException` if `target`
is `null` (top-level or companion-object function).

```kotlin
@Before(target = [Audited::class])
fun doBefore(jp: JoinPoint) {
    val service = jp.getTarget<UserService>()
    service.recordAccess()
}
```

#### `getTargetOrNull<T>(): T?`

Returns `JoinPoint.target` cast to `T`, or `null` if the target is `null` or cannot be
cast.

```kotlin
@Before(target = [Audited::class])
fun doBefore(jp: JoinPoint) {
    jp.getTargetOrNull<UserService>()?.recordAccess()
}
```

#### `findAnnotation<T : Annotation>(): AnnotationInfo?`

Returns the `AnnotationInfo` for annotation `T` on the intercepted function, or `null`
if the annotation is not present. Delegates to `MethodSignature.findAnnotation<T>()`.

```kotlin
@Before(target = [RateLimit::class])
fun doBefore(jp: JoinPoint) {
    val rateLimit = jp.findAnnotation<RateLimit>() ?: return
    val limit = rateLimit.getArg<Int>("maxCalls")
}
```

### `MethodSignature` extensions

#### `findAnnotation<T : Annotation>(): AnnotationInfo?`

Returns the `AnnotationInfo` for annotation `T` in `MethodSignature.annotations`, or
`null` if the annotation is not present.

```kotlin
@Before(target = [Secured::class])
fun doBefore(jp: JoinPoint) {
    val secured = jp.signature.findAnnotation<Secured>() ?: return
    val role = secured.getArg<String>("role")
}
```

### `AnnotationInfo` extensions

#### `getArg<T>(paramName: String): T`

Returns the annotation argument value for the parameter named `paramName`, cast to `T`.
Throws `NoSuchElementException` if the parameter does not exist, or `ClassCastException`
if the value cannot be cast.

```kotlin
@Before(target = [RateLimit::class])
fun doBefore(jp: JoinPoint) {
    val info = jp.findAnnotation<RateLimit>()!!
    val maxCalls = info.getArg<Int>("maxCalls")
}
```

#### `getArgOrNull<T>(paramName: String): T?`

Returns the annotation argument value for the parameter named `paramName`, cast to `T`,
or `null` if the parameter does not exist or the value cannot be cast.

```kotlin
@Before(target = [RateLimit::class])
fun doBefore(jp: JoinPoint) {
    val info = jp.findAnnotation<RateLimit>()!!
    val maxCalls = info.getArgOrNull<Int>("maxCalls") ?: 100
}
```
