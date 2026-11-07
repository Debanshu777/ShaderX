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
        namespace = "com.debanshu.shaderlab.imagelib"
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
            baseName = "imagelib"
            isStatic = true
        }
    }

    jvm()

    wasmJs {
        browser()
    }

    sourceSets {
        val skiaMain by creating {
            dependsOn(commonMain.get())
        }
        iosMain { dependsOn(skiaMain) }
        jvmMain { dependsOn(skiaMain) }
        wasmJsMain { dependsOn(skiaMain) }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            // skiko-wasm-js is provided transitively by Compose Multiplatform ui:1.10.3
            // at version 0.9.37.4 — do NOT pin it here with the old 0.144.5 version,
            // as that causes a runtime WebAssembly.instantiate() LinkError.
        }
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
            implementation(libs.kotlinx.coroutinesCore)
        }
        androidMain.dependencies {
            implementation(libs.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.mpfilepicker)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}
