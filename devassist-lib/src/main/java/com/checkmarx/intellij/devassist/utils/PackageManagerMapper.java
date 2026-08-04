package com.checkmarx.intellij.devassist.utils;

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
}
