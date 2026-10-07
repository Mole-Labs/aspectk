# Inheritance

By default, AspectK only intercepts functions that carry a target annotation themselves. With
`inherits = true`, advice also applies to functions that override an annotated function, even
when the override is not annotated.

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

With the default, `cache()` does not run for `SqlRepository.findById`, because the override
is not annotated.

## Enabling Inheritance

```kotlin
@Aspect
object CacheAspect {
    @Before(target = [Cached::class], inherits = true)
    fun cache(joinPoint: JoinPoint) { ... }
}
```

Now `cache()` runs for every override of a `@Cached` function, annotated or not. This also
works when the annotated function is declared in another module.

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
    // inherits = true covers both overrides
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

Annotate the template methods of an abstract class and set `inherits = true` to intercept
every concrete implementation.

## How It Works

`InheritableVisitor` walks each function's overridden declarations. If any of them carries a
target annotation whose advice has `inherits = true`, the overriding function is added to the
set of functions that receive advice.
