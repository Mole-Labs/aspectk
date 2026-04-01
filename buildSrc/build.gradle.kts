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

val testKotlinLang =
    providers.gradleProperty("testKotlinLang").getOrElse(libs.versions.kotlin.get())

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$testKotlinLang")
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
