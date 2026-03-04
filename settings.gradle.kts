// C:/Users/erick/AndroidStudioProjects/SafeTrack/settings.gradle.kts

pluginManagement {
    repositories {
        google() // <--- Simples assim! Sem o bloco 'content'
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

rootProject.name = "SafeTrack"
include(":app")
