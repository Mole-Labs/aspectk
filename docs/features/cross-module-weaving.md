# Cross-Module Injection

An `@Aspect` declared in one module is injected into targets in a downstream module that depends
on it. The target's side needs no extra annotation, marker interface or configuration.
`@Before`, `@After` and `@Around` behave the same whether the aspect lives in the same module
or in another one.

## 1. Basic cross-module injection

```kotlin
// :core
@Aspect
object LoggingAspect {
    @Before(target = [Logged::class])
    fun log(joinPoint: JoinPoint) {
        println("→ ${joinPoint.signature.methodName}")
    }
}
```

```kotlin
// :feature (depends on :core)
@Logged
fun placeOrder(orderId: String) {
    // LoggingAspect.log() is injected here, even though LoggingAspect
    // lives in a different module and :feature never imports it directly.
}
```

Both `:core` and `:feature` apply the AspectK Gradle plugin, and that is the only requirement.
`:feature` doesn't need to know `LoggingAspect` exists, and `:core` doesn't need to know
`:feature` exists.

## 2. Diamond dependency graphs

Injection follows project dependencies transitively, to any depth, including diamond shapes:

```
        :core (declares @Aspect)
         /        \
   :feature-a   :feature-b
         \        /
          :shared
```

If `:shared` depends on both `:feature-a` and `:feature-b`, and both of those depend on
`:core`, a target in `:shared` still gets the advice from `:core`. It gets it exactly once,
however many paths lead back to the aspect module.

## 3. Modules that don't use AspectK

The plugin is applied per module, not build-wide. A module with no `@Aspect` and no target
annotations of its own, such as a data layer or a utility library, doesn't need the plugin
even if it sits between two participating modules in the dependency graph.

## 4. Limitations

Cross-module injection only works within one Gradle build. An aspect in a module published as a
pre-compiled binary, for example to Maven Central, is not visible to consumers of that binary
outside the build that produced it.

## 5. Setup

Nothing beyond the normal [installation](../getting-started/installation.md). Apply the
plugin to every module that declares an `@Aspect` or uses a target annotation on one of its
own functions:

```kotlin
// build.gradle.kts, in each participating module
plugins {
    id("io.github.mole-labs.aspectk") version "LATEST_VERSION"
}
```

## 6. Incremental compilation correctness

Most Gradle builds after the first one are incremental and recompile only the files that
changed. AspectK is tested against incremental Gradle builds, including the cases a compiler
plugin can easily get wrong:

| Scenario | Guarantee |
|---|---|
| Only the **target** file is edited, the aspect file is untouched | Advice is still injected into the target |
| Only the **aspect** file is edited, the target file is untouched | Advice is re-injected into the target to reflect the change (a full recompile of the affected compilation is triggered automatically when needed) |
| An unrelated file is edited in a module that also contains aspects | Existing advice is not lost |
| Any of the above, but the aspect and the target are in **different modules** | Cross-module injection still holds, including through a diamond dependency |

These hold for Gradle's default incremental behavior, with no flag to set and no `clean` to
run. When an incremental round would miss a re-injection, AspectK forces a full recompile of that
one compilation. It does not turn incremental compilation off for the module.
