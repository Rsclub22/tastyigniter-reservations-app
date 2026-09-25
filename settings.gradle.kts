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

rootProject.name = "TastyIgniterReservations"

// :shared traegt den gesamten Code und beide Ziele (Android-Bibliothek + Desktop).
// :app ist nur noch die Android-Anwendung darum herum - AGP 9 laesst
// com.android.application nicht mehr mit dem Multiplatform-Plugin im selben Modul zu.
include(":shared")
include(":app")
