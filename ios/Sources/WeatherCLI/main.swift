// Reflex-style CLI. Runs Core headless on macOS and speaks NDJSON over stdin/stdout.
//   → {"cmd":"launch","route":"/search","location":{"granted":false}}
//   → {"cmd":"ls"}
//   → {"cmd":"do","action":"Open","input":{"id":1850147}}
//   → {"cmd":"env","http":"offline"}
//   ← {"ok":true,"path":"/cities/1850147","name":"City","state":{...},"actions":[...],"env":{...}}
import Foundation
import WeatherCore

URLProtocol.registerClass(BlockNetwork.self)

/// Same Snapshot as the in-app server, plus a test-only env (what is observable from outside the app)
struct Response: Encodable {
  struct Env: Encodable {
    let settingsOpened: Int
    let favorites: [Int]
    let httpRequests: Int
    let networkViolations: Int
  }
  enum Keys: CodingKey { case ok, env }
  let snapshot: Snapshot
  let env: Env

  func encode(to encoder: Encoder) throws {
    try snapshot.encode(to: encoder)
    var c = encoder.container(keyedBy: Keys.self)
    try c.encode(true, forKey: .ok)
    try c.encode(env, forKey: .env)
  }
}

@MainActor final class Session {
  var http = FixtureHTTP()
  var location = FakeLocation()
  var favorites = MemoryFavorites()
  var root: AppRoot?

  /// Shared by launch and env: changes the device environment
  func apply(_ req: [String: Any]) {
    if let loc = req["location"] as? [String: Any] {
      if let granted = loc["granted"] as? Bool { location.permission.granted = granted }
      if let canAskAgain = loc["canAskAgain"] as? Bool { location.permission.canAskAgain = canAskAgain }
      if let error = loc["positionError"] as? String { location.positionError = error }
      if loc.keys.contains("place") { location.place = loc["place"] as? String }
      if let error = loc["placeError"] as? String { location.placeError = error }
    }
    switch req["http"] {
    case "fixtures" as String: http.mode = .fixtures
    case "offline" as String: http.mode = .offline
    case let status as [String: Any]: http.mode = .status(status["status"] as? Int ?? 500)
    default: break
    }
    if let code = req["weatherCode"] as? Int { http.weatherCode = code }
  }

  func handle(_ req: [String: Any]) async throws -> Response {
    switch req["cmd"] as? String {
    case "launch":
      http = FixtureHTTP()
      location = FakeLocation()
      favorites = MemoryFavorites()
      let setup = FixtureHTTP() // Build favorite cities from fixtures (independent of the http setting)
      for id in req["favorites"] as? [Int] ?? [] {
        favorites.items.append(try await Deps(http: setup, location: location, favorites: favorites).api.city(id))
      }
      apply(req)
      let root = AppRoot(deps: Deps(http: http, location: location, favorites: favorites))
      self.root = root
      root.open(req["route"] as? String ?? "/")
      await root.idle()
    case "ls":
      break
    case "do":
      guard let action = req["action"] as? String else { throw CoreError("Missing action") }
      let input = try JSONSerialization.data(withJSONObject: req["input"] ?? [String: Any]())
      try await app().perform(action, input: input)
    case "env":
      apply(req)
    default:
      throw CoreError("Unknown command: \(req["cmd"] ?? "")")
    }
    let root = try app()
    return Response(
      snapshot: root.snapshot,
      env: .init(settingsOpened: location.settingsOpened, favorites: favorites.items.map(\.id),
                 httpRequests: http.requests, networkViolations: BlockNetwork.attempts))
  }

  func app() throws -> AppRoot {
    guard let root else { throw CoreError("Run launch first") }
    return root
  }
}

let session = Session()
let encoder = JSONEncoder()
encoder.outputFormatting = .withoutEscapingSlashes

while let line = readLine() {
  let out: Data
  do {
    guard let req = try JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any] else {
      throw CoreError("Not a JSON object")
    }
    out = try encoder.encode(try await session.handle(req))
  } catch {
    out = try! JSONSerialization.data(withJSONObject: ["ok": false, "error": (error as? CoreError)?.message ?? "\(error)"])
  }
  FileHandle.standardOutput.write(out + Data("\n".utf8))
}
