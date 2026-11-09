#if DEBUG
  import Foundation
  @preconcurrency import Network
  import WeatherCore

  /// Wraps the real HTTP client so `env` can switch the device environment, like the CLI does.
  @MainActor final class DebugHTTP: HTTPClient {
    static let shared = DebugHTTP()
    private let base = URLSessionHTTP()
    var offline = false
    var weatherCode: Int?

    func get(_ url: String) async throws -> HTTPResponse {
      if offline { throw CoreError("Cannot connect to the network") }
      let res = try await base.get(url)
      guard let code = weatherCode, URLComponents(string: url)?.path == "/v1/forecast",
        var json = try? JSONSerialization.jsonObject(with: res.body) as? [String: Any],
        var current = json["current"] as? [String: Any], var daily = json["daily"] as? [String: Any],
        var codes = daily["weather_code"] as? [Int], !codes.isEmpty
      else { return res }
      // Override current weather and today's forecast (for rain, snow, and similar cases)
      current["weather_code"] = code
      codes[0] = code
      daily["weather_code"] = codes
      json["current"] = current
      json["daily"] = daily
      return HTTPResponse(status: res.status, body: try JSONSerialization.data(withJSONObject: json))
    }
  }

  /// Small server that runs only in development builds. Speaks the same NDJSON as the CLI (ls / do / open / env) over TCP and drives the same ViewModels.
  /// The simulator shares the host's network, so the Mac connects via localhost:8765.
  @MainActor final class InspectorServer {
    static let shared = InspectorServer()
    static let port: NWEndpoint.Port = 8765
    private var listener: NWListener?

    func start(root: AppRoot) {
      guard listener == nil, let listener = try? NWListener(using: .tcp, on: Self.port) else { return }
      listener.newConnectionHandler = { connection in
        Task { @MainActor in
          connection.start(queue: .main)
          self.receive(connection, root: root, buffer: Data())
        }
      }
      listener.start(queue: .main)
      self.listener = listener
    }

    private func receive(_ connection: NWConnection, root: AppRoot, buffer: Data) {
      connection.receive(minimumIncompleteLength: 1, maximumLength: 1 << 16) { data, _, done, error in
        Task { @MainActor in
          var buffer = buffer + (data ?? Data())
          while let i = buffer.firstIndex(of: 0x0A) {
            let line = Data(buffer[..<i])
            buffer = Data(buffer[(i + 1)...])
            let out = await self.handle(line, root: root)
            connection.send(content: out + Data("\n".utf8), completion: .contentProcessed { _ in })
          }
          if done || error != nil { connection.cancel() } else { self.receive(connection, root: root, buffer: buffer) }
        }
      }
    }

    private struct Response: Encodable {
      enum Keys: CodingKey { case ok }
      let snapshot: Snapshot
      func encode(to encoder: Encoder) throws {
        try snapshot.encode(to: encoder)
        var c = encoder.container(keyedBy: Keys.self)
        try c.encode(true, forKey: .ok)
      }
    }

    private func handle(_ line: Data, root: AppRoot) async -> Data {
      do {
        guard let req = try JSONSerialization.jsonObject(with: line) as? [String: Any] else {
          throw CoreError("Not a JSON object")
        }
        switch req["cmd"] as? String {
        case "ls": break
        case "do":
          guard let action = req["action"] as? String else { throw CoreError("Missing action") }
          try await root.perform(action, input: JSONSerialization.data(withJSONObject: req["input"] ?? [String: Any]()))
        case "open":
          root.open(req["route"] as? String ?? "/")
          await root.idle()
        case "env":
          switch req["http"] as? String {
          case "offline": DebugHTTP.shared.offline = true
          case "live": DebugHTTP.shared.offline = false
          case nil: break
          case let other?: throw CoreError("http must be offline or live: \(other)")
          }
          if req.keys.contains("weatherCode") { DebugHTTP.shared.weatherCode = req["weatherCode"] as? Int }
        default:
          throw CoreError("Unknown command: \(req["cmd"] ?? ""). The app supports only ls, do, open, and env")
        }
        return try JSONEncoder().encode(Response(snapshot: root.snapshot))
      } catch {
        let message = (error as? CoreError)?.message ?? "\(error)"
        return (try? JSONSerialization.data(withJSONObject: ["ok": false, "error": message])) ?? Data()
      }
    }
  }
#endif
