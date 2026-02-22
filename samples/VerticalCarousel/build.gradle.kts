import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(localPropertiesFile.inputStream())
        }
    }

android {
    namespace = "com.debanshu.verticalcarousel"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.debanshu.verticalcarousel"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"${localProperties.getProperty("TMDB_ACCESS_TOKEN", "")}\"")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Compose
    implementation(libs.runtime)
    implementation(libs.foundation)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.ui)
    implementation(libs.components.resources)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.ui.tooling)

    // AndroidX
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    // Coroutines
    implementation(libs.kotlinx.coroutinesCore)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Ktor (HTTP client)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Image loading
    implementation(libs.landscapist.coil3)
    implementation(libs.landscapist.animation)
    implementation(libs.landscapist.placeholder)

    // Palette for color extraction
    implementation(libs.androidx.palette)

    // ShaderX library
    implementation("com.github.Debanshu777.Shaderx:shaderx:0.1.1")
}
