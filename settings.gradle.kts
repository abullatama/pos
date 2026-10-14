pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "A3"

// Spike app runner
include(":app")

// App shells
include(":app-cafe")
include(":app-laundry")

// Core modules
include(":core:domain")
include(":core:data")
include(":core:sync")
include(":core:auth")
include(":core:printer")
include(":core:ui")

// Feature modules
include(":feature:cafe")
include(":feature:laundry")
include(":feature:shift")
include(":feature:payment")
include(":feature:admin")
