pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "weather"

// core: UI-less Core (a pure JVM module; does not use android.*)
// cli: CLI that runs core headless on the JVM
// app: the Compose app
include(":core", ":cli", ":app")
