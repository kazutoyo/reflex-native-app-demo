// swift-tools-version: 6.0
import PackageDescription

let package = Package(
  name: "Weather",
  platforms: [.macOS(.v14), .iOS(.v17)],
  products: [
    .library(name: "WeatherCore", targets: ["WeatherCore"]),
  ],
  targets: [
    // UI-less Core. Does not import UIKit / SwiftUI / CoreLocation
    .target(name: "WeatherCore"),
    // CLI that runs Core headless on macOS. Speaks NDJSON over stdin/stdout
    .executableTarget(name: "weather-cli", dependencies: ["WeatherCore"], path: "Sources/WeatherCLI"),
  ]
)
