import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink

plugins {
    id("kotlin-conventions")
    id("native-conventions")
}

tasks.withType<KotlinJsIrLink>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xwasm-enable-array-range-checks")
}