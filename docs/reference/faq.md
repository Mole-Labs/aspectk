# FAQ

## General

### What is the difference between AspectK and AspectJ?

AspectK is annotation-driven and works exclusively through the Kotlin K2 compiler IR API.
It supports Kotlin Multiplatform (JVM, JS, WASM, Native), while AspectJ only targets JVM.
AspectJ offers richer pointcut expressions; AspectK uses simpler annotation-based targeting.

### Does AspectK support `@After` or `@Around` advice?

Currently, only `@Before` is supported. `@After` and `@Around` advice are planned for
future releases.

### Can I use AspectK with Kotlin versions other than 2.2.x?

No. AspectK targets the K2 IR transformation API and is tested against the **2.2.x** series only.
Using a different Kotlin version may cause compilation errors due to K2 IR API changes.

## Setup

### Why isn't my aspect being applied?

Check the following:

1. **Plugin is applied**: Ensure `id("io.github.mole-labs.aspectk.compiler")` is in your `plugins {}` block.
2. **Runtime dependency**: `aspectk-runtime` must be on your compile classpath.
3. **Aspect in same compilation unit**: AspectK only discovers aspects compiled in the same unit. Aspects from external JARs are not currently supported.
4. **Advice signature**: The advice method must accept exactly one `JoinPoint` parameter and return `Unit`.

### Does the order of advice execution matter?

Advice is applied in the order aspects are discovered during IR traversal. This order
is not guaranteed across compiler versions. Design your aspects to be order-independent.

## Runtime Behavior

### What is `JoinPoint.target` for extension functions?

For extension functions, `target` is the receiver of the extension, i.e., the `this` object
inside the extension function body.

### Why does `MethodSignature.returnType` show `Any` for a generic function?

Generic type parameters are erased at compile time. For a function `fun <T> box(value: T): T`,
the `returnType` is `Any::class` (the upper bound of the unconstrained type parameter `T`).
Use `returnTypeName` if you need the source-level type name.

### Are annotation default values available in `AnnotationInfo.args`?

No. Only arguments **explicitly provided** at the annotation use site appear in `args`.
Default values are not captured.

## Contributing

### How do I add a new advice type?

See the [Contributing guide](../contributing.md) for the IR transformation pipeline
and how to add new advice injection strategies.
