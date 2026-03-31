plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

val kotlinVersion =
    providers
        .gradleProperty("kotlinVersion")
        .getOrElse(libs.versions.kotlin.get())

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation(libs.vanniktech.publish.gradle)
    implementation(libs.gmazzo.buildconfig)
}

gradlePlugin {
    plugins {
        create("build") {
            id = "io.github.mole-labs.aspectk.build"
            implementationClass = "io.github.molelabs.aspectk.build.AspectKBuildPlugin"
        }
    }
}
