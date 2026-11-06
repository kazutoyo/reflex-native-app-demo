package weather.cli

import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import weather.core.City
import weather.core.Coords
import weather.core.CoreError
import weather.core.CoreJson
import weather.core.FavoritesStore
import weather.core.HttpClient
import weather.core.HttpResponse
import weather.core.LocationService
import weather.core.Permission

/** Returns fixtures (saved real Open-Meteo responses). Unknown URLs are errors; never hits the network. */
class FixtureHttp : HttpClient {
  sealed interface Mode {
    data object Fixtures : Mode
    data object Offline : Mode
    data class Status(val status: Int) : Mode
  }

  var mode: Mode = Mode.Fixtures
  var weatherCode: Int? = null
  var requests = 0
  private val dir = File(System.getenv("FIXTURES") ?: "fixtures")

  private fun file(name: String) = File(dir, name).readText()

  override suspend fun get(url: String): HttpResponse {
    requests++
    return when (val mode = mode) {
      Mode.Offline -> throw CoreError("ネットワークに接続できません")
      is Mode.Status -> HttpResponse(mode.status, "{}")
      Mode.Fixtures -> HttpResponse(200, fixture(url))
    }
  }

  fun fixture(url: String): String {
    val uri = URI(url)
    val query = (uri.rawQuery ?: "").split("&").filter { it.isNotEmpty() }.associate {
      val (k, v) = it.split("=", limit = 2) + ""
      k to URLDecoder.decode(v, Charsets.UTF_8)
    }
    when (uri.path) {
      "/v1/forecast" -> {
        val body = file("forecast-tokyo.json")
        val code = weatherCode ?: return body
        // Override current weather and today's forecast (for rain, snow, and similar cases)
        val json = CoreJson.parseToJsonElement(body).jsonObject
        val current = json["current"]!!.jsonObject
        val daily = json["daily"]!!.jsonObject
        val codes = daily["weather_code"]!!.jsonArray
        return JsonObject(
          json + mapOf(
            "current" to JsonObject(current + ("weather_code" to JsonPrimitive(code))),
            "daily" to JsonObject(daily + ("weather_code" to JsonArray(listOf(JsonPrimitive(code)) + codes.drop(1)))),
          )
        ).toString()
      }
      "/v1/search" -> return file(if (query["name"] == "Tokyo") "search-tokyo.json" else "search-empty.json")
      "/v1/get" -> {
        if (query["id"] == "1850147") return file("city-1850147.json")
        val results = CoreJson.parseToJsonElement(file("search-tokyo.json")).jsonObject["results"]!!.jsonArray
        results.firstOrNull { it.jsonObject["id"]!!.jsonPrimitive.content == query["id"] }?.let { return it.toString() }
      }
    }
    throw CoreError("フィクスチャがない: $url")
  }
}

/** Stands in for LocationManager. Mutate fields to simulate things like "the user changed a setting". */
class FakeLocation : LocationService {
  var permission = Permission(granted = true, canAskAgain = true)
  var positionError: String? = null
  var place: String? = "新宿区"
  var placeError: String? = null
  var settingsOpened = 0

  override suspend fun requestPermission() = permission

  override suspend fun currentPosition(): Coords {
    positionError?.let { throw CoreError(it) }
    return Coords(35.6895, 139.69171)
  }

  override suspend fun placeName(coords: Coords): String? {
    placeError?.let { throw CoreError(it) }
    return place
  }

  override fun openSettings() {
    settingsOpened++
  }
}

/** Stands in for SharedPreferences. */
class MemoryFavorites : FavoritesStore {
  var items = listOf<City>()
  override suspend fun list() = items
  override suspend fun save(cities: List<City>) {
    items = cities
  }
}

/** Blocks all outbound HTTP from the process and counts attempts (detected even if the app swallows the error). */
object BlockNetwork : ProxySelector() {
  var attempts = 0
  override fun select(uri: URI?): List<Proxy> {
    attempts++
    throw IOException("テスト中のネットワークは禁止: $uri")
  }
  override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
}
