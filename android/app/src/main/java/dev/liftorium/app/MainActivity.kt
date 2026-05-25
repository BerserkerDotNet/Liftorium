package dev.liftorium.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.liftorium.app.ui.LiftoriumNavHost
import dev.liftorium.app.ui.bootstrapState

/**
 * App entry point. Hosts the program library / detail / today flow
 * against the variant-aware `bootstrapState()` shim: in `debug` the
 * shim returns an in-memory sample library so Paparazzi/launch-time
 * have content; in `release` it returns an empty library so no sample
 * data ships. The `android-ui-polish` workstream replaces the raw
 * `MaterialTheme` with the canonical `LiftoriumTheme`; a later
 * `android-program-runner` follow-on slice replaces the bootstrap
 * shim with the wired manual DI container (Room +
 * ProgramResourceLoader + use cases).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiftoriumApp()
        }
    }
}

@Composable
fun LiftoriumApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LiftoriumNavHost(initial = bootstrapState())
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiftoriumAppPreview() {
    LiftoriumApp()
}
