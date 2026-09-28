package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.*;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

public class TestCxOneAssist extends com.checkmarx.intellij.ast.test.ui.BaseUITest {

    @BeforeEach
    public void checkResults() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
//        validateSuccessfulLogin(true);
        validateWelcomePageLoadedSuccessfully(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);
        navigateToCxOneAssistPage();
    }

    //@Disabled("Flaky - TC")
    @Test
    @Video
    @DisplayName("Validate all engines are selected by default once welcome page DevAssist checkbox is marked")
    public void testDevAssistCheckBoxIsSelected(){
        try {
            validateCxOneAssistPageLoadedSuccessfully();
            locateAndClickOnButton(OK_BTN);
        } finally {
            // If the assertion above fails (engine checkboxes not all selected - known
            // flakiness), the dialog would otherwise stay open and break the next test's
            // openSettings() call. Always close it, via OK or Cancel.
            closeSettingsDialogIfOpen();
        }
    }

    @Test
    @Video
    @DisplayName("Validate Dev Assist checkbox is not selected by default when all the engines are disabled")
    public void testDevAssistCheckBoxIsNotSelected(){
        selectAndUnSelectAllEngines(false);
        locateAndClickOnButton(OK_BTN);

        // Then: Dev Assist checkbox should not be selected by default upon re-login
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        try {
            validateWelcomePageLoadedSuccessfully(false);
        } finally {
            // Close the welcome popup and the underlying Settings dialog, even if the
            // assertion above fails, so the IDE is back to its prior state (no dialogs
            // left open) instead of breaking the next test's openSettings() call.
            locateAndClickOnButton(WELCOME_CLOSE_BUTTON);
            closeSettingsDialogIfOpen();
        }
    }

}
