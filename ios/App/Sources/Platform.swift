import CoreLocation
import Foundation
import UIKit
import WeatherCore

// Real implementations of the dependencies Core declares (the Mobile module side in Reflex).

@MainActor final class URLSessionHTTP: HTTPClient {
  func get(_ url: String) async throws -> HTTPResponse {
    guard let u = URL(string: url) else { throw CoreError("URL が正しくない: \(url)") }
    do {
      let (data, res) = try await URLSession.shared.data(from: u)
      return HTTPResponse(status: (res as? HTTPURLResponse)?.statusCode ?? 0, body: data)
    } catch {
      throw CoreError("ネットワークに接続できません")
    }
  }
}

@MainActor final class DeviceLocation: NSObject, LocationService, @preconcurrency CLLocationManagerDelegate {
  private let manager = CLLocationManager()
  private var permissionWaiters: [CheckedContinuation<Void, Never>] = []
  private var positionWaiters: [CheckedContinuation<CLLocation, Error>] = []

  override init() {
    super.init()
    manager.delegate = self
  }

  private var permission: Permission {
    switch manager.authorizationStatus {
    case .authorizedAlways, .authorizedWhenInUse: Permission(granted: true, canAskAgain: true)
    case .notDetermined: Permission(granted: false, canAskAgain: true)
    default: Permission(granted: false, canAskAgain: false) // On iOS, once denied, the app can never ask again
    }
  }

  func requestPermission() async -> Permission {
    if manager.authorizationStatus == .notDetermined {
      await withCheckedContinuation { permissionWaiters.append($0); manager.requestWhenInUseAuthorization() }
    }
    return permission
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    guard manager.authorizationStatus != .notDetermined else { return }
    permissionWaiters.forEach { $0.resume() }
    permissionWaiters = []
  }

  func currentPosition() async throws -> Coords {
    let location = try await withCheckedThrowingContinuation { positionWaiters.append($0); manager.requestLocation() }
    return Coords(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
  }

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard let location = locations.last else { return }
    positionWaiters.forEach { $0.resume(returning: location) }
    positionWaiters = []
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    positionWaiters.forEach { $0.resume(throwing: CoreError("位置を取得できない")) }
    positionWaiters = []
  }

  func placeName(_ coords: Coords) async throws -> String? {
    let placemarks = try await CLGeocoder().reverseGeocodeLocation(
      CLLocation(latitude: coords.latitude, longitude: coords.longitude), preferredLocale: Locale(identifier: "ja_JP"))
    let p = placemarks.first
    return p?.locality ?? p?.subAdministrativeArea ?? p?.administrativeArea
  }

  func openSettings() {
    UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
  }
}

@MainActor final class DefaultsFavorites: FavoritesStore {
  private let key = "favorites"
  func list() async -> [City] {
    UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode([City].self, from: $0) } ?? []
  }
  func save(_ cities: [City]) async {
    UserDefaults.standard.set(try? JSONEncoder().encode(cities), forKey: key)
  }
}
