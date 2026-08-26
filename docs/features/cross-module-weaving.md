# Cross-Module Weaving

An `@Aspect` declared in one module correctly weaves into targets in a *different*,
downstream module that depends on it. No annotation, marker interface, or extra configuration
is needed on the target's side — the same `@Before`/`@After`/`@Around` annotations work
identically whether the aspect lives in the same module or a different one.

## 1. Basic cross-module weaving

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
    // LoggingAspect.log() is woven in here, even though LoggingAspect
    // lives in a different module and :feature never imports it directly.
}
```

Both `:core` and `:feature` apply the AspectK Gradle plugin. That's the only requirement —
`:feature` doesn't need to know `LoggingAspect` exists, and `:core` doesn't need to know
`:feature` exists.

## 2. Diamond dependency graphs

Weaving works transitively through any depth of project dependencies, including diamond
shapes:

```
        :core (declares @Aspect)
         /        \
   :feature-a   :feature-b
         \        /
          :shared
```

If `:shared` depends on both `:feature-a` and `:feature-b`, and both of those depend on
`:core`, a target in `:shared` still gets the advice from `:core` — and exactly **once**,
not twice, regardless of how many separate paths lead back to the same aspect module.

## 3. Modules that don't use AspectK

Applying the AspectK plugin is opt-in per module, not build-wide. A plain module with no
`@Aspect` and no advice-target annotations of its own — a data layer, a networking module,
a pure-Kotlin utility library — doesn't need the plugin applied at all, even if it sits
between two participating modules in the dependency graph.

## 4. Limitations

Cross-module weaving only works within the same Gradle build. An aspect declared in a module
published as a pre-compiled binary (e.g. published to Maven Central) is not visible to
consumers of that binary outside the build that produced it — this is the same class of
limitation as depending on any other pre-compiled third-party library.

## 5. Setup

Nothing beyond the normal [installation](../getting-started/installation.md) — apply the
plugin to every module that either declares an `@Aspect` or uses a target annotation on one
of its own functions:

```kotlin
// build.gradle.kts, in each participating module
plugins {
    id("io.github.mole-labs.aspectk") version "LATEST_VERSION"
}
```

## 6. Incremental compilation correctness

A real Gradle build is rarely a clean build — most builds after the first one are
*incremental*, recompiling only the files that changed. AspectK is correctness-tested against
real (non-clean) incremental Gradle builds, covering the cases that a naive compiler-plugin
implementation tends to get wrong:

| Scenario | Guarantee |
|---|---|
| Only the **target** file is edited, the aspect file is untouched | The target is still woven correctly |
| Only the **aspect** file is edited, the target file is untouched | The target is re-woven to reflect the change (a full recompile of the affected compilation is triggered automatically when needed) |
| An unrelated file is edited in a module that also contains aspects | Existing advice is not lost |
| Any of the above, but the aspect and the target are in **different modules** | Cross-module weaving still holds, including through a diamond dependency |

You don't need to do anything to get these guarantees — they hold for the default Gradle
incremental-build behavior, with no opt-in flag or `clean` required. AspectK detects when an
incremental round needs special handling and forces the minimum extra recompilation required
to stay correct, rather than either silently missing a re-weave or disabling incremental
compilation for the whole module permanently.
