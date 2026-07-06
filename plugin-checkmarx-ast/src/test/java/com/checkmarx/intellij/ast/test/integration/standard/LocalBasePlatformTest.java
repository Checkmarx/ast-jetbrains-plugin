package com.checkmarx.intellij.ast.test.integration.standard;

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
 * Lightweight base for integration tests that exercise local IntelliJ Platform
 * behavior without any live API credentials.
 * <p>
 * Registers required project services so that classes under test can call
 * {@code project.getService(...)} without NullPointerException.
 */
public abstract class LocalBasePlatformTest extends BasePlatformTestCase {

    @BeforeEach
    @Override
    public final void setUp() throws Exception {
        super.setUp();

        // IgnoreFileManager is required by DevAssist classes referenced from GlobalSettingsComponent
        IgnoreFileManager mockIgnoreFileManager = Mockito.mock(IgnoreFileManager.class);
        Path tempIgnoreFile = Files.createTempFile("checkmarxIgnoredTempList", ".json");
        Files.writeString(tempIgnoreFile, "[]");
        when(mockIgnoreFileManager.getTempListPath()).thenReturn(tempIgnoreFile);
        ServiceContainerUtil.registerServiceInstance(
                getProject(), IgnoreFileManager.class, mockIgnoreFileManager);
    }

    @AfterEach
    @Override
    public final void tearDown() throws Exception {
        super.tearDown();
    }
}
