import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
    signing
}

group = "io.github.debanshu"
version = "1.0.0"

kotlin {
    applyDefaultHierarchyTemplate()

    // Explicit API mode for public library
    explicitApi()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shaderlib"
            isStatic = true
        }
    }

    jvm()

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

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }
    }
}

android {
    namespace = "io.github.debanshu.shaderlib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Publishing configuration
publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("ShaderLib")
                description.set("Kotlin Multiplatform library for GPU shader effects in Compose")
                url.set("https://github.com/debanshu/shaderlib")

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
                    url.set("https://github.com/debanshu/shaderlib")
                    connection.set("scm:git:git://github.com/debanshu/shaderlib.git")
                    developerConnection.set("scm:git:ssh://git@github.com/debanshu/shaderlib.git")
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
