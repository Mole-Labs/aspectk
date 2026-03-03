# Aspects

An **aspect** is the central organizational unit in AspectK. It is an `object`
annotated with `@Aspect` that groups related advice methods.

!!! warning
    Aspects must be declared as `object`, not `class`. Using `class` is not supported and
    will result in a compilation error.

## Declaring an Aspect

```kotlin
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint

@Aspect
object SecurityAspect {
    @Before(target = [RequiresAuth::class])
    fun checkAuthentication(joinPoint: JoinPoint) {
        val user = getCurrentUser()
            ?: throw UnauthorizedException("Not authenticated")
        println("Access granted to ${joinPoint.signature.methodName} for $user")
    }
}
```

## Multiple Aspects

Multiple aspect objects can each provide advice for the same target annotation.
AspectK applies all matching advice in the order aspects are discovered by the compiler.

```kotlin
@Aspect
object LoggingAspect {
    @Before(target = [Audited::class])
    fun log(jp: JoinPoint) { /* ... */ }
}

@Aspect
object AuditAspect {
    @Before(target = [Audited::class])
    fun audit(jp: JoinPoint) { /* ... */ }
}

@Audited
fun deleteUser(userId: String) { /* both log and audit run first */ }
```

!!! warning "Advice Order"
    The order of advice application across multiple aspect objects is determined by
    the compiler's IR traversal order and is not guaranteed to be stable across
    compiler versions. Design aspects to be order-independent when possible.

## Aspect Discovery

AspectK's compiler plugin scans all IR files in the compilation unit for `@Aspect`-annotated
objects. Only aspects defined within the same compilation classpath are discovered — aspects
in external libraries are currently not supported.
