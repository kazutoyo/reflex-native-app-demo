package weather.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class HttpResponse(val status: Int, val body: String)

/** Networking. The app passes HttpURLConnection; the CLI passes an implementation that returns fixtures. */
fun interface HttpClient {
  suspend fun get(url: String): HttpResponse
}

data class Permission(val granted: Boolean, val canAskAgain: Boolean)

/** Location and the path to the Settings app. The app uses LocationManager; the CLI uses a fake. */
interface LocationService {
  suspend fun requestPermission(): Permission
  suspend fun currentPosition(): Coords
  /** Reverse geocoding (coordinates -> city name). null if unknown. */
  suspend fun placeName(coords: Coords): String?
  fun openSettings()
}

/** Favorites stored on the device. The app uses SharedPreferences; the CLI uses memory. */
interface FavoritesStore {
  suspend fun list(): List<City>
  suspend fun save(cities: List<City>)
}

@Serializable
enum class Tab {
  @SerialName("weather") Weather,
  @SerialName("search") Search,
  @SerialName("favorites") Favorites,
}

sealed interface Route {
  data class City(val id: Int) : Route
}

/** Screen navigation. AppRoot implements it and passes it to ViewModels. */
interface Navigator {
  fun push(route: Route)
  fun selectTab(tab: Tab)
}

/** Dependencies injected via the constructor. No DI framework. */
class Deps(http: HttpClient, val location: LocationService, val favorites: FavoritesStore) {
  val api = OpenMeteo(http)
}
