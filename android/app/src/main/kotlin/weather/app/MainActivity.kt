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
    root = AppRoot(Deps(UrlConnectionHttp(), location, PreferencesFavorites(this)), lifecycleScope)
    root.open(intent.data?.let { "/" + (it.host ?: "") + (it.path ?: "") } ?: "/")
    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) InspectorServer(root, lifecycleScope).start()
    setContent { RootView(root) }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { root.open("/" + (it.host ?: "") + (it.path ?: "")) }
  }
}
