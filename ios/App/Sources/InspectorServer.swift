#if DEBUG
  import Foundation
  @preconcurrency import Network
  import WeatherCore

  /// Small server that runs only in development builds. Speaks the same NDJSON as the CLI (ls / do / open) over TCP and drives the same ViewModels.
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
          throw CoreError("JSON のオブジェクトではない")
        }
        switch req["cmd"] as? String {
        case "ls": break
        case "do":
          guard let action = req["action"] as? String else { throw CoreError("action がない") }
          try await root.perform(action, input: JSONSerialization.data(withJSONObject: req["input"] ?? [String: Any]()))
        case "open":
          root.open(req["route"] as? String ?? "/")
          await root.idle()
        default:
          throw CoreError("知らないコマンド: \(req["cmd"] ?? "")（アプリでは ls / do / open だけ）")
        }
        return try JSONEncoder().encode(Response(snapshot: root.snapshot))
      } catch {
        let message = (error as? CoreError)?.message ?? "\(error)"
        return (try? JSONSerialization.data(withJSONObject: ["ok": false, "error": message])) ?? Data()
      }
    }
  }
#endif
