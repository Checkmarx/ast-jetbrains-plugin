package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.*;

import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.locateAndClickOnButton;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class TestCxOneAssist {
    @Test
    @Video
    @DisplayName("Validate all the engines are selected by default once welcome page devassit check box is marked")
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
