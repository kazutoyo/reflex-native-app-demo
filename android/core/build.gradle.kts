plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
  // Kotlin has no built-in JSON or async support, so these are the only two dependencies
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
