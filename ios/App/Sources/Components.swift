import SwiftUI
import WeatherCore

/// Sky gradient matching the weather code.
func skyColors(_ code: Int?) -> [Color] {
  switch code ?? 0 {
  case 0...2: [Color(red: 0.16, green: 0.45, blue: 0.85), Color(red: 0.42, green: 0.72, blue: 0.96)]
  case 3...48: [Color(red: 0.35, green: 0.45, blue: 0.58), Color(red: 0.6, green: 0.68, blue: 0.78)]
  case 49...67, 80...82: [Color(red: 0.2, green: 0.27, blue: 0.38), Color(red: 0.42, green: 0.5, blue: 0.6)]
  case 68...77, 83...86: [Color(red: 0.38, green: 0.47, blue: 0.6), Color(red: 0.68, green: 0.75, blue: 0.85)]
  default: [Color(red: 0.13, green: 0.14, blue: 0.24), Color(red: 0.32, green: 0.33, blue: 0.48)]
  }
}

/// WMO weather code -> label.
func describeWeather(_ code: Int) -> LocalizedStringKey {
  switch code {
  case 0: "Clear"
  case 1...2: "Sunny"
  case 3: "Cloudy"
  case ...48: "Fog"
  case ...57: "Drizzle"
  case ...67: "Rain"
  case ...77: "Snow"
  case ...82: "Showers"
  case ...86: "Snow Showers"
  default: "Thunderstorm"
  }
}

func symbol(_ code: Int) -> String {
  switch code {
  case 0: "sun.max.fill"
  case 1...2: "cloud.sun.fill"
  case 3: "cloud.fill"
  case ...48: "cloud.fog.fill"
  case ...67, 80...82: "cloud.rain.fill"
  case ...77, 83...86: "cloud.snow.fill"
  default: "cloud.bolt.rain.fill"
  }
}

struct SkyScroll<Content: View>: View {
  let code: Int?
  @ViewBuilder let content: Content

  var body: some View {
    ScrollView {
      VStack(spacing: 20) { content }.padding().frame(maxWidth: .infinity)
    }
    .background(LinearGradient(colors: skyColors(code), startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    .toolbarColorScheme(.dark, for: .navigationBar)
    .environment(\.colorScheme, .dark)
  }
}

struct ForecastCard: View {
  let forecast: Forecast

  var body: some View {
    VStack(spacing: 4) {
      Image(systemName: symbol(forecast.current.code)).symbolRenderingMode(.multicolor).font(.system(size: 56))
      Text("\(Int(forecast.current.temperature.rounded()))°").font(.system(size: 88, weight: .thin))
      Text(describeWeather(forecast.current.code)).font(.title3.weight(.semibold))
      if let today = forecast.daily.first {
        Text("H:\(Int(today.max.rounded()))° L:\(Int(today.min.rounded()))°").font(.subheadline)
      }
    }
    .foregroundStyle(.white)

    VStack(alignment: .leading, spacing: 0) {
      Text("7-Day Forecast").font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.bottom, 8)
      ForEach(Array(forecast.daily.enumerated()), id: \.offset) { i, day in
        HStack {
          Group { if i == 0 { Text("Today") } else { Text(verbatim: String(day.date.dropFirst(5)).replacingOccurrences(of: "-", with: "/")) } }
            .frame(width: 52, alignment: .leading)
          Image(systemName: symbol(day.code)).symbolRenderingMode(.multicolor).frame(width: 28)
          Text(describeWeather(day.code)).font(.footnote).foregroundStyle(.secondary)
          Spacer()
          Text("\(Int(day.min.rounded()))°").foregroundStyle(.secondary)
          Text("\(Int(day.max.rounded()))°").frame(width: 36, alignment: .trailing)
        }
        .padding(.vertical, 8)
      }
    }
    .padding()
    .glassEffect(in: .rect(cornerRadius: 24))
  }
}

struct MessageCard<Actions: View>: View {
  let icon: String
  let title: LocalizedStringKey
  let message: String
  @ViewBuilder let actions: Actions

  var body: some View {
    VStack(spacing: 12) {
      Image(systemName: icon).font(.largeTitle)
      Text(title).font(.headline)
      Text(message).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
      actions
    }
    .padding(24)
    .frame(maxWidth: .infinity)
    .glassEffect(in: .rect(cornerRadius: 24))
  }
}

struct CityRow: View {
  let city: City
  let favorite: Bool
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      HStack {
        VStack(alignment: .leading) {
          Text(city.name)
          Text(city.region).font(.caption).foregroundStyle(.secondary)
        }
        Spacer()
        if favorite { Image(systemName: "star.fill").foregroundStyle(.yellow) }
      }
    }
    .foregroundStyle(.primary)
  }
}

struct FavoriteCard: View {
  let item: FavoritesViewModel.Item

  var body: some View {
    HStack {
      VStack(alignment: .leading, spacing: 2) {
        Text(item.city.name).font(.title3.weight(.semibold))
        Text(item.city.region).font(.caption)
        Spacer()
        Text(item.current.map { describeWeather($0.code) } ?? (item.failed ? "Couldn't load" : "")).font(.footnote)
      }
      Spacer()
      VStack(alignment: .trailing) {
        if let current = item.current {
          Image(systemName: symbol(current.code)).symbolRenderingMode(.multicolor).font(.title2)
          Spacer()
          Text("\(Int(current.temperature.rounded()))°").font(.system(size: 40, weight: .light))
        } else if !item.failed {
          ProgressView().tint(.white)
        }
      }
    }
    .foregroundStyle(.white)
    .padding()
    .frame(height: 110)
    .background(LinearGradient(colors: skyColors(item.current?.code), startPoint: .top, endPoint: .bottom),
                in: .rect(cornerRadius: 22))
  }
}
