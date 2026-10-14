plugins {
    `kotlin-dsl`
}

group = "id.a3.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    testImplementation(libs.junit)
}
