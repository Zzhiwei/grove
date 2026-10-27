// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Grove",
    platforms: [.macOS(.v13)],
    targets: [
        .target(name: "GroveCore"),
        .executableTarget(name: "Grove", dependencies: ["GroveCore"]),
        .testTarget(name: "GroveCoreTests", dependencies: ["GroveCore"]),
    ]
)
