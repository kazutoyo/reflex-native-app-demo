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
