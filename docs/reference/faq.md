# FAQ

## General

### What is the difference between AspectK and AspectJ?

AspectK picks targets by annotation and works through the Kotlin K2 compiler IR API, so it
runs on every Kotlin Multiplatform target (JVM, JS, WASM, Native). AspectJ is JVM only.
AspectJ's pointcut expressions can match more than AspectK's annotation-based targeting can.

### Which Kotlin versions can I use?

2.2.20 through 2.4.10 with AspectK 0.3.x. The plugin fails the build at configuration time if
the project's Kotlin version is outside the supported range. See the
[compatibility table](compatibility.md) for older releases and for the stricter rule on iOS.

## Setup

### Why isn't my aspect being applied?

Check the following:

1. The plugin is applied. `id("io.github.mole-labs.aspectk")` has to be in the `plugins {}`
   block of every module that declares an `@Aspect` or annotates a function with a target
   annotation.
2. The aspect is in the same Gradle build. Aspects in a pre-compiled library, such as one
   pulled from Maven Central, are not picked up. See
   [Cross-Module Injection](../features/cross-module-weaving.md#4-limitations).
3. The aspect is an `object`, not a `class`.
4. The advice signature is right. `@Before` and `@After` take one `JoinPoint` and return
   `Unit`. `@Around` takes one `ProceedingJoinPoint` (`SuspendProceedingJoinPoint` for a
   `suspend` target) and returns `Any?`.

### Does the order of advice execution matter?

Yes. Advices are ordered by kind first (`@Before`, then `@Around`, then the body, then
`@After`), then by the order the target annotations are written on the function. The order
between advices of the same kind that target the same annotation is not guaranteed, whatever
order they are declared in. See [Advice Ordering](../features/advice-ordering.md).

## Runtime Behavior

### What is `JoinPoint.target` for extension functions?

`null`. The extension receiver is the first element of `args` instead. See
[Join Points](../features/join-points.md#extension-function).

### Why does `MethodSignature.returnType` show `Any` for a generic function?

A type parameter is resolved to its upper bound at compile time. For `fun <T> box(value: T): T`
the `returnType` is `Any::class`, and `returnTypeName` is `"kotlin.Any"`. With a bound such as
`<T : Number>` you get `Number` instead.

### Does AspectK support Kotlin Reflection?

No. AspectK does not depend on `kotlin-reflect`. Types such as `MethodSignature.returnType`
are exposed as `KClass<*>`, and `KType` or `KCallable` reflection is not available.

### Are annotation default values available in `AnnotationInfo.args`?

No. `args` holds only the arguments written at the annotation use site.

## Contributing

### How do I add a new advice type?

The [Contributing guide](../contributing.md) describes the IR transformation pipeline and
where a new advice type plugs in.
