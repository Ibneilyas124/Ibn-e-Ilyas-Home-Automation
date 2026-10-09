plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ibneilyas.home"
    compileSdk = 34
    signingConfigs {
        val ksPath = System.getenv("KEYSTORE_PATH")
        if (ksPath != null && file(ksPath).exists()) {
            create("ibn") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = "ibn"
                keyPassword = System.getenv("KEYSTORE_PASS")
            }
        }
    }
    defaultConfig {
        applicationId = "com.ibneilyas.home"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("ibn")?.let { signingConfig = it }
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
}
