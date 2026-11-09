package io.vroomi.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vroomi.app.ui.AppRoot
import io.vroomi.app.ui.VroomiTheme

class MainActivity : ComponentActivity() {

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { runtime().start() }

    private fun runtime() = (application as VroomiApplication).runtime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask once, while parked at launch, for any declared permission the platform treats as runtime.
        // Normal / signature permissions are granted at install and are simply skipped here.
        val missing = declaredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty() || savedInstanceState != null) runtime().start() else permissionRequest.launch(missing.toTypedArray())

        setContent {
            val snapshot by runtime().hub.snapshot.collectAsStateWithLifecycle()
            VroomiTheme(night = snapshot.headlightsOn == true || isSystemInDarkTheme()) {
                AppRoot(runtime())
            }
        }
    }

    private fun declaredPermissions(): List<String> = runCatching {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.filter { it.startsWith("pleos.car.permission.") }.orEmpty()
    }.getOrDefault(emptyList())
}
