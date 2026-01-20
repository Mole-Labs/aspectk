plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
    alias(libs.plugins.buildConfig)
    kotlin("kapt")
    `java-gradle-plugin`
}

gradlePlugin {
    this.plugins {
        register("aspectKPlugin") {
            id = "com.mole.aspectk"
            implementationClass = "AspectKGradleSubPlugin"
        }
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

buildConfig {
    packageName("com.mole.aspectk")
    useKotlinOutput {
        topLevelConstants = true
        internalVisibility = true
    }
    buildConfigField("String", "VERSION", providers.gradleProperty("VERSION_NAME").map { "\"$it\"" })
    buildConfigField("String", "PLUGIN_ID", libs.versions.pluginId.map { "\"$it\"" })
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin.api)
}