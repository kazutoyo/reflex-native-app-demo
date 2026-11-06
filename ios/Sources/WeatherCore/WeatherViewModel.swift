import Foundation
import Observation

/// Weather at the current location. Location permission -> current location -> weather API.
@MainActor @Observable public final class WeatherViewModel: ViewModel {
  public enum State: Encodable, Equatable {
    case idle
    case loading
    case permissionDenied(canAskAgain: Bool)
    case loaded(forecast: Forecast, place: String?)
    case error(message: String)

    public func encode(to encoder: Encoder) throws {
      var c = encoder.container(keyedBy: Key.self)
      switch self {
      case .idle: try c.encode("idle", forKey: "status")
      case .loading: try c.encode("loading", forKey: "status")
      case .permissionDenied(let canAskAgain):
        try c.encode("permissionDenied", forKey: "status")
        try c.encode(canAskAgain, forKey: "canAskAgain")
      case .loaded(let forecast, let place):
        try c.encode("loaded", forKey: "status")
        try c.encode(forecast, forKey: "forecast")
        try c.encode(place, forKey: "place")
      case .error(let message):
        try c.encode("error", forKey: "status")
        try c.encode(message, forKey: "message")
      }
    }
  }

  public enum Action: Decodable { case appear, retry, openSettings, openSearch }

  enum Msg {
    case loading
    case denied(canAskAgain: Bool)
    case loaded(Forecast, place: String?)
    case failed(String)
  }

  static func update(_ state: State, _ msg: Msg) -> State {
    switch msg {
    case .loading: .loading
    case .denied(let canAskAgain): .permissionDenied(canAskAgain: canAskAgain)
    case .loaded(let forecast, let place): .loaded(forecast: forecast, place: place)
    case .failed(let message): .error(message: message)
    }
  }

  public let name = "Weather"
  public let actions = ["Appear", "Retry", "OpenSettings", "OpenSearch"]
  public private(set) var state: State = .idle
  @ObservationIgnored private let deps: Deps
  @ObservationIgnored private unowned let nav: Navigator

  init(deps: Deps, nav: Navigator) {
    self.deps = deps
    self.nav = nav
  }

  private func dispatch(_ msg: Msg) { state = Self.update(state, msg) }

  private func load() async {
    dispatch(.loading)
    let permission = await deps.location.requestPermission()
    guard permission.granted else { return dispatch(.denied(canAskAgain: permission.canAskAgain)) }
    do {
      let coords = try await deps.location.currentPosition()
      let forecast = try await deps.api.forecast(latitude: coords.latitude, longitude: coords.longitude)
      let place = try? await deps.location.placeName(coords) // Show the weather even if the place name is unavailable
      dispatch(.loaded(forecast, place: place ?? nil))
    } catch {
      dispatch(.failed(errorMessage(error)))
    }
  }

  public func send(_ action: Action) async {
    switch action {
    case .appear:
      // Once loaded, do not refetch every time the user switches tabs.
      // If permission was denied, reload: the user may have granted it in Settings and come back
      switch state {
      case .idle, .permissionDenied: await load()
      default: break
      }
    case .retry: await load()
    case .openSettings: deps.location.openSettings()
    case .openSearch: nav.selectTab(.search)
    }
  }

  public func appear() async { await send(.appear) }
}
