plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        create("build") {
            id = "com.mole.aspectK.build"
            implementationClass = "com.mole.build.AspectKBuildPlugin"
        }
    }
}
