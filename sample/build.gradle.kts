// Root build file - submodule configuration은 각 모듈의 build.gradle.kts에서 처리
//
// Every Kotlin/Android/Compose plugin used by ANY subproject is declared here with `apply
// false`, so it's resolved and loaded exactly once via one shared classloader scope. Without
// this, :composeApp (which also applies AGP) and a plain-KGP module (:core, :data, :feature-*)
// end up with the Kotlin Multiplatform plugin loaded via two DIFFERENT classloader scopes, and
// any shared build service (e.g. KotlinNativeBundleBuildService, used by iOS targets) then fails
// with "property ... loaded with [scope A] using a provider ... loaded with [scope B]".
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
}
