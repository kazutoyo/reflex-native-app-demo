import Foundation

public struct City: Codable, Sendable, Hashable {
  public let id: Int
  public let name: String
  public let region: String
  public let latitude: Double
  public let longitude: Double
}

public struct Current: Codable, Sendable, Hashable {
  public let temperature: Double
  public let code: Int
}

public struct Day: Codable, Sendable, Hashable {
  public let date: String
  public let code: Int
  public let max: Double
  public let min: Double
}

public struct Forecast: Codable, Sendable, Hashable {
  public let current: Current
  public let daily: [Day]
}

public struct Coords: Sendable {
  public let latitude: Double
  public let longitude: Double
  public init(latitude: Double, longitude: Double) {
    self.latitude = latitude
    self.longitude = longitude
  }
}

/// Error shown to the user. message goes into state as is.
public struct CoreError: Error, Sendable {
  public let message: String
  public init(_ message: String) { self.message = message }
}

func errorMessage(_ error: Error) -> String {
  (error as? CoreError)?.message ?? error.localizedDescription
}

/// Keys for encoding state to JSON. nil is written as `null` too (to match the Kotlin version).
struct Key: CodingKey, ExpressibleByStringLiteral {
  let stringValue: String
  init(stringLiteral value: String) { stringValue = value }
  init?(stringValue: String) { self.stringValue = stringValue }
  var intValue: Int? { nil }
  init?(intValue: Int) { nil }
}

/// WMO weather code -> display values.
public func describeWeather(_ code: Int) -> String {
  switch code {
  case 0: "快晴"
  case 1...2: "晴れ"
  case 3: "くもり"
  case ...48: "霧"
  case ...57: "霧雨"
  case ...67: "雨"
  case ...77: "雪"
  case ...82: "にわか雨"
  case ...86: "にわか雪"
  default: "雷雨"
  }
}
