import Foundation

/// Open-Meteo (no API key needed). Networking is delegated to the injected HTTPClient.
@MainActor public struct OpenMeteo {
  let http: HTTPClient

  private func get<T: Decodable>(_ url: String, as: T.Type) async throws -> T {
    let res = try await http.get(url)
    guard (200..<300).contains(res.status) else { throw CoreError("HTTP \(res.status)") }
    return try JSONDecoder().decode(T.self, from: res.body)
  }

  private struct ForecastResponse: Decodable {
    struct Current: Decodable { let temperature_2m: Double; let weather_code: Int }
    struct Daily: Decodable {
      let time: [String]
      let weather_code: [Int]
      let temperature_2m_max: [Double]
      let temperature_2m_min: [Double]
    }
    let current: Current
    let daily: Daily
  }

  struct Place: Decodable {
    let id: Int
    let name: String
    let latitude: Double
    let longitude: Double
    let admin1: String?
    let country: String?

    var city: City {
      var region: [String] = []
      for s in [admin1, country] { if let s, !s.isEmpty, !region.contains(s) { region.append(s) } }
      return City(id: id, name: name, region: region.joined(separator: ", "), latitude: latitude, longitude: longitude)
    }
  }

  private struct SearchResponse: Decodable { let results: [Place]? } // results is absent when there are no matches

  public func forecast(latitude: Double, longitude: Double) async throws -> Forecast {
    let r = try await get(
      "https://api.open-meteo.com/v1/forecast?latitude=\(latitude)&longitude=\(longitude)"
        + "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto",
      as: ForecastResponse.self)
    let daily = r.daily.time.indices.map {
      Day(date: r.daily.time[$0], code: r.daily.weather_code[$0],
          max: r.daily.temperature_2m_max[$0], min: r.daily.temperature_2m_min[$0])
    }
    return Forecast(current: Current(temperature: r.current.temperature_2m, code: r.current.weather_code), daily: daily)
  }

  public func searchCities(_ query: String) async throws -> [City] {
    let name = query.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? query
    let r = try await get(
      "https://geocoding-api.open-meteo.com/v1/search?name=\(name)&count=10&language=ja", as: SearchResponse.self)
    return (r.results ?? []).map(\.city)
  }

  public func city(_ id: Int) async throws -> City {
    try await get("https://geocoding-api.open-meteo.com/v1/get?id=\(id)&language=ja", as: Place.self).city
  }
}
