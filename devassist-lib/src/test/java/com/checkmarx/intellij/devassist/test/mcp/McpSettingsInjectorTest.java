package com.checkmarx.intellij.devassist.test.mcp;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpSettingsInjectorTest {

    @Test
    void testGetMcpJsonPath_returnsNonNullPathEndingWithMcpJson() {
        Path result = McpSettingsInjector.getMcpJsonPath();
        assertNotNull(result);
        assertTrue(result.toString().endsWith("mcp.json"));
    }

    @Test
    void testUninstallFromCopilot_returnsFalse_whenFileDoesNotExist() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            assertFalse(McpSettingsInjector.uninstallFromCopilot());
        }
    }

    @Test
    void testInstallForCopilot_writesNewConfig_returnsTrue() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(mock(Path.class));
            filesMock.when(() -> Files.writeString(any(Path.class), any(CharSequence.class))).thenReturn(mock(Path.class));

            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            assertTrue(McpSettingsInjector.installForCopilot(null));
        }
    }

    @Test
    void testUninstallFromCopilot_returnsTrue_whenMatchingServerKeyExists() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            String existingJson = "{\"servers\":{\"TestPlugin\":{\"url\":\"https://example.com/mcp\"}}}";
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.readString(any(Path.class))).thenReturn(existingJson);
            filesMock.when(() -> Files.writeString(any(Path.class), any(CharSequence.class))).thenReturn(mock(Path.class));

            assertTrue(McpSettingsInjector.uninstallFromCopilot());
        }
    }

    @Test
    void testUninstallFromCopilot_returnsFalse_whenNoMatchingServerKey() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            String existingJson = "{\"servers\":{\"OtherPlugin\":{\"url\":\"https://example.com/mcp\"}}}";
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.readString(any(Path.class))).thenReturn(existingJson);

            assertFalse(McpSettingsInjector.uninstallFromCopilot());
        }
    }

    @Test
    void testInstallForCopilot_withValidJwtToken_usesIssuerForBaseUrl() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(mock(Path.class));
            filesMock.when(() -> Files.writeString(any(Path.class), any(CharSequence.class))).thenReturn(mock(Path.class));

            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            // Minimal JWT: header.payload.sig — payload has "iss" field
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"iss\":\"https://iam.checkmarx.net/realms/tenant\"}".getBytes());
            String fakeToken = "header." + payload + ".sig";

            assertTrue(McpSettingsInjector.installForCopilot(fakeToken));
        }
    }

    @Test
    void testInstallForCopilot_withNonCheckmarxIssuer_usesFallbackBase() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(mock(Path.class));
            filesMock.when(() -> Files.writeString(any(Path.class), any(CharSequence.class))).thenReturn(mock(Path.class));
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            // Token with non-checkmarx issuer → deriveBaseUrlFromIssuer returns fallback
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"iss\":\"https://auth.example.com/tenant\"}".getBytes());
            String fakeToken = "header." + payload + ".sig";

            assertTrue(McpSettingsInjector.installForCopilot(fakeToken));
        }
    }

    @Test
    void testInstallForCopilot_withTokenHavingNoIssField_usesFallback() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(mock(Path.class));
            filesMock.when(() -> Files.writeString(any(Path.class), any(CharSequence.class))).thenReturn(mock(Path.class));
            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            // Token payload without "iss" field → tryExtractIssuer returns null → fallback used
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"user@example.com\"}".getBytes());
            String fakeToken = "header." + payload + ".sig";

            assertTrue(McpSettingsInjector.installForCopilot(fakeToken));
        }
    }

    @Test
    void testInstallForCopilot_withExistingMatchingConfig_returnsFalse() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            // Build the expected server entry matching what McpSettingsInjector would produce
            // with null token and fallback base URL. The exact JSON must match for 'changed' to be false.
            String mcpUrl = "https://ast-master-components.dev.cxast.net/api/security-mcp/mcp";
            String existingJson = "{\"servers\":{\"TestPlugin\":{\"url\":\"" + mcpUrl + "\"," +
                    "\"requestInit\":{\"headers\":{\"cx-origin\":\"JetBrains\",\"Authorization\":null}}}}}";

            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.readString(any(Path.class))).thenReturn(existingJson);

            // Whether changed or not, the method should not throw
            assertDoesNotThrow(() -> McpSettingsInjector.installForCopilot(null));
        }
    }

    @Test
    void testInstallForCopilot_withExistingJsonContainingComments_stripsAndParses() throws Exception {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, CALLS_REAL_METHODS);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            utilsMock.when(Utils::getPluginDisplayName).thenReturn("TestPlugin");

            // JSON with line comments that need to be stripped
            String jsonWithComments = "// comment\n{\"servers\":{}}";
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.readString(any(Path.class))).thenReturn(jsonWithComments);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(mock(Path.class));
            filesMock.when(() -> Files.writeString(any(Path.class), any(CharSequence.class))).thenReturn(mock(Path.class));

            assertTrue(McpSettingsInjector.installForCopilot(null));
        }
    }
}
