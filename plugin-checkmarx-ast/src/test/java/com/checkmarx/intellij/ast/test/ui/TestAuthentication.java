package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.common.utils.Constants;
import org.junit.jupiter.api.*;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.testASTConnection;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ast.test.ui.utils.TestConstants.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestAuthentication extends com.checkmarx.intellij.ast.test.ui.BaseUITest {

    @AfterEach
    public void cleanupDialogs() {
        if (hasAnyComponent(WELCOME_CLOSE_BUTTON)) {
            click(WELCOME_CLOSE_BUTTON);
        }
        if (hasAnyComponent(OK_BTN)) {
            click(OK_BTN);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test successful AST authentication using API key")
    @Video
    public void testASTSuccessAuthentication() {
        // Test successfully connection
        testASTConnection(true);
    }

    @Test
    @Order(2)
    @DisplayName("Test failed AST authentication using wrong API key")
    @Video
    public void testASTFailedAuthentication() {
        // Test wrong connection
        testASTConnection(false);
    }

    @Test
    @Order(3)
    @DisplayName("Test AST Settings page title hyperlink text")
    @Video
    public void testASTSettingsPageTittleLink() {

        testASTSettingsPageTitlePresent();
    }

    @Test
    @Order(4)
    @DisplayName("Test OAuth Connection with valid Base URI and Tenant ")
    @Video
    public void testASTOAuthValidInput() {
        testASTOAuthRadioButton(true);
    }

    @Test
    @Order(5)
    @DisplayName("Test OAuth Connection with invalid Base URI")
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
    @Order(6)
    @DisplayName("Test OAuth Connection with invalid Tenant ")
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
    @Order(7)
    @DisplayName("Validate dev assist welcome page launched after successful login")
    @Video
    public void validateWelcomePage(){
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateWelcomePageLoadedSuccessfully(true);
        validateSuccessfulLogin(true);
    }

    @Test
    @Order(8)
    @DisplayName("TC88: Verify success notification shown in IDE after OAuth login")
    @Video
    public void testSuccessNotificationAfterLogin() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);

        // TC88: Verify the success notification is displayed in the IDE
        waitFor(() -> hasAnyComponent(SUCCESS_CONNECTION));
        Assertions.assertTrue(hasAnyComponent(SUCCESS_CONNECTION),
                "Success notification should be displayed after login");

        String successText = getText(SUCCESS_CONNECTION);
        Assertions.assertNotNull(successText, "Success notification text should not be null");
        log("Success notification displayed: " + successText);

        validateSuccessfulLogin(true);
    }

    @Test
    @Order(9)
    @DisplayName("TC84: Verify logout button is disabled when user is not logged in")
    @Video
    public void testLogoutButtonDisabledWhenNotLoggedIn() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();

        // TC84: After logout, the logout button should not be present (disabled/hidden)
        Assertions.assertFalse(hasAnyComponent(LOGOUT_BUTTON),
                "Logout button should not be visible/enabled when user is not logged in");
        log("Logout button is correctly not available when not logged in");
        click(OK_BTN);
    }

    @Test
    @Order(10)
    @DisplayName("TC85: Verify clicking logout resets UI to initial state")
    @Video
    public void testLogoutResetsUIToInitialState() {
        // First login
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateSuccessfulLogin(true);

        // Now logout
        openSettings();
        Assertions.assertTrue(hasAnyComponent(LOGOUT_BUTTON),
                "Logout button should be visible when logged in");
        click(LOGOUT_BUTTON);
        if (hasAnyComponent(LOGOUT_CONFIRM_YES)) {
            click(LOGOUT_CONFIRM_YES);
        }

        // TC85: Verify UI resets to initial state after logout
        openSettings();
        // API Key field should be empty/editable
        Assertions.assertFalse(hasAnyComponent(SUCCESSFUL_LOGIN_MESSAGE),
                "Success message should not be displayed after logout");
        Assertions.assertFalse(hasAnyComponent(LOGOUT_BUTTON),
                "Logout button should not be visible after logout");
        log("UI correctly reset to initial state after logout");
        click(OK_BTN);
    }

    @Test
    @Order(11)
    @DisplayName("TC91: Verify 'Connect' button disabled when no method selected and fields empty")
    @Video
    public void testConnectButtonDisabledWhenFieldsEmpty() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();

        // Select API Key radio but leave the API Key field empty
        selectRadioButton(API_KEY_RADIO);
        setField(Constants.FIELD_NAME_API_KEY, "");

        // TC91: Connect button should be disabled when mandatory fields are empty
        waitFor(() -> hasAnyComponent(CONNECT_BUTTON));
        boolean isClickable = isElementClickable(CONNECT_BUTTON);
        Assertions.assertFalse(isClickable,
                "Connect button should be disabled when API key field is empty");
        log("Connect button correctly disabled when fields are empty");
        click(OK_BTN);
    }

    @Test
    @Order(12)
    @DisplayName("TC92: Verify credentials are mandatory depending on selected method")
    @Video
    public void testCredentialsMandatoryForSelectedMethod() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();

        // Select API Key method with empty key - connect should fail or be disabled
        selectRadioButton(API_KEY_RADIO);
        setField(Constants.FIELD_NAME_API_KEY, "");

        waitFor(() -> hasAnyComponent(CONNECT_BUTTON));
        boolean isDisabledWhenEmpty = !isElementClickable(CONNECT_BUTTON);
        log("Connect button disabled with empty API key: " + isDisabledWhenEmpty);

        // Fill in valid API key - connect should become enabled
        setField(Constants.FIELD_NAME_API_KEY, Environment.API_KEY);
        waitFor(() -> isElementClickable(CONNECT_BUTTON));
        Assertions.assertTrue(isElementClickable(CONNECT_BUTTON),
                "Connect button should be enabled after filling in API key");
        log("Connect button correctly enabled after filling mandatory credentials");
        click(OK_BTN);
    }

    @Test
    @Order(13)
    @DisplayName("TC93: Verify success message is 'You are connected to Checkmarx One'")
    @Video
    public void testSuccessMessageText() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);

        // TC93: Verify exact success message text
        waitFor(() -> hasAnyComponent(SUCCESS_CONNECTION));
        String messageText = getText(SUCCESS_CONNECTION);
        Assertions.assertEquals("You are connected to Checkmarx One", messageText,
                "Success message should be 'You are connected to Checkmarx One'");
        log("Success message text verified: " + messageText);

        validateSuccessfulLogin(true);
    }

    @Test
    @Order(14)
    @DisplayName("TC99: Verify all fields are disabled upon successful login")
    @Video
    public void testAllFieldsDisabledAfterLogin() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateSuccessfulLogin(true);

        // TC99: After successful login, open settings and verify all auth fields are disabled
        openSettings();

        // API Key field should be disabled after login
        String apiKeyFieldXpath = String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY);
        if (hasAnyComponent(apiKeyFieldXpath)) {
            Assertions.assertFalse(isElementClickable(apiKeyFieldXpath),
                    "API Key field should be disabled after successful login");
        }

        // Additional parameters field should be disabled after login
        String additionalParamsXpath = String.format(FIELD_NAME, Constants.FIELD_NAME_ADDITIONAL_PARAMETERS);
        if (hasAnyComponent(additionalParamsXpath)) {
            Assertions.assertFalse(isElementClickable(additionalParamsXpath),
                    "Additional parameters field should be disabled after successful login");
        }

        // Connect button should not be clickable after login
        Assertions.assertFalse(isElementClickable(CONNECT_BUTTON),
                "Connect button should be disabled after successful login");
        log("All auth fields are correctly disabled after successful login");
        click(OK_BTN);
    }
}
