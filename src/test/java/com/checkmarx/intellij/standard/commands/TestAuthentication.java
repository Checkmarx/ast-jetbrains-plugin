package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.commands.Authentication;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.standard.BaseTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for global settings.
 */
public class TestAuthentication extends BaseTest {

    private GlobalSettingsState state;
    private GlobalSettingsSensitiveState sensitiveState;

    @BeforeEach
    public void beforeEach() {
        state = new GlobalSettingsState();
        sensitiveState = new GlobalSettingsSensitiveState();
        state.setServerURL(System.getenv("CX_BASE_URI"));
        if (StringUtils.isNotBlank(System.getenv("CX_TENANT"))) {
            state.setTenantName(System.getenv("CX_TENANT"));
        }
        sensitiveState.setApiKey(System.getenv("CX_APIKEY"));
    }

    @AfterEach
    public void afterEach() {
        state = null;
        sensitiveState = null;
    }

    /**
     * Provide correct credentials and test auth validation
     */
    @Test
    public void testSuccessfulConnection() {
        Assertions.assertDoesNotThrow(() -> Authentication.validateConnection(state, sensitiveState));
    }

    /**
     * Test failing connection due to wrong server URL.
     */
    @Test
    public void testFailServerURL() {
        state.setServerURL("https://wronghost.com");
        Assertions.assertThrows(CxException.class, () -> Authentication.validateConnection(state, sensitiveState));
    }

    /**
     * Test failing connection due to wrong tenant
     */
    @Test
    public void testFailTenant() {
        state.setTenantName("wrong_tenant");
        Assertions.assertThrows(CxException.class, () -> Authentication.validateConnection(state, sensitiveState));
    }

    /**
     * Test failing connection due to wrong api key
     */
    @Test
    public void testFailAPIKey() {
        sensitiveState.setApiKey("invalid_key");
        Assertions.assertThrows(CxException.class, () -> Authentication.validateConnection(state, sensitiveState));
    }
}
