package sample.platform

import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSinceReferenceDate

// NSDate reference epoch: 2001-01-01 00:00:00 UTC
// Unix epoch offset: 978307200 seconds
private const val REFERENCE_DATE_OFFSET_MS = 978307200_000L

actual fun platformName(): String = "Apple / ${NSProcessInfo.processInfo.operatingSystemVersionString}"

actual fun currentTimeMillis(): Long = (NSDate.timeIntervalSinceReferenceDate() * 1000).toLong() + REFERENCE_DATE_OFFSET_MS
