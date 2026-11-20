import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The explicit legacy mode compiles only the 0.1.3 factory contract.
val legacyConsumer = providers.gradleProperty("shaderxConsumerLegacy").map(String::toBoolean).getOrElse(false)
val factorySources = if (legacyConsumer) "legacy" else "development"

kotlin {
    jvmToolchain(21)
    androidLibrary {
        namespace = "io.github.debanshu.shaderx.validation.kmp"
        compileSdk = if (legacyConsumer) libs.versions.android.compileSdk.get().toInt() else 35
        minSdk = if (legacyConsumer) 33 else 31
        compilerOptions { jvmTarget.set(if (legacyConsumer) JvmTarget.JVM_21 else JvmTarget.JVM_17) }
    }
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain {
            kotlin.srcDir("../consumer-contracts/src")
            kotlin.srcDir("../consumer-contracts/$factorySources")
            if (!legacyConsumer) kotlin.srcDir(project(":samples:ShaderLab:composeApp").layout.buildDirectory.dir("generated-playground-consumer"))
            dependencies {
                // Supplying a version switches every target to the published/staged artifact.
                val consumerVersion = providers.gradleProperty("shaderxConsumerVersion").orNull
                if (consumerVersion == null) implementation(project(":shaderx"))
                else implementation("io.github.debanshu777:shaderx:$consumerVersion")
                // 0.1.3 did not expose its Compose compile dependencies transitively.
                if (legacyConsumer) {
                    implementation(libs.runtime)
                    implementation(libs.foundation)
                    implementation(libs.ui)
                }
            }
        }
        commonTest {
            if (!legacyConsumer) kotlin.srcDir("src/developmentTest/kotlin")
            dependencies { implementation(libs.kotlin.test) }
        }
    }
}

// Compile the exact playground output in a separate consumer, never a handwritten equivalent.
if (!legacyConsumer) tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") || it.name == "compileAndroidMain" }.configureEach {
    dependsOn(":samples:ShaderLab:composeApp:jvmTest")
}
