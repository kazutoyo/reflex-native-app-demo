import SwiftUI
import WeatherCore

// Screens are presentation only. All logic lives in the WeatherCore ViewModels.

struct WeatherScreen: View {
  let vm: WeatherViewModel

  var body: some View {
    let loaded: (forecast: Forecast, place: String?)? = if case .loaded(let f, let p) = vm.state { (f, p) } else { nil }
    SkyScroll(code: loaded?.forecast.current.code) {
      switch vm.state {
      case .idle, .loading:
        ProgressView().tint(.white).controlSize(.large).padding(.top, 120)
      case .loaded(let forecast, let place):
        if place != nil { Text("My Location").font(.subheadline.weight(.medium)).foregroundStyle(.white.opacity(0.75)) }
        ForecastCard(forecast: forecast)
      case .permissionDenied(let canAskAgain):
        MessageCard(icon: "location.slash.fill", title: "Location Access Is Off",
                    message: String(localized: "Allow location access to see the weather where you are.")) {
          if canAskAgain {
            Button("Allow Again") { vm.tap(.retry) }.buttonStyle(.glassProminent)
          } else {
            Button("Open Settings") { vm.tap(.openSettings) }.buttonStyle(.glassProminent)
          }
          Button("Search for a City") { vm.tap(.openSearch) }.buttonStyle(.glass)
        }
      case .error(let message):
        MessageCard(icon: "exclamationmark.icloud.fill", title: "Couldn't Load the Weather", message: message) {
          Button("Try Again") { vm.tap(.retry) }.buttonStyle(.glassProminent)
        }
      }
    }
    .navigationTitle(loaded?.place ?? String(localized: "My Location"))
  }
}

struct SearchScreen: View {
  let vm: SearchViewModel
  // The View owns the text being typed. Round-tripping through the ViewModel reorders characters when typing fast
  @State private var query = ""

  var body: some View {
    let s = vm.state
    let searched = s.status != .idle && !s.query.trimmingCharacters(in: .whitespaces).isEmpty
    List {
      if searched {
        Section {
          ForEach(s.results, id: \.id) { city in
            CityRow(city: city, favorite: s.favorites.contains { $0.id == city.id }) { vm.tap(.open(id: city.id)) }
          }
        } header: {
          HStack { Text("Results"); if s.status == .loading { ProgressView() } }
        }
      }
    }
    .overlay {
      if s.status == .error {
        ContentUnavailableView("Search Failed", systemImage: "wifi.exclamationmark", description: Text(s.error ?? ""))
      } else if searched, s.status == .loaded, s.results.isEmpty {
        ContentUnavailableView.search(text: s.query)
      } else if !searched {
        ContentUnavailableView("Search for a City", systemImage: "magnifyingglass", description: Text("Search by city name to see its weather."))
      }
    }
    .navigationTitle("Search")
    .searchable(text: $query, prompt: "City name (e.g. Tokyo)")
    .onChange(of: query) { vm.tap(.changeQuery(query: query)) }
    .onSubmit(of: .search) { vm.tap(.submit) }
  }
}

struct CityScreen: View {
  let vm: CityViewModel

  var body: some View {
    let loaded: (city: City, forecast: Forecast, favorite: Bool)? =
      if case .loaded(let c, let f, let fav) = vm.state { (c, f, fav) } else { nil }
    SkyScroll(code: loaded?.forecast.current.code) {
      switch vm.state {
      case .idle, .loading:
        ProgressView().tint(.white).controlSize(.large).padding(.top, 120)
      case .loaded(let city, let forecast, _):
        Text(city.region).font(.subheadline.weight(.medium)).foregroundStyle(.white.opacity(0.75))
        ForecastCard(forecast: forecast)
      case .error(let message):
        MessageCard(icon: "exclamationmark.icloud.fill", title: "Couldn't Load the Weather", message: message) {
          Button("Try Again") { vm.tap(.retry) }.buttonStyle(.glassProminent)
        }
      }
    }
    .navigationTitle(loaded?.city.name ?? "")
    .toolbar {
      if let loaded {
        Button("Favorite", systemImage: loaded.favorite ? "star.fill" : "star") { vm.tap(.toggleFavorite) }
          .tint(loaded.favorite ? .yellow : nil)
      }
    }
  }
}

struct FavoritesScreen: View {
  let vm: FavoritesViewModel

  var body: some View {
    ScrollView {
      LazyVStack(spacing: 12) {
        ForEach(vm.state.items, id: \.city.id) { item in
          Button { vm.tap(.open(id: item.city.id)) } label: { FavoriteCard(item: item) }.buttonStyle(.plain)
        }
      }
      .padding()
    }
    .overlay {
      if vm.state.status == .loaded, vm.state.items.isEmpty {
        ContentUnavailableView("No Favorites Yet", systemImage: "star",
                               description: Text("Tap ☆ on a city to add it here."))
      }
    }
    .navigationTitle("Favorites")
  }
}
