import Foundation
import Observation

/// City details (weekly forecast and favorite). Back is lent by the stack, so it is not defined here.
@MainActor @Observable public final class CityViewModel: ViewModel, Hashable {
  public enum State: Encodable, Equatable {
    case idle
    case loading
    case loaded(city: City, forecast: Forecast, favorite: Bool)
    case error(message: String)

    public func encode(to encoder: Encoder) throws {
      var c = encoder.container(keyedBy: Key.self)
      switch self {
      case .idle: try c.encode("idle", forKey: "status")
      case .loading: try c.encode("loading", forKey: "status")
      case .loaded(let city, let forecast, let favorite):
        try c.encode("loaded", forKey: "status")
        try c.encode(city, forKey: "city")
        try c.encode(forecast, forKey: "forecast")
        try c.encode(favorite, forKey: "favorite")
      case .error(let message):
        try c.encode("error", forKey: "status")
        try c.encode(message, forKey: "message")
      }
    }
  }

  public enum Action: Decodable { case appear, retry, toggleFavorite }

  enum Msg {
    case loading
    case loaded(City, Forecast, favorite: Bool)
    case failed(String)
    case favoriteChanged(Bool)
  }

  static func update(_ state: State, _ msg: Msg) -> State {
    switch msg {
    case .loading: return .loading
    case .loaded(let city, let forecast, let favorite): return .loaded(city: city, forecast: forecast, favorite: favorite)
    case .failed(let message): return .error(message: message)
    case .favoriteChanged(let favorite):
      guard case .loaded(let city, let forecast, _) = state else { return state }
      return .loaded(city: city, forecast: forecast, favorite: favorite)
    }
  }

  public let name = "City"
  public let actions = ["Appear", "Retry", "ToggleFavorite"]
  public let id: Int
  public private(set) var state: State = .idle
  @ObservationIgnored private let deps: Deps

  init(id: Int, deps: Deps) {
    self.id = id
    self.deps = deps
  }

  private func dispatch(_ msg: Msg) { state = Self.update(state, msg) }

  private func load() async {
    dispatch(.loading)
    do {
      let city = try await deps.api.city(id)
      let forecast = try await deps.api.forecast(latitude: city.latitude, longitude: city.longitude)
      let favorites = await deps.favorites.list()
      dispatch(.loaded(city, forecast, favorite: favorites.contains { $0.id == id }))
    } catch {
      dispatch(.failed(errorMessage(error)))
    }
  }

  public func send(_ action: Action) async {
    switch action {
    case .appear:
      if state == .idle { await load() }
    case .retry:
      await load()
    case .toggleFavorite:
      guard case .loaded(let city, _, let favorite) = state else { return }
      let others = await deps.favorites.list().filter { $0.id != id }
      await deps.favorites.save(favorite ? others : others + [city])
      dispatch(.favoriteChanged(!favorite))
    }
  }

  public func appear() async { await send(.appear) }

  nonisolated public static func == (a: CityViewModel, b: CityViewModel) -> Bool { a === b }
  nonisolated public func hash(into hasher: inout Hasher) { hasher.combine(ObjectIdentifier(self)) }
}
