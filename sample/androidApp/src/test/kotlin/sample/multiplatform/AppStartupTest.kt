package sample.multiplatform

import sample.multiplatform.aspects.LoggingAspect
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The application module has product flavors, so its variants (`playDebug`) have no namesake in
 * the library modules (`main`). Advice declared in :core still has to be woven into it.
 */
class AppStartupTest {
    @BeforeTest
    fun setUp() = LoggingAspect.clearLogs()

    @AfterTest
    fun tearDown() = LoggingAspect.clearLogs()

    @Test
    fun `advice from a library module is woven into the flavored application module`() {
        assertEquals("warm:cold-start", AppStartup().warmUp("cold-start"))

        assertEquals(listOf("[INFO][App] → warmUp(reason=cold-start) : String"), LoggingAspect.logs)
    }
}
