import Foundation
import WeatherCore

/// Returns fixtures (saved real Open-Meteo responses). Unknown URLs are errors; never hits the network.
@MainActor final class FixtureHTTP: HTTPClient {
  enum Mode { case fixtures, offline, status(Int) }
  var mode = Mode.fixtures
  var weatherCode: Int?
  var requests = 0
  let dir = URL(fileURLWithPath: ProcessInfo.processInfo.environment["FIXTURES"] ?? "fixtures")

  private func file(_ name: String) throws -> Data { try Data(contentsOf: dir.appendingPathComponent(name)) }

  func get(_ url: String) async throws -> HTTPResponse {
    requests += 1
    switch mode {
    case .offline: throw CoreError("Cannot connect to the network")
    case .status(let status): return HTTPResponse(status: status, body: Data("{}".utf8))
    case .fixtures: return HTTPResponse(status: 200, body: try fixture(url))
    }
  }

  func fixture(_ url: String) throws -> Data {
    let components = URLComponents(string: url)
    let query = Dictionary((components?.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { a, _ in a })
    switch components?.path {
    case "/v1/forecast":
      let body = try file("forecast-tokyo.json")
      guard let code = weatherCode else { return body }
      // Override current weather and today's forecast (for rain, snow, and similar cases)
      var json = try JSONSerialization.jsonObject(with: body) as! [String: Any]
      var current = json["current"] as! [String: Any]
      var daily = json["daily"] as! [String: Any]
      var codes = daily["weather_code"] as! [Int]
      current["weather_code"] = code
      codes[0] = code
      daily["weather_code"] = codes
      json["current"] = current
      json["daily"] = daily
      return try JSONSerialization.data(withJSONObject: json)
    case "/v1/search":
      return try file(query["name"] == "Tokyo" ? "search-tokyo.json" : "search-empty.json")
    case "/v1/get":
      let city = try file("city-1850147.json")
      let results = (try JSONSerialization.jsonObject(with: file("search-tokyo.json")) as! [String: Any])["results"] as! [[String: Any]]
      if query["id"] == "1850147" { return city }
      if let hit = results.first(where: { "\($0["id"]!)" == query["id"] }) {
        return try JSONSerialization.data(withJSONObject: hit)
      }
    default: break
    }
    throw CoreError("No fixture for \(url)")
  }
}

/// Stands in for CoreLocation. Mutate fields to simulate things like "the user changed a setting".
@MainActor final class FakeLocation: LocationService {
  var permission = Permission(granted: true, canAskAgain: true)
  var positionError: String?
  var place: String? = "新宿区"
  var placeError: String?
  var settingsOpened = 0

  func requestPermission() async -> Permission { permission }

  func currentPosition() async throws -> Coords {
    if let positionError { throw CoreError(positionError) }
    return Coords(latitude: 35.6895, longitude: 139.69171)
  }

  func placeName(_ coords: Coords) async throws -> String? {
    if let placeError { throw CoreError(placeError) }
    return place
  }

  func openSettings() { settingsOpened += 1 }
}

/// Stands in for UserDefaults.
@MainActor final class MemoryFavorites: FavoritesStore {
  var items: [City] = []
  func list() async -> [City] { items }
  func save(_ cities: [City]) async { items = cities }
}

/// Blocks all outbound HTTP from the process and counts attempts (detected even if the app swallows the error).
final class BlockNetwork: URLProtocol, @unchecked Sendable {
  nonisolated(unsafe) static var attempts = 0
  override class func canInit(with request: URLRequest) -> Bool {
    attempts += 1
    return true
  }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
  }
  override func stopLoading() {}
}
