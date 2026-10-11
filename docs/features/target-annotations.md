# Target Annotations

An advice names one or more annotation classes, and AspectK weaves it into every function of
your module that carries one of them. This page covers which annotations can be named and what
counts as a function.

## Annotations You Did Not Declare

A target annotation does not have to be your own. Any annotation class the aspect can reference
works, including one from the standard library, from a third-party library, or written in Java:

```kotlin
@Aspect
object ThreadCheckAspect {
    @Before(WorkerThread::class)          // androidx.annotation.WorkerThread
    fun check(joinPoint: JoinPoint) {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "${joinPoint.signature.methodName} must not run on the main thread"
        }
    }
}

class UserRepository {
    @WorkerThread
    fun load(id: Int): User = dao.find(id)   // the check now runs first
}
```

Code that already carries such annotations gets the advice without being edited.

!!! note
    The advice is woven into functions compiled in your module. A function inside the library
    that declares the annotation is not changed.

## Lambdas and Anonymous Functions

Kotlin applies an annotation written in front of a lambda to the function the lambda compiles
to, so a lambda can be a target:

```kotlin
button.setOnClickListener(@Logged { submit() })   // as an argument

executor.execute @Logged { refresh() }            // as a trailing lambda

val handler: (String) -> Unit = @Logged { name -> greet(name) }

register(@Logged fun() { submit() })              // anonymous function
```

- The advice runs each time the lambda is invoked, not when it is created.
- `joinPoint.args` holds the arguments the lambda was invoked with.
- `@Before`, `@After` and `@Around` all apply. A lambda under `@Around` can still capture
  variables from the enclosing function.

### Lambdas Passed to Inline Functions

`forEach`, `let`, `run`, `map`, `getOrElse`, `onFailure` and most other standard-library
functions that take a lambda are `inline`. Their lambda never becomes a function, so Kotlin
rejects an annotation it would have to store:

```
The lambda expression here is an inlined argument, so this annotation cannot be stored anywhere.
```

Declare the target annotation with `SOURCE` retention and Kotlin accepts it. AspectK weaves the
advice into the lambda body before the lambda is inlined:

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class LogFailure

@Aspect
object FailureAspect {
    @Before(LogFailure::class)
    fun log(joinPoint: JoinPoint) {
        println("failed: ${joinPoint.args.first()}")
    }
}

fun load(): Config = runCatching { read() }
    .getOrElse @LogFailure { Config.DEFAULT }   // logs the exception, then falls back
```

- A non-local `return` inside the lambda still returns from the enclosing function.
- A `SOURCE` annotation also works on named functions and on lambdas that are not inlined, and
  an aspect in another module can target it, so one annotation can serve every case.
