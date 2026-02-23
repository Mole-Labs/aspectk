/*
 * Copyright (C) 2026 aspectk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
    id("com.mole.aspectK.build")
    alias(libs.plugins.buildConfig)
    kotlin("kapt")
    `java-gradle-plugin`
}

aspectKBuild {
    publish("AspectK Gradle Plugin")
    enableBackwardsCompatibility()
}

gradlePlugin {
    this.plugins {
        create("aspectK") {
            id = "com.mole.aspectK"
            implementationClass = "AspectKGradleSubPlugin"
        }
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
