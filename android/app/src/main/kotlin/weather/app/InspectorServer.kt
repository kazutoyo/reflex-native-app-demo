package weather.app

import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import weather.core.AppRoot
import weather.core.CoreError
import weather.core.CoreJson
import weather.core.snapshot

/**
 * Small server that runs only in development builds. Speaks the same NDJSON as the CLI (ls / do / open) over TCP and drives the same ViewModels.
 * From the Mac, connect via localhost:8765 after `adb forward tcp:8765 tcp:8765`.
 */
class InspectorServer(private val root: AppRoot, private val scope: CoroutineScope) {
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
      else -> throw CoreError("知らないコマンド: ${req["cmd"]}（アプリでは ls / do / open だけ）")
    }
    JsonObject(mapOf("ok" to JsonPrimitive(true)) + root.snapshot())
  } catch (e: Exception) {
    buildJsonObject {
      put("ok", JsonPrimitive(false))
      put("error", JsonPrimitive((e as? CoreError)?.message ?: e.toString()))
    }
  }
}
