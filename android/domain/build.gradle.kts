plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

dependencies {
    // :domain depends ONLY on :core (pure JVM). It must remain framework-free.
    // Adding Android, Room, or Compose dependencies here will silently break the
    // architecture guarantee; the architecture-guard unit test in this module
    // also fails loudly if such types become resolvable at runtime.
    api(project(":core"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
