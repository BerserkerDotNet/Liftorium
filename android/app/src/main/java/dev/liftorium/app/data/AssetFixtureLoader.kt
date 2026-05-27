package dev.liftorium.app.data

import android.content.Context
import android.util.Log
import dev.liftorium.data.resource.ProgramResourceLoader

/**
 * Reads bundled program-resource fixture JSON files from the app's
 * `assets/programs/` directory and hands each one to
 * [ProgramResourceLoader.load]. Idempotent on contentHash: re-running
 * the loader against the same hash yields a `LoaderResult.Idempotent`
 * outcome and no Room writes happen.
 *
 * Lives in `dev.liftorium.app.data` (NOT `dev.liftorium.app.ui`) so it
 * may legally import `:data`. The architecture-fitness task
 * `verifyAppUiBoundary` only scans the `ui` subpackage tree.
 */
public class AssetFixtureLoader(
    private val context: Context,
    private val loader: ProgramResourceLoader,
) {

    public suspend fun loadAll() {
        val assetManager = context.assets
        val files = try {
            assetManager.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }
        } catch (io: java.io.IOException) {
            Log.e(LOG_TAG, "Failed to list assets/$ASSET_DIR", io)
            return
        }
        for (fileName in files) {
            val raw = try {
                assetManager.open("$ASSET_DIR/$fileName").bufferedReader().use { it.readText() }
            } catch (io: java.io.IOException) {
                Log.e(LOG_TAG, "Failed to read fixture asset $fileName", io)
                continue
            }
            try {
                val result = loader.load(raw)
                Log.i(LOG_TAG, "Bootstrap loader: $fileName -> ${result::class.simpleName}")
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Loader threw on $fileName", t)
            }
        }
    }

    public companion object {
        public const val ASSET_DIR: String = "programs"
        private const val LOG_TAG = "AssetFixtureLoader"
    }
}
