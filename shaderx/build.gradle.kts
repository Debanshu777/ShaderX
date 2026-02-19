import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
    signing
}

group = "com.github.Debanshu777.ShaderX"
version = "0.1.1"

kotlin {
    applyDefaultHierarchyTemplate()

    // Explicit API mode for public library
    explicitApi()

    androidLibrary {
        namespace = "io.github.debanshu.shaderx"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
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

// Publishing configuration
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
                        id.set("debanshu")
                        name.set("Debanshu")
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

    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
        // Uncomment for Maven Central publishing
        // maven {
        //     name = "sonatype"
        //     url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        //     credentials {
        //         username = project.findProperty("ossrhUsername") as String? ?: ""
        //         password = project.findProperty("ossrhPassword") as String? ?: ""
        //     }
        // }
    }
}

// Signing configuration (for Maven Central)
signing {
    // Configure signing only if credentials are available
    val signingKey = project.findProperty("signing.key") as String?
    val signingPassword = project.findProperty("signing.password") as String?
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// Task to generate documentation
tasks.register("generateDocs") {
    group = "documentation"
    description = "Generates API documentation"
    // Add Dokka configuration here when ready
}
