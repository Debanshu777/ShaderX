import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "io.github.debanshu.shaderx.validation.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "io.github.debanshu.shaderx.validation.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "development"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
dependencies {
    implementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.testJunit)
    val consumerVersion = providers.gradleProperty("shaderxConsumerVersion").orNull
    if (consumerVersion == null) implementation(project(":shaderx"))
    else implementation("io.github.debanshu777:shaderx:$consumerVersion")
}
