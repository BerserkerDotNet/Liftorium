package dev.liftorium.app

import android.app.Application
import android.util.Log
import dev.liftorium.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Liftorium [Application] class. Owns the single [AppContainer] for the
 * process lifetime so configuration changes never tear down the Room
 * database / use cases / DAOs.
 *
 * Registered in `AndroidManifest.xml` via `android:name=".LiftoriumApplication"`.
 *
 * On boot, kicks off `AppContainer.bootstrapBundledFixtures()` in an
 * application-scoped coroutine so the program library is populated from
 * `assets/programs/` before the user reaches the program detail screen.
 * The loader is idempotent on contentHash, so re-launching the app does
 * not re-insert existing program versions.
 */
public open class LiftoriumApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public open val container: AppContainer by lazy { AppContainer.create(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                container.bootstrapBundledFixtures()
            } catch (t: Throwable) {
                Log.e("LiftoriumApplication", "Fixture bootstrap failed", t)
            }
        }
    }
}
