plugins {
    id("id.a3.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "id.a3.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
