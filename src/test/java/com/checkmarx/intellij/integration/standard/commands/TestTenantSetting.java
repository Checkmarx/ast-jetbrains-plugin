package com.checkmarx.intellij.integration.standard.commands;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.integration.standard.BaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Integration tests for TenantSetting functionality.
 */
public class TestTenantSetting extends BaseTest {

    private GlobalSettingsSensitiveState sensitiveState;

    @BeforeEach
    public void beforeEach() {
        sensitiveState = new GlobalSettingsSensitiveState();
        sensitiveState.setApiKey(System.getenv("CX_APIKEY"));
    }

    @AfterEach
    public void afterEach() {
        sensitiveState = null;
    }

    /**
     * Test scan permission check with valid credentials
     */
    @Test
    public void testScanPermissionCheck() {
        Assertions.assertDoesNotThrow(() -> {
            boolean isScanAllowed = TenantSetting.isScanAllowed();
            // We assert true here because in a standard test environment, scan should be allowed
            Assertions.assertTrue(isScanAllowed, "Scan should be allowed for test tenant");
        });
    }
} 