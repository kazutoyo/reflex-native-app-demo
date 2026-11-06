import Foundation

/// A node in Reflex's Introspection. The CLI and the in-app server see only this shape.
@MainActor public protocol Node: AnyObject {
  var name: String { get }
  /// Everything the user can see goes into state
  var encodableState: any Encodable { get }
  /// Everything the user can do goes into actions
  var actions: [String] { get }
  /// Builds an Action from the action name and JSON input and sends it. Rejects input that does not match the type before sending
  func perform(_ action: String, input: Data) async throws
  /// Called by AppRoot when the node gains focus
  func appear() async
}

/// Elm-style ViewModel. Only update changes state, and only send is exposed.
@MainActor public protocol ViewModel: Node {
  associatedtype State: Encodable
  associatedtype Action: Decodable
  var state: State { get }
  func send(_ action: Action) async
}

extension ViewModel {
  public var encodableState: any Encodable { state }

  public func perform(_ action: String, input: Data) async throws {
    // Reshape into the Codable form Swift synthesizes for Action ({"open":{"id":1}}) and decode
    let key = action.prefix(1).lowercased() + action.dropFirst()
    let wrapped = "{\"\(key)\":".data(using: .utf8)! + input + "}".data(using: .utf8)!
    let decoded: Action
    do {
      decoded = try JSONDecoder().decode(Action.self, from: wrapped)
    } catch {
      throw CoreError("\(action) の入力が正しくない: \(String(data: input, encoding: .utf8) ?? "")")
    }
    await send(decoded)
  }
}

/// Result of ls. Both the CLI and the in-app server return this shape.
public struct Snapshot: Encodable {
  struct AnyState: Encodable {
    let value: any Encodable
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
  }

  public let path: String
  public let name: String
  let state: AnyState
  public let actions: [String]
}

extension AppRoot {
  public var snapshot: Snapshot {
    Snapshot(path: path, name: focused.name, state: .init(value: focused.encodableState), actions: actions)
  }
}
