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

rootProject.name = "ASL Wallet"
include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:security")
include(":core:testing")
include(":feature:auth")
include(":feature:home")
include(":feature:profile")
include(":feature:aeps")
include(":feature:wallet")
include(":feature:recharge")
include(":feature:bbps")
include(":feature:dmt")
include(":feature:reports")
