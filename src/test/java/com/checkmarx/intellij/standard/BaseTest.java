package com.checkmarx.intellij.standard;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseTest extends BasePlatformTestCase {

    @BeforeEach
    public final void setUp() throws Exception {
        super.setUp();
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();

        state.setServerURL(System.getenv("CX_BASE_URI"));
        if (StringUtils.isNotBlank(System.getenv("CX_TENANT"))) {
            state.setTenantName(System.getenv("CX_TENANT"));
        }
        sensitiveState.setApiKey(System.getenv("CX_APIKEY"));
    }

    @AfterEach
    public final void tearDown() throws Exception {
        super.tearDown();
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();

        state.loadState(new GlobalSettingsState());
        sensitiveState.reset();
    }
}
