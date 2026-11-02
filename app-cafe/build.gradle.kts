plugins {
    id("id.a3.android.application")
}

android {
    namespace = "id.a3coffeeshop"

    defaultConfig {
        applicationId = "id.a3coffeeshop"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":feature:cafe"))
    implementation(project(":feature:shift"))
    implementation(project(":feature:payment"))
    implementation(project(":feature:admin"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":core:auth"))
    implementation(project(":core:printer"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
