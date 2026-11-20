import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
android {
    namespace = "com.debanshu.shaderx.sampler"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.debanshu.shaderx.sampler"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
dependencies {
    implementation(projects.samples.sampler.composeApp)
    implementation(libs.androidx.activity.compose)
}
