import Foundation
import Observation

/// Favorite cities and the current weather for each.
@MainActor @Observable public final class FavoritesViewModel: ViewModel {
  public struct Item: Encodable, Equatable {
    public let city: City
    /// nil means loading or the fetch failed (failed)
    public var current: Current?
    public var failed = false

    public func encode(to encoder: Encoder) throws {
      var c = encoder.container(keyedBy: Key.self)
      try c.encode(city, forKey: "city")
      try c.encode(current, forKey: "current")
      try c.encode(failed, forKey: "failed")
    }
  }

  public struct State: Encodable, Equatable {
    public enum Status: String, Encodable { case idle, loaded }
    public var status = Status.idle
    public var items: [Item] = []
  }

  public enum Action: Decodable {
    case appear
    case open(id: Int)
  }

  enum Msg {
    case listLoaded([City])
    case currentLoaded(id: Int, Current?)
  }

  static func update(_ state: State, _ msg: Msg) -> State {
    switch msg {
    case .listLoaded(let cities):
      // Keep weather that was already fetched
      let known = Dictionary(state.items.map { ($0.city.id, $0) }, uniquingKeysWith: { a, _ in a })
      return State(status: .loaded, items: cities.map { known[$0.id] ?? Item(city: $0) })
    case .currentLoaded(let id, let current):
      var s = state
      s.items = s.items.map { $0.city.id == id ? Item(city: $0.city, current: current, failed: current == nil) : $0 }
      return s
    }
  }

  public let name = "Favorites"
  public let actions = ["Appear", "Open"]
  public private(set) var state = State()
  @ObservationIgnored private let deps: Deps
  @ObservationIgnored private unowned let nav: Navigator

  init(deps: Deps, nav: Navigator) {
    self.deps = deps
    self.nav = nav
  }

  private func dispatch(_ msg: Msg) { state = Self.update(state, msg) }

  public func send(_ action: Action) async {
    switch action {
    case .appear:
      // The user may change favorites in the detail screen and come back, so reload on every focus
      dispatch(.listLoaded(await deps.favorites.list()))
      for item in state.items where item.current == nil && !item.failed {
        let current = try? await deps.api.forecast(latitude: item.city.latitude, longitude: item.city.longitude).current
        dispatch(.currentLoaded(id: item.city.id, current))
      }
    case .open(let id):
      nav.push(.city(id: id))
    }
  }

  public func appear() async { await send(.appear) }
}
