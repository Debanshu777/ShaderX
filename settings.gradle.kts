rootProject.name = "ShaderXProject"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Opt-in repository for validation against staged Maven artifacts.
        providers.gradleProperty("shaderxConsumerRepository").orNull?.let { repository ->
            maven { url = uri(repository) }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":shaderx")
include(":samples:ShaderLab:androidApp")
include(":samples:ShaderLab:composeApp")
include(":samples:ShaderLab:imagelib")
include(":samples:ShaderLab:audiolib")
include(":samples:ShaderLab:iosApp")
include(":samples:VerticalCarousel")
include(":samples:ASCIICamera:composeApp")
include(":samples:ASCIICamera:androidApp")

// Separate compilations ensure fixtures cannot access library internals or friend paths.
include(":validation:kmp-consumer")
include(":validation:android-consumer")

include(":validation:android-app")

// Optional specialist integrations compile outside the core library and its friend tests.
include(":validation:ecosystem-consumer")

// One focused visual sampler, independent of ShaderLab.
include(":samples:Sampler:composeApp")
include(":samples:Sampler:androidApp")
