plugins {
    id("id.a3.android.library")
    id("id.a3.android.compose")
}

android {
    namespace = "id.a3.feature.laundry"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":core:printer"))
    implementation(project(":feature:shift"))
    implementation(project(":feature:payment"))
    implementation(libs.retrofit.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
