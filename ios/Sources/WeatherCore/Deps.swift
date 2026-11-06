import Foundation

public struct HTTPResponse: Sendable {
  public let status: Int
  public let body: Data
  public init(status: Int, body: Data) {
    self.status = status
    self.body = body
  }
}

/// Networking. The app passes URLSession; the CLI passes an implementation that returns fixtures.
@MainActor public protocol HTTPClient: AnyObject {
  func get(_ url: String) async throws -> HTTPResponse
}

public struct Permission: Sendable {
  public var granted: Bool
  public var canAskAgain: Bool
  public init(granted: Bool, canAskAgain: Bool) {
    self.granted = granted
    self.canAskAgain = canAskAgain
  }
}

/// Location and the path to the Settings app. The app uses CoreLocation; the CLI uses a fake.
@MainActor public protocol LocationService: AnyObject {
  func requestPermission() async -> Permission
  func currentPosition() async throws -> Coords
  /// Reverse geocoding (coordinates -> city name). nil if unknown.
  func placeName(_ coords: Coords) async throws -> String?
  func openSettings()
}

/// Favorites stored on the device. The app uses UserDefaults; the CLI uses memory.
@MainActor public protocol FavoritesStore: AnyObject {
  func list() async -> [City]
  func save(_ cities: [City]) async
}

public enum AppTab: String, Codable, Sendable, CaseIterable {
  case weather, search, favorites
}

public enum Route: Sendable {
  case city(id: Int)
}

/// Screen navigation. AppRoot implements it and passes it to ViewModels.
@MainActor public protocol Navigator: AnyObject {
  func push(_ route: Route)
  func selectTab(_ tab: AppTab)
}

/// Dependencies injected via the constructor. No DI framework.
@MainActor public struct Deps {
  public let api: OpenMeteo
  public let location: LocationService
  public let favorites: FavoritesStore
  public init(http: HTTPClient, location: LocationService, favorites: FavoritesStore) {
    api = OpenMeteo(http: http)
    self.location = location
    self.favorites = favorites
  }
}
