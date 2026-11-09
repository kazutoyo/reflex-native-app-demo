// Reflex-style CLI. Runs Core headless on the JVM and speaks NDJSON over stdin/stdout (same protocol as the iOS weather-cli).
package weather.cli

import java.net.ProxySelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import weather.core.AppRoot
import weather.core.CoreError
import weather.core.CoreJson
import weather.core.Deps
import weather.core.OpenMeteo
import weather.core.snapshot

class Session(private val scope: CoroutineScope) {
  var http = FixtureHttp()
  var location = FakeLocation()
  var favorites = MemoryFavorites()
  var root: AppRoot? = null

  /** Shared by launch and env: changes the device environment */
  fun apply(req: JsonObject) {
    req["location"]?.jsonObject?.let { loc ->
      loc["granted"]?.jsonPrimitive?.booleanOrNull?.let { location.permission = location.permission.copy(granted = it) }
      loc["canAskAgain"]?.jsonPrimitive?.booleanOrNull?.let { location.permission = location.permission.copy(canAskAgain = it) }
      loc["positionError"]?.jsonPrimitive?.contentOrNull?.let { location.positionError = it }
      if ("place" in loc) location.place = loc["place"]?.jsonPrimitive?.contentOrNull
      loc["placeError"]?.jsonPrimitive?.contentOrNull?.let { location.placeError = it }
    }
    when (val h = req["http"]) {
      JsonPrimitive("fixtures") -> http.mode = FixtureHttp.Mode.Fixtures
      JsonPrimitive("offline") -> http.mode = FixtureHttp.Mode.Offline
      is JsonObject -> http.mode = FixtureHttp.Mode.Status(h["status"]?.jsonPrimitive?.intOrNull ?: 500)
      else -> {}
    }
    req["weatherCode"]?.jsonPrimitive?.intOrNull?.let { http.weatherCode = it }
  }

  suspend fun handle(req: JsonObject): JsonObject {
    when (req["cmd"]?.jsonPrimitive?.contentOrNull) {
      "launch" -> {
        http = FixtureHttp()
        location = FakeLocation()
        favorites = MemoryFavorites()
        val setup = OpenMeteo(FixtureHttp()) // Build favorite cities from fixtures (independent of the http setting)
        favorites.items = req["favorites"]?.jsonArray?.map { setup.city(it.jsonPrimitive.intOrNull!!) } ?: emptyList()
        apply(req)
        val root = AppRoot(Deps(http, location, favorites), scope)
        this.root = root
        root.open(req["route"]?.jsonPrimitive?.contentOrNull ?: "/")
        root.idle()
      }
      "ls" -> {}
      "do" -> {
        val action = req["action"]?.jsonPrimitive?.contentOrNull ?: throw CoreError("Missing action")
        app().perform(action, req["input"]?.jsonObject ?: JsonObject(emptyMap()))
      }
      "env" -> apply(req)
      else -> throw CoreError("Unknown command: ${req["cmd"]}")
    }
    val root = app()
    // Same snapshot as the in-app server, plus a test-only env (what is observable from outside the app)
    return buildJsonObject {
      put("ok", true)
      root.snapshot().forEach { (k, v) -> put(k, v) }
      putJsonObject("env") {
        put("settingsOpened", location.settingsOpened)
        putJsonArray("favorites") { favorites.items.forEach { add(JsonPrimitive(it.id)) } }
        put("httpRequests", http.requests)
        put("networkViolations", BlockNetwork.attempts)
      }
    }
  }

  private fun app() = root ?: throw CoreError("Run launch first")
}

fun main(): Unit = runBlocking {
  ProxySelector.setDefault(BlockNetwork)
  val session = Session(this)
  while (true) {
    val line = readlnOrNull() ?: break
    val out = try {
      session.handle(CoreJson.parseToJsonElement(line).jsonObject)
    } catch (e: Exception) {
      buildJsonObject {
        put("ok", false)
        put("error", (e as? CoreError)?.message ?: e.toString())
      }
    }
    println(out)
    System.out.flush()
  }
  coroutineContext.cancelChildren()
}

private fun kotlin.coroutines.CoroutineContext.cancelChildren() = this[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
