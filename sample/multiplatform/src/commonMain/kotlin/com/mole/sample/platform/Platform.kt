package com.mole.sample.platform

/** 현재 플랫폼의 이름을 반환합니다 (예: "JVM", "iOS", "macOS"). */
expect fun platformName(): String

/** 현재 시각을 Unix 에포크 기준 밀리초로 반환합니다. */
expect fun currentTimeMillis(): Long
