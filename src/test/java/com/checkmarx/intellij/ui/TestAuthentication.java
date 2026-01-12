package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.integration.Environment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.TestConstants.*;

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
    @Video
    public void testASTSettingsPageTittleLink() {

        testASTSettingsPageTittlePresent();
    }

    @Test
    @Video
    public void testASTOAuthValidInput() {
        testASTOAuthRadioButton(true);
    }

    @Test
    @Video
    public void testASTOAuthInvalidBaseUrl() {
        testASTOAuthInvalidInput(
                "http://invalid-url",
                Environment.TENANT,
                INVALID_BASE_URL_ERROR,
                INVALID_BASE_URL_ERROR_MESSAGE,
                "Invalid Base URL"
        );
    }


    @Test
    @Video
    public void testASTOAuthInvalidTenant() {
        testASTOAuthInvalidInput(
                Environment.BASE_URL,
                "invalid-tenant",
                INVALID_TENANT_ERROR,
                INVALID_TENANT_ERROR_MESSAGE,
                "Invalid Tenant"
        );
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
