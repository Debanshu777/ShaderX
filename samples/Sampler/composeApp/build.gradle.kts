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
        namespace = "com.debanshu.shaderx.sampler.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        androidResources { enable = true }
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Sampler"; isStatic = true }
    }
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "composeApp.js" } }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shaderx)
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.components.resources)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        val skiaTest by creating { dependsOn(commonTest.get()) }
        jvmTest { dependsOn(skiaTest) }
        wasmJsTest { dependsOn(skiaTest) }
        iosTest { dependsOn(skiaTest) }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
    }
}

compose.desktop { application { mainClass = "com.debanshu.shaderx.sampler.MainKt" } }

compose.resources { packageOfResClass = "com.debanshu.shaderx.sampler.resources" }
