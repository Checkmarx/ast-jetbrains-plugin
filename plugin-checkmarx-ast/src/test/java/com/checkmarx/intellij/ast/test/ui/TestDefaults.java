package com.checkmarx.intellij.ast.test.ui;

import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;
import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.*;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestDefaults extends BaseUITest {

    @BeforeEach
    public void checkResults() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateSuccessfulLogin(true);
    }

    @Disabled("Flaky - TC05")
    @Test
    @Video
    @Order(1)
    @DisplayName("TC05: Verify all severity icons are selected by default on first install")
    public void testDefaultSeverityFiltersSelected() {
        openScanResultsPanel();
        verifyDefaultSeverityFiltersSelected();
    }

    @Test
    @Video
    @DisplayName("TC17 - Verify Default Group By Option Is Severity")
    public void testDefaultGroupBySeverity() {
        verifyDefaultGroupByIsSeverity();
    }
}