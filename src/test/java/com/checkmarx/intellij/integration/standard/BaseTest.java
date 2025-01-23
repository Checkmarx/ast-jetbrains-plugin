package com.checkmarx.intellij.integration.standard;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseTest extends BasePlatformTestCase {

    @BeforeEach
    public final void setUp() throws Exception {
        super.setUp();
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();
        sensitiveState.setApiKey(System.getenv("CX_APIKEY"));
        state.setAdditionalParameters("--debug");
    }

    @AfterEach
    public final void tearDown() throws Exception {
        super.tearDown();
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();

        state.loadState(new GlobalSettingsState());
        sensitiveState.reset();
    }

    protected final Project getEnvProject() {
        return Assertions.assertDoesNotThrow(() -> com.checkmarx.intellij.commands.Project.getList()
                .stream()
                .filter(p -> p.getName()
                        .equals(Environment.PROJECT_NAME))
                .findFirst()
                .orElseThrow());
    }
}
