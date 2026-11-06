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
        if place != nil { Text("現在地").font(.subheadline.weight(.medium)).foregroundStyle(.white.opacity(0.75)) }
        ForecastCard(forecast: forecast)
      case .permissionDenied(let canAskAgain):
        MessageCard(icon: "location.slash.fill", title: "位置情報が許可されていません",
                    message: "現在地の天気を表示するには、位置情報の利用を許可してください。") {
          if canAskAgain {
            Button("もう一度許可する") { vm.tap(.retry) }.buttonStyle(.glassProminent)
          } else {
            Button("設定を開く") { vm.tap(.openSettings) }.buttonStyle(.glassProminent)
          }
          Button("都市を検索する") { vm.tap(.openSearch) }.buttonStyle(.glass)
        }
      case .error(let message):
        MessageCard(icon: "exclamationmark.icloud.fill", title: "天気を取得できませんでした", message: message) {
          Button("再試行") { vm.tap(.retry) }.buttonStyle(.glassProminent)
        }
      }
    }
    .navigationTitle(loaded?.place ?? "現在地")
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
          HStack { Text("検索結果"); if s.status == .loading { ProgressView() } }
        }
      }
    }
    .overlay {
      if s.status == .error {
        ContentUnavailableView("検索できませんでした", systemImage: "wifi.exclamationmark", description: Text(s.error ?? ""))
      } else if searched, s.status == .loaded, s.results.isEmpty {
        ContentUnavailableView.search(text: s.query)
      } else if !searched {
        ContentUnavailableView("都市を検索", systemImage: "magnifyingglass", description: Text("都市名で検索して、天気を見られます"))
      }
    }
    .navigationTitle("検索")
    .searchable(text: $query, prompt: "都市名（例：Tokyo）")
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
        MessageCard(icon: "exclamationmark.icloud.fill", title: "天気を取得できませんでした", message: message) {
          Button("再試行") { vm.tap(.retry) }.buttonStyle(.glassProminent)
        }
      }
    }
    .navigationTitle(loaded?.city.name ?? "")
    .toolbar {
      if let loaded {
        Button("お気に入り", systemImage: loaded.favorite ? "star.fill" : "star") { vm.tap(.toggleFavorite) }
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
        ContentUnavailableView("お気に入りはまだありません", systemImage: "star",
                               description: Text("都市の詳細で ☆ を押すと、ここに並びます"))
      }
    }
    .navigationTitle("お気に入り")
  }
}
