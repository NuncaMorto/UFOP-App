// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription
import Foundation

let packageDir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let xcframeworkPath = packageDir
    .appendingPathComponent("../rust-core/target/ios/CampusCoreFFI.xcframework")
    .standardizedFileURL
    .path

let hasCompiledXCFramework = FileManager.default.fileExists(atPath: xcframeworkPath)

var targets: [Target] = [
    .target(
        name: "CampusUFOP",
        dependencies: hasCompiledXCFramework ? ["CampusCoreFFI"] : [],
        path: "CampusUFOP",
        // The UniFFI-generated bindings in CoreFFI/Generated redeclare the same
        // public types as the hand-written CoreFFI/CampusCoreBridge.swift fallback.
        // Exclude them so the package compiles without redeclaration conflicts.
        exclude: ["CoreFFI/Generated"]
    )
]

if hasCompiledXCFramework {
    targets.insert(
        .binaryTarget(
            name: "CampusCoreFFI",
            path: "../rust-core/target/ios/CampusCoreFFI.xcframework"
        ),
        at: 0
    )
}

let package = Package(
    name: "CampusUFOP",
    defaultLocalization: "pt",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "CampusUFOP",
            targets: ["CampusUFOP"]
        )
    ],
    targets: targets
)
