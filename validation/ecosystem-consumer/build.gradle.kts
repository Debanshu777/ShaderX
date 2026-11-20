import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Consumer-only pins. Do not move specialist libraries into :shaderx's dependency graph.
kotlin {
    jvmToolchain(21)
    androidLibrary {
        namespace = "io.github.debanshu.shaderx.ecosystem"
        compileSdk = 35
        minSdk = 33 // This recipe uses runtime shaders, not just native blur.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shaderx"))
            implementation(libs.coil.compose)
            implementation(libs.haze)
            implementation(libs.kotlinx.coroutinesCore)
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

compose.desktop {
    application { mainClass = "io.github.debanshu.shaderx.ecosystem.MainKt" }
}
// Resolve default demo media relative to the checkout, rather than the module directory.
tasks.withType<JavaExec>().configureEach { workingDir = rootProject.projectDir }
