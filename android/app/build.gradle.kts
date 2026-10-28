import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.shelly.overlayprompter"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.shelly.overlayprompter"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    val ksFile = File(System.getProperty("user.home"), "keys/keystore.properties")
    signingConfigs {
        if (ksFile.exists()) create("release") {
            val k = Properties().apply { ksFile.inputStream().use { load(it) } }
            storeFile = file(k.getProperty("storeFile"))
            storePassword = k.getProperty("storePassword")
            keyAlias = k.getProperty("keyAlias")
            keyPassword = k.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
