pluginManagement {
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SillyCrypt"
include(":app")
include(":libsillycript:core")
include(":libsillycript:android")
include(":exfat")
include(":exfat-android")
include(":workprofile")
include(":core:serialization")
include(":core:di")
include(":settings")
include(":core:mapper")
include(":core:common")
include(":exfat-browser")
