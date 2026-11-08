package weather.app

import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import weather.core.AppRoot
import weather.core.CoreError
import weather.core.CoreJson
import weather.core.HttpClient
import weather.core.HttpResponse
import weather.core.snapshot

/** Wraps the real HTTP client so `env` can switch the device environment, like the CLI does. */
class DebugHttp(private val base: HttpClient) : HttpClient {
  var offline = false
  var weatherCode: Int? = null

  override suspend fun get(url: String): HttpResponse {
    if (offline) throw CoreError("ネットワークに接続できません")
    val res = base.get(url)
    val code = weatherCode ?: return res
    if (URI(url).path != "/v1/forecast" || res.status >= 400) return res
    // Override current weather and today's forecast (for rain, snow, and similar cases)
    val json = CoreJson.parseToJsonElement(res.body).jsonObject
    val current = json["current"]!!.jsonObject
    val daily = json["daily"]!!.jsonObject
    val codes = daily["weather_code"]!!.jsonArray
    return HttpResponse(
      res.status,
      JsonObject(
        json + mapOf(
          "current" to JsonObject(current + ("weather_code" to JsonPrimitive(code))),
          "daily" to JsonObject(daily + ("weather_code" to JsonArray(listOf(JsonPrimitive(code)) + codes.drop(1)))),
        )
      ).toString(),
    )
  }
}

/**
 * Small server that runs only in development builds. Speaks the same NDJSON as the CLI (ls / do / open / env) over TCP and drives the same ViewModels.
 * From the Mac, connect via localhost:8765 after `adb forward tcp:8765 tcp:8765`.
 */
class InspectorServer(private val root: AppRoot, private val http: DebugHttp, private val scope: CoroutineScope) {
  fun start() {
    scope.launch(Dispatchers.IO) {
      val server = ServerSocket(8765, 1, InetAddress.getLoopbackAddress())
      while (true) {
        val socket = server.accept()
        launch(Dispatchers.IO) {
          socket.use {
            val writer = it.getOutputStream().bufferedWriter()
            it.getInputStream().bufferedReader().lineSequence().forEach { line ->
              val out = withContext(Dispatchers.Main) { handle(line) }
              writer.write(out.toString() + "\n")
              writer.flush()
            }
          }
        }
      }
    }
  }

  private suspend fun handle(line: String): JsonObject = try {
    val req = CoreJson.parseToJsonElement(line).jsonObject
    when (req["cmd"]?.jsonPrimitive?.contentOrNull) {
      "ls" -> {}
      "do" -> root.perform(
        req["action"]?.jsonPrimitive?.contentOrNull ?: throw CoreError("action がない"),
        req["input"]?.jsonObject ?: JsonObject(emptyMap()),
      )
      "open" -> {
        root.open(req["route"]?.jsonPrimitive?.contentOrNull ?: "/")
        root.idle()
      }
      "env" -> {
        when (val h = req["http"]?.jsonPrimitive?.contentOrNull) {
          "offline" -> http.offline = true
          "live" -> http.offline = false
          null -> {}
          else -> throw CoreError("http は offline か live: $h")
        }
        if ("weatherCode" in req) http.weatherCode = req["weatherCode"]?.jsonPrimitive?.intOrNull
      }
      else -> throw CoreError("知らないコマンド: ${req["cmd"]}（アプリでは ls / do / open / env だけ）")
    }
    JsonObject(mapOf("ok" to JsonPrimitive(true)) + root.snapshot())
  } catch (e: Exception) {
    buildJsonObject {
      put("ok", JsonPrimitive(false))
      put("error", JsonPrimitive((e as? CoreError)?.message ?: e.toString()))
    }
  }
}
