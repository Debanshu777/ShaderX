@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "com.debanshu.shaderlab.audiolib"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "audiolib"
            isStatic = true
        }
    }

    jvm()

    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
            implementation(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // Skia-backed platforms (iOS / Desktop / Wasm) share one ImageBitmap packing path.
        val skiaMain by creating {
            dependsOn(commonMain.get())
        }

        iosMain {
            dependsOn(skiaMain)
        }

        jvmMain {
            dependsOn(skiaMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        wasmJsMain {
            dependsOn(skiaMain)
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
    }
}
