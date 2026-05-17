// Root build file. Plugin versions are pinned in gradle/libs.versions.toml.
// Modules apply plugins explicitly; declaring them here with apply false
// keeps a single version source of truth across all modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.paparazzi) apply false
}
