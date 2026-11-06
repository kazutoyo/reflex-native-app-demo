import Foundation
import Observation

/// App root. Owns the tab navigator and a stack per tab.
/// Navigation is also a state change, so the whole app can be walked without creating Views.
@MainActor @Observable public final class AppRoot: Navigator {
  public private(set) var tab = AppTab.weather
  /// Root screen of each tab. Kept when switching tabs
  public private(set) var weather: WeatherViewModel!
  public private(set) var search: SearchViewModel!
  public private(set) var favorites: FavoritesViewModel!
  /// Stack per tab (screens pushed on top of the root)
  public private(set) var paths: [AppTab: [CityViewModel]] = [.search: [], .favorites: []]

  @ObservationIgnored private let deps: Deps
  @ObservationIgnored private var focusedID: ObjectIdentifier?
  @ObservationIgnored private var pending: Task<Void, Never>?

  public init(deps: Deps) {
    self.deps = deps
    weather = WeatherViewModel(deps: deps, nav: self)
    search = SearchViewModel(deps: deps, nav: self)
    favorites = FavoritesViewModel(deps: deps, nav: self)
  }

  // MARK: - Focus

  /// Top of the selected tab's stack. The "deepest node" in Reflex.
  public var focused: Node {
    switch tab {
    case .weather: weather
    case .search: paths[.search]!.last ?? search
    case .favorites: paths[.favorites]!.last ?? favorites
    }
  }

  public var path: String {
    switch tab {
    case .weather: "/"
    case .search: paths[.search]!.last.map { "/cities/\($0.id)" } ?? "/search"
    case .favorites: paths[.favorites]!.last.map { "/favorites/\($0.id)" } ?? "/favorites"
    }
  }

  public var canGoBack: Bool { !(paths[tab]?.isEmpty ?? true) }

  /// Sends Appear when focus moves to another screen (same rule in the app and the CLI)
  private func focusChanged() {
    let node = focused
    let id = ObjectIdentifier(node)
    guard id != focusedID else { return }
    focusedID = id
    let previous = pending
    pending = Task {
      await previous?.value
      await node.appear()
    }
  }

  /// Waits until the Appear started by a navigation finishes
  public func idle() async {
    while let task = pending {
      await task.value
      if pending == task { pending = nil }
    }
  }

  // MARK: - Navigator

  public func push(_ route: Route) {
    guard case .city(let id) = route, tab != .weather else { return }
    paths[tab]!.append(CityViewModel(id: id, deps: deps))
    focusChanged()
  }

  public func selectTab(_ tab: AppTab) {
    self.tab = tab
    focusChanged()
  }

  public func back() {
    guard canGoBack else { return }
    paths[tab]!.removeLast()
    focusChanged()
  }

  /// Rewrites the stack from the View side, e.g. when SwiftUI's NavigationStack pops via swipe
  public func setPath(_ path: [CityViewModel], for tab: AppTab) {
    paths[tab] = path
    focusChanged()
  }

  /// Deep link
  public func open(_ url: String) {
    let parts = url.split(separator: "/").map(String.init)
    switch (parts.first, parts.count > 1 ? Int(parts[1]) : nil) {
    case ("search", _): tab = .search; paths[.search] = []
    case ("cities", let id?): tab = .search; paths[.search] = [CityViewModel(id: id, deps: deps)]
    case ("favorites", nil): tab = .favorites; paths[.favorites] = []
    case ("favorites", let id?): tab = .favorites; paths[.favorites] = [CityViewModel(id: id, deps: deps)]
    default: tab = .weather
    }
    focusChanged()
  }

  // MARK: - Introspection

  /// Actions of the current screen plus those lent by the navigator (Back, SelectTab)
  public var actions: [String] { focused.actions + (canGoBack ? ["Back"] : []) + ["SelectTab"] }

  /// Sends by action name. Rejects actions that neither the current screen nor its parents have
  public func perform(_ action: String, input: Data) async throws {
    switch action {
    case "Back" where canGoBack:
      back()
    case "SelectTab":
      struct Input: Decodable { let tab: AppTab }
      guard let input = try? JSONDecoder().decode(Input.self, from: input) else {
        throw CoreError("SelectTab の入力が正しくない: \(String(data: input, encoding: .utf8) ?? "")")
      }
      selectTab(input.tab)
    case _ where focused.actions.contains(action):
      try await focused.perform(action, input: input)
    default:
      throw CoreError("\(focused.name) に \(action) はない: \(actions.joined(separator: ", "))")
    }
    await idle()
  }
}
