package weather.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import weather.core.AppRoot
import weather.core.Deps

class MainActivity : ComponentActivity() {
  private val location = DeviceLocation(this)
  private lateinit var root: AppRoot

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val http = if (debuggable) DebugHttp(UrlConnectionHttp()) else UrlConnectionHttp()
    root = AppRoot(Deps(http, location, PreferencesFavorites(this), resources.configuration.locales[0].language), lifecycleScope)
    root.open(intent.data?.let { "/" + (it.host ?: "") + (it.path ?: "") } ?: "/")
    if (http is DebugHttp) InspectorServer(root, http, lifecycleScope).start()
    setContent { RootView(root) }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { root.open("/" + (it.host ?: "") + (it.path ?: "")) }
  }
}
