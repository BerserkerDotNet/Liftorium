plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
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
    // kotlinx-serialization-json is pure Kotlin/JVM and carries no Android
    // framework types. It is required for android-program-runner ProgramResource DTOs and the
    // JsonElement-based content-hash canonicalizer that mirrors
    // schema/hash.ts. See android-program-runner workstream.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.archunit.junit4)
}
