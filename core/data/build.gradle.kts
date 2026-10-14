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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
