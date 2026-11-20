@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechMavenPublish)
    alias(libs.plugins.dokka)
}

group = "io.github.debanshu777"
// The release coordinate is declared once in root gradle.properties.
version = providers.gradleProperty("shaderxVersion").get()

kotlin {
    applyDefaultHierarchyTemplate()
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
        // KGP 2.3 validates JVM classfiles here; Android's new KMP plugin needs
        // the separate consumer/device gates. Native/Wasm dumps follow later.
        klib.enabled.set(false)
    }

    // Suppress beta warning for expect/actual classes (LruCache)
    @Suppress("OPT_IN_USAGE")
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "io.github.debanshu.shaderx"
        // Keep the published consumer floor separate from the contributor/sample SDK.
        compileSdk = 35
        minSdk = 31

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Compose 1.10's device-test asset wiring requires Android resources enabled.
        androidResources { enable = true }
        withDeviceTestBuilder {
            sourceSetTreeName = "androidDevice"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        // Isolate host tests from common bitmap tests that need a real backend.
        withHostTestBuilder {
            sourceSetTreeName = "androidHost"
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shaderx"
            isStatic = true
        }
    }

    jvm()

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "shaderx.js"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            // Exposed as `api` — the public surface returns Compose types (ImageBitmap, Modifier,
            // RenderEffect), so consumers of the published artifact must receive Compose transitively.
            api(libs.runtime)
            api(libs.foundation)
            api(libs.ui)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

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
            }
        }

        wasmJsMain {
            dependsOn(skiaMain)
        }

        // Exercise the same native Skia filter semantics on JVM, Wasm and iOS simulators.
        val skiaTest by creating {
            dependsOn(commonTest.get())
            dependencies { implementation(libs.kotlinx.coroutinesTest) }
        }
        jvmTest { dependsOn(skiaTest) }
        iosTest { dependsOn(skiaTest) }
        wasmJsTest { dependsOn(skiaTest) }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }

        named("androidDeviceTest") {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.androidx.test.runner)
            }
        }

        named("androidHostTest") {
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "shaderx", version.toString())

    pom {
        name.set("ShaderX")
        description.set(
            "GPU shader effects for Jetpack Compose and Compose Multiplatform on Android, iOS, " +
                "JVM desktop, and WebAssembly, with built-in effects and custom AGSL/SkSL shaders.",
        )
        url.set("https://github.com/Debanshu777/ShaderX")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        properties.set(
            mapOf(
                "kotlin.keywords" to "kotlin-multiplatform, compose-multiplatform, jetpack-compose, " +
                    "shader, agsl, sksl, gpu, image-processing, android, ios, desktop, webassembly",
            ),
        )

        developers {
            developer {
                id.set("debanshu777")
                name.set("Debanshu Datta")
            }
        }

        scm {
            url.set("https://github.com/Debanshu777/ShaderX")
            connection.set("scm:git:https://github.com/Debanshu777/ShaderX.git")
            developerConnection.set("scm:git:ssh://git@github.com/Debanshu777/ShaderX.git")
        }
    }
}

tasks.register("generateDocs") {
    group = "documentation"
    description = "Generates KDoc HTML via Dokka. Output: build/dokka/html."
    dependsOn("dokkaGeneratePublicationHtml")
}
