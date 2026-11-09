import SwiftUI
import WeatherCore

@main
struct WeatherApp: App {
  #if DEBUG
    private static let http: HTTPClient = DebugHTTP.shared
  #else
    private static let http: HTTPClient = URLSessionHTTP()
  #endif
  @State private var root = AppRoot(
    deps: Deps(http: http, location: DeviceLocation(), favorites: DefaultsFavorites(),
      language: Bundle.main.preferredLocalizations.first ?? "en")) // Same language as the UI

  var body: some Scene {
    WindowGroup {
      RootView(root: root)
        .task {
          root.open("/")
          #if DEBUG
            InspectorServer.shared.start(root: root)
          #endif
        }
        .onOpenURL { url in root.open("/" + [url.host(), url.path()].compactMap { $0 }.joined()) }
    }
  }
}

/// The View's only contract: read state and call send(action:).
extension ViewModel {
  func tap(_ action: Action) { Task { await send(action) } }
}

struct RootView: View {
  let root: AppRoot

  private func path(_ tab: AppTab) -> Binding<[CityViewModel]> {
    Binding(get: { root.paths[tab] ?? [] }, set: { root.setPath($0, for: tab) })
  }

  var body: some View {
    TabView(selection: Binding(get: { root.tab }, set: { root.selectTab($0) })) {
      Tab("Weather", systemImage: "cloud.sun.fill", value: AppTab.weather) {
        NavigationStack { WeatherScreen(vm: root.weather) }
      }
      Tab("Favorites", systemImage: "star.fill", value: AppTab.favorites) {
        NavigationStack(path: path(.favorites)) {
          FavoritesScreen(vm: root.favorites)
            .navigationDestination(for: CityViewModel.self) { CityScreen(vm: $0) }
        }
      }
      Tab(value: AppTab.search, role: .search) {
        NavigationStack(path: path(.search)) {
          SearchScreen(vm: root.search)
            .navigationDestination(for: CityViewModel.self) { CityScreen(vm: $0) }
        }
      }
    }
  }
}
