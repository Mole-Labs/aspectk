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

dependencies {
    implementation(libs.kotlin.gradlePlugin)
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
