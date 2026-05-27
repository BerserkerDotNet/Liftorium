plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.liftorium.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    // Room schema export. Path locked in docs/decisions.md
    // (2026-05-16: foundation Room schema export path).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
        // Robolectric host-side MigrationTest reads schemas from assets via
        // MigrationTestHelper; mirror the export path into the unit-test
        // source set so :data:testDebugUnitTest can verify the v1→v2
        // migration without requiring an emulator/device.
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        // Robolectric host-side integration tests for ProgramResourceLoader
        // and the Room database baseline (android-program-runner workstream).
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":domain"))
    api(project(":core"))

    // `api` (not `implementation`): LiftoriumDatabase's supertype
    // androidx.room.RoomDatabase leaks into the public API of `:data`
    // (callers of LiftoriumDatabaseFactory.create() receive the class),
    // so consumers need the Room runtime on their compile classpath.
    // Production Room annotations (Entity/Dao/Database) still live ONLY
    // in `:data:main` — enforced by the root `verifyRoomLocality` task.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit.ktx)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.archunit.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
