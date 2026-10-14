plugins {
    id("id.a3.android.library")
    id("id.a3.android.compose")
}

android {
    namespace = "id.a3.core.ui"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
