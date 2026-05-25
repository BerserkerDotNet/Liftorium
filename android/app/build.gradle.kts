plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.detekt)
}

android {
    namespace = "dev.liftorium.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.liftorium.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Robolectric needs merged Android resources and AssetManager access for
    // host-side Compose rendering. Do NOT enable isReturnDefaultValues; that
    // hides real Android stubs and creates false confidence in render tests.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Immutable collections backing the @Immutable UI state DTOs. The
    // Compose compiler treats kotlinx.collections.immutable.ImmutableList
    // as stable, unlike the read-only `List` interface which it must
    // assume could be backed by a MutableList at runtime. Enforced by
    // Detekt's compose-rules `UnstableCollections` rule.
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // NOTE: archunit-junit4 is intentionally NOT a `:app` test dependency.
    // The Android Gradle Plugin's ASM transform on the unit-test classpath
    // crashes on archunit's shaded thirdparty JAR layout ("duplicate entry:
    // com/tngtech/archunit/thirdparty/") — a known AGP+ArchUnit
    // incompatibility. The architectural boundary between
    // `dev.liftorium.app.ui..` and `dev.liftorium.data..` is therefore
    // enforced by the root-level `verifyModuleGraph` task (which only
    // permits the edge `:app -> :data` to exist), reviewer discipline, and
    // the `:data` ArchUnit rule that forbids `:data` from depending on
    // `:app`. A future `:archtests` JVM-only module could host the
    // `:app`-side rule without triggering the ASM transform.

    // Host-side UI rendering: Robolectric runs Android-aware tests on the JVM;
    // Compose ui-test rule renders LiftoriumApp without an emulator.
    // ui-test-manifest stays on debugImplementation only — its AndroidManifest
    // entries merge into the debug variant and are visible to testDebugUnitTest.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit.ktx)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Detekt rules that enforce the @Immutable promise (no MutableState/
    // MutableCollection params, stable composable params, etc.). The
    // ruleset is auto-discovered via the `detektPlugins` configuration.
    // See docs/decisions.md.
    detektPlugins(libs.detekt.compose.rules)
}

// ---------------------------------------------------------------------------
// Detekt — Compose stability discipline guard
// ---------------------------------------------------------------------------
//
// Scope: this module's UI sources only (`src/<sourceSet>/java`,
// `src/<sourceSet>/kotlin`). We intentionally do NOT enable the full Detekt
// default ruleset because we are not adopting Detekt as a general code-style
// linter — only as the enforcement mechanism for the `@Immutable` /
// Compose-stability promise documented in `docs/decisions.md`.
//
// The `mrmans0n/compose-rules` ruleset is loaded via `detektPlugins`. Our
// `config/detekt/detekt-compose.yml` disables every default Detekt rule and
// enables only the Compose stability ones (MutableParams,
// UnstableCollections, ComposableParamOrder, etc.). Wired into `check` so
// CI fails on regressions.
detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("config/detekt/detekt-compose.yml"))
    buildUponDefaultConfig = false
    parallel = true
    source.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
            "src/debug/java",
            "src/debug/kotlin",
            "src/release/java",
            "src/release/kotlin",
        ),
    )
}

tasks.named("check") {
    dependsOn("detekt")
}
