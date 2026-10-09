plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    // No AspectK plugin here -- :data declares no @Aspect and no advice-target annotations,
    // demonstrating that applying the plugin is opt-in per module, not build-wide.
}

android {
    namespace = "sample.multiplatform.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm()
    jvmToolchain(17)
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    js { nodejs() }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: AppDatabase (public) extends RoomDatabase, so consumers
            // touching the `database` property need RoomDatabase on their own classpath too.
            api(libs.androidx.room.runtime)
        }
        // Room 3 needs an explicit driver. The bundled one has no JS artifact, so it can't sit
        // in commonMain; the web targets get the web worker driver instead.
        jvmMain.dependencies { implementation(libs.androidx.sqlite.bundled) }
        androidMain.dependencies { implementation(libs.androidx.sqlite.bundled) }
        appleMain.dependencies { implementation(libs.androidx.sqlite.bundled) }
        jsMain.dependencies { implementation(libs.androidx.sqlite.web) }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJs", libs.androidx.room.compiler)
}

// Run commonMain KSP before any platform compilation.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
