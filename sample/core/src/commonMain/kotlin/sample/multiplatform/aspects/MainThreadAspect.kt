package sample.multiplatform.aspects

import androidx.annotation.MainThread
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint

/**
 * Advice on an annotation this project does not declare: androidx's [MainThread].
 *
 * A target annotation can come from any library. Functions that already carry it get the advice
 * without being edited, as long as their module applies the AspectK plugin.
 */
@Aspect
object MainThreadAspect {
    /** Names of the `@MainThread` functions called so far. Used by tests. */
    val calls = mutableListOf<String>()

    @Before(MainThread::class)
    fun record(joinPoint: JoinPoint) {
        calls.add(joinPoint.signature.methodName)
    }

    fun clear() = calls.clear()
}
