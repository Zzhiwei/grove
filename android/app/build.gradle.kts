plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "local.grove.focus"
    compileSdk = 37

    defaultConfig {
        applicationId = "local.grove.focus"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    testOptions.unitTests.isReturnDefaultValues = true // android.util.Log etc. are no-ops in JVM tests
    testOptions.unitTests.all {
        it.systemProperty("fixtures", rootProject.file("../shared/fixtures").path)
        it.inputs.dir(rootProject.file("../shared/fixtures")) // rerun tests when fixtures change
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-okhttp:3.5.1") // same Ktor version supabase-kt 3.8.0 is built with
    testImplementation("junit:junit:4.13.2")
}
