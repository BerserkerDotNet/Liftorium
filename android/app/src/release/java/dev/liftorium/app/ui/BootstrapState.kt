package dev.liftorium.app.ui

import kotlinx.collections.immutable.persistentListOf

/**
 * Release-variant shim. Returns an empty library so the launch-time
 * placeholder shows the empty-library copy until real DI replaces
 * this entry point. The in-memory `SampleStateFactory` lives in the
 * `debug` source set and is NOT compiled into the release APK.
 */
public fun bootstrapState(): LiftoriumNavState.Library =
    LiftoriumNavState.Library(versions = persistentListOf(), details = emptyMap())
