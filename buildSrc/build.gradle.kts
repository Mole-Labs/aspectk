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
}

gradlePlugin {
    plugins {
        create("build") {
            id = "com.mole.aspectK.build"
            implementationClass = "com.mole.build.AspectKBuildPlugin"
        }
    }
}
