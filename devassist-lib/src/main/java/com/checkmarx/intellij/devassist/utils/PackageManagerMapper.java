package com.checkmarx.intellij.devassist.utils;

import java.util.List;

/**
 * Utility class for mapping new package manager names to legacy names
 * supported by the Checkmarx remediation tools.
 *
 * This mapper handles the conversion of package manager names returned by ast-cli
 * (gradle, sbt, cocoapods, carthage) to the legacy names expected by the
 * Checkmarx remediation API (mvn, swift).
 */
public final class PackageManagerMapper {

    private PackageManagerMapper() {
        throw new UnsupportedOperationException("Cannot instantiate PackageManagerMapper");
    }

    /**
     * Maps new package manager names to legacy names supported by remediation tools.
     *
     * Mapping rules:
     * - gradle → mvn
     * - sbt → mvn
     * - cocoapods → swift
     * - carthage → swift
     * - all others → unchanged
     *
     * @param packageManager the package manager name (e.g., "gradle", "npm", "cocoapods")
     * @return the mapped package manager name, or the original if no mapping exists
     */
    public static String mapToRemediationFormat(String packageManager) {
        if (packageManager == null || packageManager.isEmpty()) {
            return packageManager;
        }

        String lowerCase = packageManager.toLowerCase();

        switch (lowerCase) {
            case "gradle":
            case "sbt":
                return "mvn";
            case "cocoapods":
            case "carthage":
                return "swift";
            default:
                return packageManager;
        }
    }

    /**
     * Infers companion lock file names based on the manifest file name.
     * Some manifests may have multiple companion files (e.g., package.json has both package-lock.json and yarn.lock).
     *
     * @param fileName name of the manifest file
     * @return list of companion file names; empty list if no companions are defined
     */
    public static List<String> getCompanionFileNames(String fileName) {
        // npm/Yarn - support both package-lock.json (npm) and yarn.lock (yarn)
        if (fileName.equals("package.json")) {
            return List.of("package-lock.json", "yarn.lock");
        }

        // .NET
        if (fileName.contains(".csproj")) {
            return List.of("packages.lock.json");
        }

        // Swift Package Manager (AST-165765)
        if (fileName.equals("Package.swift")) {
            return List.of("Package.resolved");
        }
        if (fileName.startsWith("Package@swift-") && fileName.endsWith(".swift")) {
            return List.of(fileName.replace(".swift", ".resolved"));
        }

        // CocoaPods (AST-165761)
        if (fileName.equals("Podfile")) {
            return List.of("Podfile.lock");
        }

        // Carthage
        if (fileName.equals("Cartfile") || fileName.equals("Cartfile.private")) {
            return List.of("Cartfile.resolved");
        }

        // Ruby Bundler
        if (fileName.equals("Gemfile")) {
            return List.of("Gemfile.lock");
        }

        // PHP Composer
        if (fileName.equals("composer.json")) {
            return List.of("composer.lock");
        }

        // Python Poetry or UV
        if (fileName.equals("pyproject.toml")) {
            return List.of("poetry.lock", "uv.lock");
        }

        // Dart/Flutter Pub
        if (fileName.equals("pubspec.yaml")) {
            return List.of("pubspec.lock");
        }

        return List.of();
    }
}
