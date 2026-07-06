package com.checkmarx.intellij.cxdevassist.test.integration;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.when;

/**
 * Lightweight base for integration tests in plugin-checkmarx-devassist.
 * Boots the IntelliJ test platform (BasePlatformTestCase) without any
 * live API credentials.  Registers the IgnoreFileManager project service
 * so that DevAssist classes can be instantiated without NullPointerException.
 */
public abstract class LocalBasePlatformTest extends BasePlatformTestCase {

    @BeforeEach
    @Override
    public final void setUp() throws Exception {
        super.setUp();

        IgnoreFileManager mockIgnoreFileManager = Mockito.mock(IgnoreFileManager.class);
        Path tempIgnoreFile = Files.createTempFile("cxDevAssistIgnoredTempList", ".json");
        Files.writeString(tempIgnoreFile, "[]");
        when(mockIgnoreFileManager.getTempListPath()).thenReturn(tempIgnoreFile);
        ServiceContainerUtil.registerServiceInstance(
                getProject(), IgnoreFileManager.class, mockIgnoreFileManager);
    }

    @AfterEach
    @Override
    public final void tearDown() throws Exception {
        // Reset global settings to clean defaults before platform teardown.
        // This prevents state leakage between tests (GlobalSettingsState.getState()
        // returns `this`, so snapshot/restore via getState() is a no-op).
        GlobalSettingsState.getInstance().loadState(new GlobalSettingsState());
        GlobalSettingsSensitiveState.getInstance().reset();
        super.tearDown();
    }
}
