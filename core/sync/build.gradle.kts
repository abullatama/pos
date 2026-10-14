plugins {
    id("id.a3.android.library")
}

android {
    namespace = "id.a3.core.sync"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
