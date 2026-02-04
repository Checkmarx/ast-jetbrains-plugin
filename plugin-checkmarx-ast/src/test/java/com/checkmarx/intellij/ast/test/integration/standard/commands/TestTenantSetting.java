package com.checkmarx.intellij.ast.test.integration.standard.commands;

import com.checkmarx.intellij.ast.test.integration.standard.BaseTest;
import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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