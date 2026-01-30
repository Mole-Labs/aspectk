plugins {
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
    alias(libs.plugins.diffplug.spotless) apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktlint("1.8.0")
                .editorConfigOverride(
                    mapOf(
                        "indent_size" to "4",
                        "continuation_indent_size" to "4",
                        "ktlint_standard_no-wildcard-imports" to "disabled",
                    ),
                )
            licenseHeaderFile(rootProject.file("spotless/LICENSE.txt"))
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("kts") {
            target("**/*.kts")
            targetExclude("**/build/**/*.kts")
            licenseHeaderFile(rootProject.file("spotless/LICENSE.txt"), "(^(?![\\/ ]\\*).*$)")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
