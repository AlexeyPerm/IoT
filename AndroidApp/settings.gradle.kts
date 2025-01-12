pluginManagement {
    repositories {
        google()  // Репозиторий Google
        mavenCentral()  // Репозиторий Maven Central
        gradlePluginPortal()  // Репозиторий Gradle Plugin Portal
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()  // Репозиторий Google
        mavenCentral()  // Репозиторий Maven Central
        maven { url = uri("https://www.jitpack.io" ) }  // Репозиторий JitPack
    }
}

rootProject.name = "IoT"
include(":app")
