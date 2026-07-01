package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.*;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

public class TestCxOneAssist extends com.checkmarx.intellij.ast.test.ui.BaseUITest {

    @AfterEach
    public void cleanupDialogs() {
        // Close welcome popup if still open
        if (hasAnyComponent(WELCOME_CLOSE_BUTTON)) {
            click(WELCOME_CLOSE_BUTTON);
        }
        // Close settings dialog if still open
        if (hasAnyComponent(OK_BTN)) {
            click(OK_BTN);
        }
    }

    @Test
    @Video
    @DisplayName("Validate all engines are selected by default once welcome page DevAssist checkbox is marked")
    public void testDevAssistCheckBoxIsSelected(){
        // Given: User is logged in and welcome page is loaded
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);

        // When: User validates the welcome page and closes it
        validateWelcomePageLoadedSuccessfully(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);

        // Then: All engines should be selected by default
        navigateToCxOneAssistPage();
        validateCxOneAssistPageLoadedSuccessfully();
        locateAndClickOnButton(OK_BTN);
    }

    @Test
    @Video
    @DisplayName("Validate Dev Assist checkbox is not selected by default when all the engines are disabled")
    public void testDevAssistCheckBoxIsNotSelected(){
        // Given: User is logged in and welcome page is loaded
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateWelcomePageLoadedSuccessfully(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);

        // When: User disables all engines in CxOne Assist
        navigateToCxOneAssistPage();
        selectAndUnSelectAllEngines(false);
        locateAndClickOnButton(OK_BTN);

        // Then: Dev Assist checkbox should not be selected by default upon re-login
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateWelcomePageLoadedSuccessfully(false);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);
    }
}
