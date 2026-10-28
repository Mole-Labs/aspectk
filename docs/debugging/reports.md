# Debug Reports

!!! info "Since 0.4.3"
    Debug reports are available from AspectK 0.4.3. Earlier versions do not have them.

Advice is woven into the compiled code, not into your sources, so the source never shows what
runs. Debug reports show which advice applies to each target annotation and which functions it
was woven into. They also include a Kotlin-like dump of every woven file.

## 1. Enabling reports

```kotlin
// build.gradle.kts of the module to inspect
aspectk {
    debug = true
}
```

Every compilation of the module then writes its reports to its own directory under
`build/aspectk/reports/`:

```
build/aspectk/reports/<target>/<compilation>/
  aspects.txt                  advice per target annotation, and hints that no longer resolve
  woven/app/Main.kt            Kotlin-like dump of a woven file
  woven/app/Main.aspects.txt   functions in that file and the advice woven into each
```

`<target>` and `<compilation>` are the Kotlin target and compilation, for example `jvm/main`.
On Android modules that use the built-in Kotlin of AGP the target segment is empty, so the
path looks like `reports/debugUnitTest/`.

Writing the reports adds work to every compilation of the module, so enable them while
investigating and leave them off otherwise.

## 2. Example project

The reports below come from this project. `:app` declares its own aspect and depends on
`:lib`. `:gone` had an aspect for the same annotation, but it has since been deleted while
`:app` still has its record.

```kotlin
// :shared
package shared

annotation class Logged
```

```kotlin
// :lib (depends on :shared)
package lib

@Aspect
object LibAspect {
    @Before(target = [shared.Logged::class])
    fun advice(jp: JoinPoint) {}
}
```

```kotlin
// :app (depends on :lib), Main.kt
package app

@Aspect
object AppAspect {
    @Before(target = [shared.Logged::class])
    fun advice(jp: JoinPoint) {}
}

class Woven {
    @shared.Logged
    fun run() {}
}

@shared.Logged
fun topLevel() {}
```

```kotlin
// :app, Plain.kt: uses no target annotation
package app

class Plain {
    fun run() {}
}
```

## 3. `aspects.txt`

`:app` gets this `aspects.txt`:

```
## Applied
shared.Logged
  BEFORE  app.AppAspect.advice  [local]
  BEFORE  lib.LibAspect.advice  [external]

## Dropped
gone.GoneAspect.advice -> shared.Logged  [external]
```

**Applied** lists every target annotation the module knows about by its fully qualified name,
here `shared.Logged`. Under each one is a line for every advice that applies to it:

```
  BEFORE  app.AppAspect.advice  [local]
  ──┬───  ──────┬───── ──┬───   ──┬──
  kind    aspect object  advice   where it came from
          (qualified)    function
```

So `app.AppAspect.advice` is the `advice` function of the `app.AppAspect` object, and it is a
`@Before` advice. The tag says where the advice came from:

| Tag | Source |
|---|---|
| `local` | An `@Aspect` in this module, compiled in this build |
| `external` | An `@Aspect` in another module, received through the compile classpath (see [Cross-Module Injection](../features/cross-module-weaving.md)) |
| `carried-forward` | An `@Aspect` in this module recorded by the previous build. Its file was not recompiled in this incremental round, so the advice is read back from that record |

**Dropped** lists advice that was recorded earlier but no longer exists, for example because
the aspect was deleted. Here `GoneAspect` no longer exists, so nothing is woven for it. Each
line has the form `<aspect>.<advice> -> <target annotations>  [<tag>]`. A `carried-forward`
entry only appears in an incremental round: a full rebuild starts without the previous record.

## 4. `woven/`

Each file that received advice gets two entries, laid out by package. In the example,
`Main.kt` is woven and `Plain.kt` is not, so only `Main.kt` has entries:

```
woven/app/Main.kt
woven/app/Main.aspects.txt
```

### `<File>.aspects.txt`

This file lists the woven functions in the file by their fully qualified names. Under each
function are the advice woven into it, in weaving order, in the same format as `aspects.txt`:

```
app.Woven.run
  BEFORE  app.AppAspect.advice  [local]
  BEFORE  lib.LibAspect.advice  [external]
app.topLevel
  BEFORE  app.AppAspect.advice  [local]
  BEFORE  lib.LibAspect.advice  [external]
```

### `<File>.kt`

This file is the whole source file after weaving, printed as Kotlin-like code by the
compiler's `dumpKotlinLike()`. It shows the code AspectK inserted, which the source file does
not. This is `Woven` from `woven/app/Main.kt`, with the constructor and the inherited
`equals`/`hashCode`/`toString` stubs left out:

```kotlin
class Woven {
  @Logged
  fun run() {
    { // BLOCK
      AppAspect.advice(jp = DefaultJoinPoint(target = <this>, signature = $MethodSignatures.<get-ajc$tjp_0>(), args = listOf<Any?>(elements = [<this>])))
      LibAspect.advice(jp = DefaultJoinPoint(target = <this>, signature = $MethodSignatures.<get-ajc$tjp_0>(), args = listOf<Any?>(elements = [<this>])))
    }
  }

  object $MethodSignatures {
    val ajc$tjp_0: MethodSignature
      field = MethodSignature(methodName = "run", annotations = listOf<AnnotationInfo>(elements = [AnnotationInfo(type = Logged::class, typeName = "shared.Logged", args = listOf<Any?>(elements = []), parameterNames = listOf<String>(elements = []))]), parameter = listOf<MethodParameter>(elements = [MethodParameter(name = "<this>", type = Woven::class, typeName = "app.Woven", annotations = listOf<AnnotationInfo>(elements = []), isNullable = false)]), returnType = Unit::class, returnTypeName = "kotlin.Unit")
      get
  }
}
```

The dump shows the following:

- **Advice calls:** the empty body of `run()` now starts with a call to each `@Before` advice,
  in the order listed in `Main.aspects.txt`.
- **`JoinPoint` construction:** each call builds a `DefaultJoinPoint` from the receiver
  (`<this>`), the method signature and the argument list. A top-level function has
  `target = null`.
- **Method signatures:** AspectK generates a `$MethodSignatures` object that holds one
  `MethodSignature` per woven function. It is created once and shared by every call.
- **`@After` and `@Around`:** these wrap the original body. In the dump, the body appears inside
  a `try`/`finally` block or a proceed lambda.

The dump is generated from the compiler's internal representation, so it is not always valid
Kotlin. `<this>`, `<get-...>` and `/* fake */` are notation of the dump format.

A file with no woven function has neither entry. If an advice is listed under **Applied** but
no `<File>.aspects.txt` mentions it, it was not woven into this module. Check whether its
target annotation is used here, on a function with a body.

## 5. Incremental builds

An incremental round only compiles the files that changed, so the reports are kept per file:

- `aspects.txt` is rewritten every round and stays complete. Advice from files that were not
  recompiled is listed as `carried-forward`.
- A recompiled file has its old dump and `<File>.aspects.txt` replaced. If it is no longer
  woven, both are removed.
- Files that were not recompiled keep the reports of the round that last compiled them.

!!! note
    Deleting a source file leaves its reports in place, because the file is never part of a
    later round. Run a clean build to remove them.
