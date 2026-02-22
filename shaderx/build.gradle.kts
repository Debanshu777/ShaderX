@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

group = "com.github.Debanshu777.ShaderX"
version = "0.1.2"

kotlin {
    applyDefaultHierarchyTemplate()
    explicitApi()

    androidLibrary {
        namespace = "io.github.debanshu.shaderx"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
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

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("ShaderX")
                description.set("Kotlin Multiplatform library for GPU shader effects in Compose")
                url.set("https://github.com/Debanshu777/ShaderX")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("debanshu777")
                        name.set("Debanshu Datta")
                    }
                }

                scm {
                    url.set("https://github.com/Debanshu777/ShaderX")
                    connection.set("scm:git:git://github.com/Debanshu777/ShaderX.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Debanshu777/ShaderX.git")
                }
            }
        }
    }
}

tasks.register("generateDocs") {
    group = "documentation"
    description = "Generates API documentation"
}
