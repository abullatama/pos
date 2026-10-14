plugins {
    id("id.a3.android.library")
}

android {
    namespace = "id.a3.core.auth"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
