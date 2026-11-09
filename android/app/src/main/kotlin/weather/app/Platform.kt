package weather.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import weather.core.City
import weather.core.Coords
import weather.core.CoreError
import weather.core.CoreJson
import weather.core.FavoritesStore
import weather.core.HttpClient
import weather.core.HttpResponse
import weather.core.LocationService
import weather.core.Permission

// Real implementations of the dependencies Core declares (the Mobile module side in Reflex).

class UrlConnectionHttp : HttpClient {
  override suspend fun get(url: String): HttpResponse = withContext(Dispatchers.IO) {
    try {
      val conn = URL(url).openConnection() as HttpURLConnection
      val status = conn.responseCode
      HttpResponse(status, (if (status < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: "")
    } catch (e: java.io.IOException) {
      throw CoreError("Cannot connect to the network")
    }
  }
}

/** Permission requests go through a launcher registered from the Activity. Create it before onCreate. */
class DeviceLocation(private val activity: ComponentActivity) : LocationService {
  private var waiting: CompletableDeferred<Boolean>? = null
  private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    waiting?.complete(result.values.any { it })
  }

  private fun granted() =
    activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

  override suspend fun requestPermission(): Permission {
    if (granted()) return Permission(granted = true, canAskAgain = true)
    val deferred = CompletableDeferred<Boolean>().also { waiting = it }
    launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    val ok = deferred.await()
    // After two denials, Android no longer shows the dialog
    return Permission(ok, canAskAgain = ok || activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
  }

  @Suppress("MissingPermission")
  override suspend fun currentPosition(): Coords {
    val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { return Coords(it.latitude, it.longitude) }
    // Do not stay loading forever on devices that never return a fix (e.g. emulators)
    return withTimeoutOrNull(15_000) {
      suspendCancellableCoroutine { cont ->
        manager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, activity.mainExecutor) { location ->
          cont.resume(location?.let { Coords(it.latitude, it.longitude) })
        }
      }
    } ?: throw CoreError("Cannot get the location")
  }

  override suspend fun placeName(coords: Coords): String? = suspendCancellableCoroutine { cont ->
    Geocoder(activity, activity.resources.configuration.locales[0]).getFromLocation(coords.latitude, coords.longitude, 1, object : Geocoder.GeocodeListener {
      override fun onGeocode(addresses: MutableList<android.location.Address>) {
        val a = addresses.firstOrNull()
        cont.resume(a?.locality ?: a?.subAdminArea ?: a?.adminArea)
      }
      override fun onError(errorMessage: String?) = cont.resumeWith(Result.failure(CoreError(errorMessage ?: "Reverse geocoding failed")))
    })
  }

  override fun openSettings() {
    activity.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
    )
  }
}

class PreferencesFavorites(context: Context) : FavoritesStore {
  private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
  private val serializer = ListSerializer(City.serializer())

  override suspend fun list(): List<City> =
    prefs.getString("favorites", null)?.let { CoreJson.decodeFromString(serializer, it) } ?: emptyList()

  override suspend fun save(cities: List<City>) {
    prefs.edit().putString("favorites", CoreJson.encodeToString(serializer, cities)).apply()
  }
}
