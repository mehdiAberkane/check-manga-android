plugins {
    // AGP 9 compile Kotlin lui-même : plus de plugin org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
