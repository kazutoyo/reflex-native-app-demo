package weather.core

import java.net.URLEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Open-Meteo (no API key needed). Networking is delegated to the injected HttpClient. */
class OpenMeteo(private val http: HttpClient) {
  private suspend fun get(url: String): JsonObject {
    val res = http.get(url)
    if (res.status !in 200..299) throw CoreError("HTTP ${res.status}")
    return CoreJson.parseToJsonElement(res.body).jsonObject
  }

  private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

  private fun city(r: JsonObject) = City(
    id = r["id"]!!.jsonPrimitive.int,
    name = r.str("name")!!,
    region = listOfNotNull(r.str("admin1"), r.str("country")).filter { it.isNotEmpty() }.distinct().joinToString(", "),
    latitude = r["latitude"]!!.jsonPrimitive.double,
    longitude = r["longitude"]!!.jsonPrimitive.double,
  )

  suspend fun forecast(latitude: Double, longitude: Double): Forecast {
    val r = get(
      "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
        "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
    )
    val current = r["current"]!!.jsonObject
    val daily = r["daily"]!!.jsonObject
    fun col(key: String) = daily[key]!!.jsonArray
    return Forecast(
      current = Current(current["temperature_2m"]!!.jsonPrimitive.double, current["weather_code"]!!.jsonPrimitive.int),
      daily = col("time").indices.map { i ->
        Day(
          date = col("time")[i].jsonPrimitive.content,
          code = col("weather_code")[i].jsonPrimitive.int,
          max = col("temperature_2m_max")[i].jsonPrimitive.double,
          min = col("temperature_2m_min")[i].jsonPrimitive.double,
        )
      },
    )
  }

  suspend fun searchCities(query: String): List<City> {
    val name = URLEncoder.encode(query, Charsets.UTF_8)
    val r = get("https://geocoding-api.open-meteo.com/v1/search?name=$name&count=10&language=ja")
    // results is absent when there are no matches
    return r["results"]?.jsonArray?.map { city(it.jsonObject) } ?: emptyList()
  }

  suspend fun city(id: Int): City = city(get("https://geocoding-api.open-meteo.com/v1/get?id=$id&language=ja"))
}
