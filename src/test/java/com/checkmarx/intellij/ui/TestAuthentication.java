package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.*;

import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;

public class TestAuthentication extends BaseUITest{
    @Test
    @DisplayName("Test successful AST authentication using API key")
    @Video
    public void testASTSuccessAuthentication() {
        // Test successfully connection
        testASTConnection(true);
    }

    @Test
    @DisplayName("Test failed AST authentication using wrong API key")
    @Video
    public void testASTFailedAuthentication() {
        // Test wrong connection
        testASTConnection(false);
    }

    @Test
    @DisplayName("Validate dev assist welcome page launched after successful login")
    @Video
    public void validateWelcomePage(){
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateWelcomePageLoadedSuccessfully(true);
        validateSuccessfulLogin(true);
    }
}
