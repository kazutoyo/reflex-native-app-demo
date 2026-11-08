package weather.core

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class City(val id: Int, val name: String, val region: String, val latitude: Double, val longitude: Double)

@Serializable
data class Current(val temperature: Double, val code: Int)

@Serializable
data class Day(val date: String, val code: Int, val max: Double, val min: Double)

@Serializable
data class Forecast(val current: Current, val daily: List<Day>)

data class Coords(val latitude: Double, val longitude: Double)

/** Error shown to the user. message goes into state as is. */
class CoreError(message: String) : Exception(message)

/** Rethrows cancellation so a cancelled load is not shown as an error. */
internal fun errorMessage(e: Throwable): String {
  if (e is CancellationException) throw e
  return e.message ?: e.toString()
}

/** JSON for state and actions. Writes nulls too (to match the Swift version). */
val CoreJson = Json {
  explicitNulls = true
  encodeDefaults = true
  ignoreUnknownKeys = true
}

/** WMO weather code -> display values. */
fun describeWeather(code: Int): String = when {
  code == 0 -> "快晴"
  code <= 2 -> "晴れ"
  code == 3 -> "くもり"
  code <= 48 -> "霧"
  code <= 57 -> "霧雨"
  code <= 67 -> "雨"
  code <= 77 -> "雪"
  code <= 82 -> "にわか雨"
  code <= 86 -> "にわか雪"
  else -> "雷雨"
}
