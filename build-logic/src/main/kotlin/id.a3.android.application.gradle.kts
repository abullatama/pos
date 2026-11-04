import java.util.Properties
import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension> {
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Penandatanganan rilis: baca keystore.properties (di luar git). Awalan: "cafe" atau "laundry".
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val signingPrefix = project.name.removePrefix("app-")
    val releaseStoreFile = keystoreProps.getProperty("$signingPrefix.storeFile")
    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = keystoreProps.getProperty("$signingPrefix.storePassword")
                keyAlias = keystoreProps.getProperty("$signingPrefix.keyAlias")
                keyPassword = keystoreProps.getProperty("$signingPrefix.keyPassword")
            }
        }
        buildTypes {
            getByName("release") { signingConfig = signingConfigs.getByName("release") }
        }
    } else {
        logger.warn("keystore.properties tidak memuat '$signingPrefix.*': APK rilis ${project.name} TIDAK bertanda tangan.")
    }
}

configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
    jvmToolchain(17)
}
