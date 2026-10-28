# IDE Support

AspectK reports mistakes in how aspects and advice are written as compiler diagnostics. Some
of them come from the compiler frontend, which IntelliJ IDEA and Android Studio run while you
edit, so the IDE can highlight them without a build.

## 1. Enabling highlighting in the IDE

The IDE runs only the compiler plugins it bundles (Compose, serialization and a few others)
unless you allow other ones:

1. Make sure K2 mode is on: **Settings → Languages & Frameworks → Kotlin → Enable K2 mode**.
   It is the default in recent versions.
2. Open **Help → Find Action**, run **Registry...**, and uncheck
   `kotlin.k2.only.bundled.compiler.plugins.enabled`. Typing `bundled` in the registry window
   finds it.
3. Restart the IDE and sync the Gradle project.

The IDE loads AspectK from the compiler plugin classpath that Gradle sync imports, so the
module has to apply the AspectK Gradle plugin as usual.

## 2. What the IDE shows

Only frontend diagnostics are highlighted while editing. They depend only on the
declaration itself:

| Diagnostic | Severity | Reported when |
|---|---|---|
| `ASPECT_NOT_OBJECT` | error | an `@Aspect` is a `class` instead of an `object` |
| `ADVICE_INVALID_PARAMETER` | error | advice does not take exactly one parameter of type `JoinPoint` or `Any` (`@Around` also accepts `ProceedingJoinPoint`, or `SuspendProceedingJoinPoint` when the advice is `suspend`), or has a receiver |
| `AROUND_INVALID_RETURN_TYPE` | error | `@Around` advice does not return `Any?` or `Any` |
| `ADVICE_OUTSIDE_ASPECT` | warning | `@Before`, `@After` or `@Around` is declared outside an `@Aspect` object, so it is never applied |

```kotlin
@Aspect
class LoggingAspect   // ASPECT_NOT_OBJECT: declare it with object
```

## 3. Diagnostics reported only by the build

These checks need to know which advice applies to a function. That includes advice from other
modules, which is only known while weaving. So they run during the Gradle build and do not
appear in the editor:

| Diagnostic | Severity | Reported when |
|---|---|---|
| `AROUND_SUSPEND_MISMATCH` | error | `@Around` advice and its target differ in `suspend`. The advice must be `suspend` and take a `SuspendProceedingJoinPoint` exactly when the target is `suspend` |
| `AROUND_INLINE_LAMBDA` | error | `@Around` wraps an `inline` function with inline or `crossinline` lambda parameters. Mark them `noinline` |
| `TAILREC_ADVICE` | warning | `@After` or `@Around` wraps a `tailrec` function. The recursive call is no longer a tail call, so it is not optimized and deep recursion can overflow the stack |

A function that fails one of the errors is not woven.

Every AspectK message starts with `[AspectK]`, so it is easy to tell apart from the compiler's
own diagnostics in build output.

!!! note
    Weaving itself never runs in the IDE, so the editor cannot show what was woven where. Use
    [debug reports](reports.md) for that.
