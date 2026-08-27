package sample.multiplatform.platform

/** Returns the current platform's name (e.g. "JVM", "iOS", "macOS"). */
expect fun platformName(): String

/** Returns the current time in milliseconds since the Unix epoch. */
expect fun currentTimeMillis(): Long
