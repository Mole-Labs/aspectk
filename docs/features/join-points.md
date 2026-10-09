# Join Points

A `JoinPoint` describes one call of an intercepted function. AspectK creates one each time
the function runs and passes it to the matching advice.

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

`MethodSignature` holds metadata about the intercepted function, fixed at compile time:

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

When the return type is a type parameter such as `T`, `returnType` and `returnTypeName`
resolve to its upper bound:

```kotlin
fun <T> identity(value: T): T = value
// sig.returnType     == Any::class
// sig.returnTypeName == "kotlin.Any"

fun <T : Number> double(value: T): T = value
// sig.returnType     == Number::class
// sig.returnTypeName == "kotlin.Number"
```

## `args` — Runtime Arguments

`args` is a `List<Any?>` of the arguments passed to the intercepted function, in declaration
order. A dispatch or extension receiver, when there is one, comes first (see
[Supported Function Types](#supported-function-types)). For a top-level function:

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

For a nullable parameter, the matching `args` element may be `null`:

```kotlin
@Logged
fun process(data: String?) { ... }  // jp.args[0] may be null
```

`MethodParameter.isNullable` tells you whether `null` is expected:

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
    `args` holds only the arguments written in source. Arguments left at their default
    value are omitted, and `parameterNames` tells you which ones are present.

## Supported Function Types

AspectK can intercept the function kinds below. The layout of `target` and `args` depends on
the kind:

### Class member function

```kotlin
class UserService {
    @Logged
    fun getUser(id: String): User { ... }
}
// jp.target  → UserService instance
// jp.args    → [UserService instance, id]
```

The dispatch receiver is also the first element of `args`.

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

`args` contains only the declared parameters. The continuation is not included.

```kotlin
@Logged
suspend fun fetchData(url: String): String { ... }
// jp.target  → receiver instance (or null for top-level)
// jp.args    → [url]
```

### `inline` function

An inline lambda parameter is not a value, so its slot in `args` is `null`.

```kotlin
@Logged
inline fun <reified T> measure(label: String, block: () -> T): T = block()
// jp.target  → null
// jp.args    → [label, null]
```

### Property getter

Same layout as a member function with no parameters.

```kotlin
class Config {
    val name: String
        @Logged get() = "aspectk"
}
// jp.target  → Config instance
// jp.args    → [Config instance]
```

### Property setter

The receiver is `args[0]` and the incoming value is `args[1]`.

```kotlin
class Config {
    var name: String = ""
        @Logged set(value) { field = value }
}
// jp.target  → Config instance
// jp.args    → [Config instance, newValue]
```

### `expect`/`actual` function

Advice is injected into the `actual` declaration on each platform. The layout is the same as
for a top-level function.

```kotlin
// commonMain
@Logged
expect fun platformGreet(name: String)
// jp.target  → null
// jp.args    → [name]
```

## Extension Functions

`aspectk-runtime` includes extension functions on `JoinPoint`, `ProceedingJoinPoint`,
`MethodSignature` and `AnnotationInfo` for the common lookups and casts.

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

Returns `JoinPoint.target` cast to `T`, for advice that needs to call the receiver's own
members. Throws `ClassCastException` if the cast fails, or `NullPointerException` if `target`
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

#### `getAnnotationArgs<T : Annotation>(): Map<String, Any?>`

Returns the arguments of annotation `T` on the intercepted function, by parameter name.
Throws `NoSuchElementException` if the function is not annotated with `T`.

```kotlin
@RateLimit(maxCalls = 5)
fun search(query: String) { /* ... */ }

@Before(RateLimit::class)
fun doBefore(jp: JoinPoint) {
    val args = jp.getAnnotationArgs<RateLimit>()   // {maxCalls=5}
    val maxCalls = args["maxCalls"] as Int
}
```

Only the arguments written at the use site are present. A parameter left to its default value
has no entry, so read it with a fallback: `args["scope"] as? String ?: "global"`.

#### `getAnnotationArgsOrNull<T : Annotation>(): Map<String, Any?>?`

Same as `getAnnotationArgs`, but returns `null` if the function is not annotated with `T`.

### `ProceedingJoinPoint` extensions

Every function here also exists on `SuspendProceedingJoinPoint`, as a `suspend` function.

#### `proceedAs<T>(): T`

Proceeds with the original arguments and returns the result cast to `T`. Throws
`ClassCastException` if the result is not a `T`.

```kotlin
@Around(Scaled::class)
fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedAs<Int>() * 2
```

#### `proceedAs<T>(vararg args: Any?): T`

Proceeds with substituted arguments, as `proceed(vararg args)` does, and returns the result
cast to `T`.

#### `proceedWith(vararg replacements: Pair<String, Any?>): Any?`

Proceeds with the named arguments replaced and every other one kept. Unlike
`proceed(vararg args)`, the arguments that don't change don't have to be passed again. Throws
`NoSuchElementException` if a name is not a parameter of the intercepted function.

```kotlin
@Normalized
fun find(tenant: String, name: String, limit: Int): List<User> { /* ... */ }

@Around(Normalized::class)
fun doAround(pjp: ProceedingJoinPoint): Any? =
    pjp.proceedWith("name" to pjp.getArg<String>("name").trim())
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
