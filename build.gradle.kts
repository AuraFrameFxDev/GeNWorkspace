plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false

    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false

    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false

    alias(libs.plugins.hiltAndroid) apply false

    alias(libs.plugins.navigationSafeargsKotlin) apply false

    alias(libs.plugins.secretsGradlePlugin) apply false
}

// Task to clean build directories
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}