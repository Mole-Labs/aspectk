package sample.multiplatform.platform

actual fun platformName(): String = "JVM (${System.getProperty("java.version")})"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
