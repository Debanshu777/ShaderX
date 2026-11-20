import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
}

// The explicit legacy mode compiles only the 0.1.3 factory contract.
val legacyConsumer = providers.gradleProperty("shaderxConsumerLegacy").map(String::toBoolean).getOrElse(false)
val factorySources = if (legacyConsumer) "legacy" else "development"

android {
    namespace = "io.github.debanshu.shaderx.validation.android"
    compileSdk = if (legacyConsumer) libs.versions.android.compileSdk.get().toInt() else 35
    defaultConfig { minSdk = if (legacyConsumer) 33 else 31 }
    compileOptions {
        sourceCompatibility = if (legacyConsumer) JavaVersion.VERSION_21 else JavaVersion.VERSION_17
        targetCompatibility = if (legacyConsumer) JavaVersion.VERSION_21 else JavaVersion.VERSION_17
    }
}


kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(if (legacyConsumer) JvmTarget.JVM_21 else JvmTarget.JVM_17) }
}

androidComponents.onVariants { variant ->
    variant.sources.kotlin?.addStaticSourceDirectory("../consumer-contracts/src")
    variant.sources.kotlin?.addStaticSourceDirectory("../consumer-contracts/$factorySources")
    if (!legacyConsumer) variant.sources.kotlin?.addStaticSourceDirectory("../../samples/ShaderLab/composeApp/build/generated-playground-consumer")
}

dependencies {
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

if (!legacyConsumer) tasks.matching { (it.name.startsWith("compile") && it.name.endsWith("Kotlin")) || it.name.startsWith("extract") && it.name.endsWith("Annotations") }.configureEach {
    dependsOn(":samples:ShaderLab:composeApp:jvmTest")
}
