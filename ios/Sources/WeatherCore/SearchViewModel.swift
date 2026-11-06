import Foundation
import Observation

/// City search. Shows favorites when the query is empty.
@MainActor @Observable public final class SearchViewModel: ViewModel {
  public struct State: Encodable, Equatable {
    public enum Status: String, Encodable { case idle, loading, loaded, error }
    public var query = ""
    public var status = Status.idle
    public var results: [City] = []
    public var error: String?
    public var favorites: [City] = []

    public func encode(to encoder: Encoder) throws {
      var c = encoder.container(keyedBy: Key.self)
      try c.encode(query, forKey: "query")
      try c.encode(status, forKey: "status")
      try c.encode(results, forKey: "results")
      try c.encode(error, forKey: "error")
      try c.encode(favorites, forKey: "favorites")
    }
  }

  public enum Action: Decodable {
    case appear, submit
    case changeQuery(query: String)
    case open(id: Int)
  }

  enum Msg {
    case favoritesLoaded([City])
    case queryChanged(String)
    case searching
    case found([City])
    case failed(String)
  }

  static func update(_ state: State, _ msg: Msg) -> State {
    var s = state
    switch msg {
    case .favoritesLoaded(let favorites): s.favorites = favorites
    case .queryChanged(let query): s.query = query
    case .searching: s.status = .loading; s.error = nil
    case .found(let results): s.status = .loaded; s.results = results
    case .failed(let message): s.status = .error; s.error = message
    }
    return s
  }

  public let name = "Search"
  public let actions = ["Appear", "ChangeQuery", "Submit", "Open"]
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
      // Reload on every focus to reflect favorites changed in the detail screen
      dispatch(.favoritesLoaded(await deps.favorites.list()))
    case .changeQuery(let query):
      dispatch(.queryChanged(query))
    case .submit:
      let query = state.query.trimmingCharacters(in: .whitespaces)
      guard !query.isEmpty else { return }
      dispatch(.searching)
      do {
        dispatch(.found(try await deps.api.searchCities(query)))
      } catch {
        dispatch(.failed(errorMessage(error)))
      }
    case .open(let id):
      nav.push(.city(id: id))
    }
  }

  public func appear() async { await send(.appear) }
}
