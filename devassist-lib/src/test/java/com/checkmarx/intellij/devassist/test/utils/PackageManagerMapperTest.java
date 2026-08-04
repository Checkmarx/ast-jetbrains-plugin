package com.checkmarx.intellij.devassist.test.utils;

import com.checkmarx.intellij.devassist.utils.PackageManagerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackageManagerMapperTest {

    @Test
    @DisplayName("Gradle maps to mvn")
    void testGradleMapToMvn() {
        String result = PackageManagerMapper.mapToRemediationFormat("gradle");
        assertEquals("mvn", result);
    }

    @Test
    @DisplayName("SBT maps to mvn")
    void testSbtMapToMvn() {
        String result = PackageManagerMapper.mapToRemediationFormat("sbt");
        assertEquals("mvn", result);
    }

    @Test
    @DisplayName("CocoaPods maps to swift")
    void testCocoaPodsMapToSwift() {
        String result = PackageManagerMapper.mapToRemediationFormat("cocoapods");
        assertEquals("swift", result);
    }

    @Test
    @DisplayName("Carthage maps to swift")
    void testCarthageMapToSwift() {
        String result = PackageManagerMapper.mapToRemediationFormat("carthage");
        assertEquals("swift", result);
    }

    @Test
    @DisplayName("NPM passes through unchanged")
    void testNpmPassThrough() {
        String result = PackageManagerMapper.mapToRemediationFormat("npm");
        assertEquals("npm", result);
    }

    @Test
    @DisplayName("Maven passes through unchanged")
    void testMavenPassThrough() {
        String result = PackageManagerMapper.mapToRemediationFormat("mvn");
        assertEquals("mvn", result);
    }

    @Test
    @DisplayName("PyPI passes through unchanged")
    void testPypiPassThrough() {
        String result = PackageManagerMapper.mapToRemediationFormat("pypi");
        assertEquals("pypi", result);
    }

    @Test
    @DisplayName("Go passes through unchanged")
    void testGoPassThrough() {
        String result = PackageManagerMapper.mapToRemediationFormat("go");
        assertEquals("go", result);
    }

    @Test
    @DisplayName("Nuget passes through unchanged")
    void testNugetPassThrough() {
        String result = PackageManagerMapper.mapToRemediationFormat("nuget");
        assertEquals("nuget", result);
    }

    @Test
    @DisplayName("Case insensitive: GRADLE maps to mvn")
    void testGradleUppercaseMapToMvn() {
        String result = PackageManagerMapper.mapToRemediationFormat("GRADLE");
        assertEquals("mvn", result);
    }

    @Test
    @DisplayName("Case insensitive: SBT maps to mvn")
    void testSbtUppercaseMapToMvn() {
        String result = PackageManagerMapper.mapToRemediationFormat("SBT");
        assertEquals("mvn", result);
    }

    @Test
    @DisplayName("Case insensitive: CocoaPods maps to swift")
    void testCocoaPodsUppercaseMapToSwift() {
        String result = PackageManagerMapper.mapToRemediationFormat("COCOAPODS");
        assertEquals("swift", result);
    }

    @Test
    @DisplayName("Case insensitive: Carthage maps to swift")
    void testCarthageUppercaseMapToSwift() {
        String result = PackageManagerMapper.mapToRemediationFormat("CARTHAGE");
        assertEquals("swift", result);
    }

    @Test
    @DisplayName("Null input returns null")
    void testNullInput() {
        String result = PackageManagerMapper.mapToRemediationFormat(null);
        assertNull(result);
    }

    @Test
    @DisplayName("Empty string input returns empty string")
    void testEmptyStringInput() {
        String result = PackageManagerMapper.mapToRemediationFormat("");
        assertEquals("", result);
    }

    @Test
    @DisplayName("Unknown package manager passes through unchanged")
    void testUnknownPackageManagerPassThrough() {
        String result = PackageManagerMapper.mapToRemediationFormat("unknown_manager");
        assertEquals("unknown_manager", result);
    }
}
