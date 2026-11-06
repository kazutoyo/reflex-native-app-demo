plugins {
  id("com.android.application")
  kotlin("plugin.compose")
}

android {
  namespace = "dev.kazutoyo.reflexweather"
  compileSdk = 37

  defaultConfig {
    applicationId = "dev.kazutoyo.reflexweather"
    minSdk = 33
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures { compose = true }
}

dependencies {
  implementation(project(":core"))
  implementation(platform("androidx.compose:compose-bom:2026.09.00"))
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.activity:activity-compose:1.12.4")
}
