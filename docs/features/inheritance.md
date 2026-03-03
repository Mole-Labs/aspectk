# Inheritance

By default, AspectK only intercepts functions that are **directly annotated** with a target
annotation. With `inherits = true`, advice also applies to functions that **override**
an annotated function, even if the override itself is not annotated.

## Default Behavior (`inherits = false`)

```kotlin
abstract class BaseRepository {
    @Cached
    abstract fun findById(id: String): Entity?
}

class SqlRepository : BaseRepository() {
    // No @Cached annotation here
    override fun findById(id: String): Entity? { ... }
}

@Aspect
object CacheAspect {
    @Before(target = [Cached::class])  // inherits = false by default
    fun cache(joinPoint: JoinPoint) { ... }
}
```

With default settings, `cache()` runs for `BaseRepository.findById` declarations but
**not** for `SqlRepository.findById` calls (since the override is not annotated).

## Enabling Inheritance

```kotlin
@Aspect
object CacheAspect {
    @Before(target = [Cached::class], inherits = true)
    fun cache(joinPoint: JoinPoint) { ... }
}
```

Now `cache()` runs for **any** override of a `@Cached`-annotated function, regardless
of whether the override itself carries the annotation.

## Use Cases

### Interface Contracts

```kotlin
interface AuthService {
    @RequiresAdmin
    fun deleteUser(userId: String)

    @RequiresAdmin
    fun resetPassword(userId: String)
}

class AuthServiceImpl : AuthService {
    // Both overrides are protected automatically with inherits = true
    override fun deleteUser(userId: String) { ... }
    override fun resetPassword(userId: String) { ... }
}

@Aspect
object AdminAspect {
    @Before(target = [RequiresAdmin::class], inherits = true)
    fun verifyAdmin(jp: JoinPoint) {
        if (!currentUser.isAdmin) throw ForbiddenException()
    }
}
```

### Abstract Base Classes

Use `inherits = true` when you annotate template methods in abstract classes and want
all concrete implementations to be intercepted automatically.

## How It Works

When `inherits = true`, AspectK's `InheritableVisitor` tracks all IR functions that
override a function annotated with a target annotation. These overriding functions are
added to the transform targets, even without the direct annotation.
