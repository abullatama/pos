plugins {
    id("id.a3.android.library")
    id("id.a3.android.compose")
}

android {
    namespace = "id.a3.feature.admin"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
}
