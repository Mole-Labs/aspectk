# Advice Ordering

When several advices apply to the same function, AspectK orders them with two rules, applied by
priority.

| Priority | Rule | Order |
|---|---|---|
| 1 | Kind | `@Before`, then `@Around`, then the body, then `@After` |
| 2 | Target annotation | The order the annotations are written on the function |

"First" means *runs first* for `@Before` and `@After`, and *outermost* for `@Around`: the first
`@Around` is entered first and returns last.

The order advices are declared in inside an `@Aspect` is **not** part of the rules. See
[What Is Not Guaranteed](#what-is-not-guaranteed).

## 1. Kind

Whatever annotations the advices target, and wherever they are declared, the kinds always layer
the same way:

```
@Before  →  @Around (enter)  →  body  →  @After  →  @Around (return)
```

```kotlin
@Aspect
object TransferAspect {
    @After(target = [Transfer::class])
    fun audit(joinPoint: JoinPoint) = println("audit")

    @Around(target = [Transfer::class])
    fun transaction(pjp: ProceedingJoinPoint): Any? {
        println("begin")
        return pjp.proceed().also { println("commit") }
    }

    @Before(target = [Transfer::class])
    fun checkPermission(joinPoint: JoinPoint) = println("check")
}

@Transfer
fun transfer(amount: Int) = println("transfer $amount")
```

```
check
begin
transfer 100
audit
commit
```

Two things follow from where each kind sits:

- `@Before` is outside every `@Around`. It runs exactly once per call, sees the original
  arguments, and runs even if an `@Around` never calls `proceed()`.
- `@After` is inside every `@Around`, wrapped around the body only. It runs once per
  `proceed()`: not at all if an `@Around` skips `proceed()`, twice if one calls it twice. It sees
  the arguments the body actually received, including ones replaced through `proceed(newArgs)`.
  If the body throws, `@After` runs first and the exception then reaches the `@Around` advices.

## 2. Target Annotation

When a function carries several target annotations, the advices of the annotation written first
come first. Reorder the annotations on the function to reorder the advices.

```kotlin
@Aspect
object Aspects {
    @Around(target = [Cached::class])
    fun cache(pjp: ProceedingJoinPoint): Any? { /* return the cached value or proceed() */ }

    @Around(target = [Timed::class])
    fun time(pjp: ProceedingJoinPoint): Any? { /* measure proceed() */ }
}

@Timed
@Cached
fun timedThenCached(x: Int): Int = x * 2   // time( cache( body ) ): a cache hit is timed too

@Cached
@Timed
fun cachedThenTimed(x: Int): Int = x * 2   // cache( time( body ) ): only real computations are timed
```

The same holds for `@Before` and `@After`: with `@Logged @Timed` on the function, the `Logged`
advice of each kind runs ahead of the `Timed` one.

Rule 1 still wins over this one. With `@Logged @Timed`, a `@Before` targeting `Timed` runs
ahead of an `@Around` targeting `Logged`.

!!! note "One advice listing several targets"
    An advice applies once for each of its target annotations that the function carries.

    ```kotlin
    @Aspect
    object TraceAspect {
        @Around(target = [Logged::class, Timed::class])
        fun trace(pjp: ProceedingJoinPoint): Any? {
            println("enter")
            return pjp.proceed().also { println("exit") }
        }
    }

    @Logged
    fun one() = println("body")

    @Logged
    @Timed
    fun both() = println("body")
    ```

    `one()` carries one of the two targets, so the advice applies once:

    ```
    enter
    body
    exit
    ```

    `both()` carries both, so the advice applies twice, nested inside itself:

    ```
    enter
    enter
    body
    exit
    exit
    ```

    The same goes for `@Before` and `@After`: on `both()` they would run twice in a row. If the
    advice should run once however many of its targets are present, give it a single target
    annotation and put that annotation on the function.

!!! note "Inherited annotations"
    An override that receives advice through `inherits = true` uses the order the annotations
    are written in on the overridden declaration.

## What Is Not Guaranteed

Two orders are left unspecified. Don't rely on what you observe for them: it can change between
builds and versions.

**Several advices on one target annotation.** When advices of the same kind target the same
annotation, the order between them is not guaranteed. It does not follow the order they are
declared in, inside one `@Aspect` or across several.

```kotlin
@Aspect
object Aspects {
    @Around(target = [Logged::class]) fun first(pjp: ProceedingJoinPoint): Any? { /* ... */ }
    @Around(target = [Logged::class]) fun second(pjp: ProceedingJoinPoint): Any? { /* ... */ }
}

@Logged
fun work() { /* ... */ }   // which of the two is outermost is unspecified
```

When the order matters, give each advice its own target annotation and order the annotations on
the function (rule 2).

**Annotations inherited from another module.** Rule 2 holds for inherited annotations only when
the overridden declaration is compiled in the same module. Otherwise, repeat the annotations on
the override.
