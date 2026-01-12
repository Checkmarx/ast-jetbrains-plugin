package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.integration.Environment;
import org.junit.jupiter.api.Test;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;

public class TestAuthentication extends BaseUITest{
    @Test
    @Video
    public void testASTSuccessAuthentication() {
        // Test successfully connection
        testASTConnection(true);
    }

    @Test
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
                Constants.INVALID_BASE_URL_ERROR_MESSAGE,
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
                Constants.INVALID_TENANT_ERROR_MESSAGE,
                "Invalid Tenant"
        );
    }


}
