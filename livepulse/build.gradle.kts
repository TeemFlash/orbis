plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val emulatorMode = providers.gradleProperty("livepulseEmulators").isPresent
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "kz.semey.livepulse"
    compileSdk = 34
    defaultConfig {
        applicationId = "kz.semey.livepulse"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "USE_EMULATORS", emulatorMode.toString())
        buildConfigField("String", "EMULATOR_HOST", "\"${providers.gradleProperty("livepulseEmulatorHost").getOrElse("10.0.2.2")}\"")
        manifestPlaceholders["cleartextTraffic"] = emulatorMode.toString()
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    testImplementation("junit:junit:4.13.2")
}
