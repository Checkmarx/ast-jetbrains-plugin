package com.checkmarx.intellij.ui.PageMethods;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.integration.Environment;
import org.junit.jupiter.api.Assertions;

import static com.checkmarx.intellij.ui.BaseUITest.focusCxWindow;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.TestConstants.*;


public class CheckmarxSettingsPage {

    public static void openSettings() {
        waitFor(() -> {
            focusCxWindow();
            if (hasAnyComponent(SETTINGS_ACTION)) {
                click(SETTINGS_ACTION);
            } else if (hasAnyComponent(SETTINGS_BUTTON)) {
                click(SETTINGS_BUTTON);
            }
            return hasAnyComponent(String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY));
        });
    }

    public static void testASTConnection(boolean validCredentials) {
        openSettings();

        //Logout if already authenticated
        logoutIfUserIsAlreadyLoggedIn();

        //Perform login using API key
        performLoginUsingApiKey(validCredentials);

        // Expect success or expect failure
        validateSuccessfulLogin(validCredentials);
    }

    public static void logoutIfUserIsAlreadyLoggedIn(){
        //Logout if already authenticated
        if (hasAnyComponent(LOGOUT_BUTTON)) {
            log("Detected previous authentication. Logging out.");
            click(LOGOUT_BUTTON);

            if (hasAnyComponent(LOGOUT_CONFIRM_YES)) {
                click(LOGOUT_CONFIRM_YES);
            }

            waitFor(() -> hasAnyComponent(API_KEY_RADIO));
        }
    }

    public static void performLoginUsingApiKey(boolean isValidCredential) {
        //Select API Key radio
        selectRadioButton(API_KEY_RADIO);

        // Set API key
        String apiKey = isValidCredential ? Environment.API_KEY : "invalid-api-key";
        setField(Constants.FIELD_NAME_API_KEY, apiKey);

        // Set additional parameter
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");

        // Attempt connection
        click(CONNECT_BUTTON);
        waitFor(() -> !hasAnyComponent(VALIDATING_CONNECTION));


    }

    public static void validateSuccessfulLogin(boolean isValidCredential) {
        // Expect success or expect failure
        if (isValidCredential) {
            Assertions.assertTrue(hasAnyComponent(SUCCESS_CONNECTION));
            locateAndClickOnButton(WELCOME_CLOSE_BUTTON);
            click(OK_BTN);
            waitFor(() -> hasAnyComponent(START_SCAN_BTN) && hasAnyComponent(CANCEL_SCAN_BTN));
        } else {
            Assertions.assertFalse(hasAnyComponent(SUCCESS_CONNECTION));
            click(OK_BTN);
            waitFor(() -> !hasAnyComponent(START_SCAN_BTN) && !hasAnyComponent(CANCEL_SCAN_BTN));
        }
    }

    public static void validateWelcomePageLoadedSuccessfully(boolean isCodeSmartSelectedByDefault) {
        waitFor(() -> hasAnyComponent(WELCOME_TITLE));
        hasAnyComponent(WELCOME_ASSIST_TITLE);
        hasAnyComponent(CODE_SMART_CHECKBOX);
        hasAnyComponent(WELCOME_PAGE_IMAGE);
        String welcomeTitle = getText(WELCOME_TITLE);
        Assertions.assertEquals("Welcome to Checkmarx", welcomeTitle);
        boolean checkBoxSelected = isCheckboxSelected(CODE_SMART_CHECKBOX);
        if (isCodeSmartSelectedByDefault) {
            Assertions.assertTrue(checkBoxSelected);
        } else {
            Assertions.assertFalse(checkBoxSelected);
        }
    }


    public static void testASTSettingsPageTittlePresent() {
        openSettings();

        waitFor(() -> hasAnyComponent(HELP_PLUGIN_LINK));
        String tittleText = getText(HELP_PLUGIN_LINK);
        Assertions.assertTrue(find(HELP_PLUGIN_LINK).isShowing(),
                "Help link is not visible on screen");
        Assertions.assertEquals(
                "Checkmarx One Jetbrains Plugin help page",
                tittleText,
                "Settings page title text did not match"
        );
        Assertions.assertTrue(hasAnyComponent(HELP_PLUGIN_LINK),
                "Expected Checkmarx One Jetbrains Plugin help page label to be visible");
        click(OK_BTN);
    }

    public static void testASTOAuthRadioButton(boolean expectSuccess) {
        openSettings();
        logoutIfAuthenticated();
        ensureOAuthSelected();

        setField(CX_BASE_URI, Environment.BASE_URL);
        setField(TENANT, Environment.TENANT);

        // Attempt connection
        waitFor(() -> hasAnyComponent(CONNECT_BUTTON));
        click(CONNECT_BUTTON);

        waitFor(() -> hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        click(OAUTH_POPUP_CANCEL_BUTTON);
        click(OK_BTN);

    }

    private static void ensureOAuthSelected() {
        waitFor(() -> hasAnyComponent(OAUTH_RADIO));
        find(OAUTH_RADIO).click();
    }

    public static void testASTOAuthInvalidInput(
            String baseUrl,
            String tenant,
            String expectedErrorXpath,
            String expectedErrorMessage,
            String scenarioName
    ) {
        log("Executing OAuth negative test: " + scenarioName);

        openSettings();
        logoutIfAuthenticated();
        ensureOAuthSelected();

        setField(CX_BASE_URI, baseUrl);
        setField(TENANT, tenant);

        click(CONNECT_BUTTON);

        // Wait for error to appear
        waitFor(() -> hasAnyComponent(expectedErrorXpath));

        // Assert error is visible
        Assertions.assertTrue(
                hasAnyComponent(expectedErrorXpath),
                "Expected error not shown for scenario: " + scenarioName
        );

        // Assert error message text
        String actualErrorMessage = getText(expectedErrorXpath);
        Assertions.assertTrue(
                actualErrorMessage.contains(expectedErrorMessage),
                "Error message mismatch for scenario: " + scenarioName +
                        "\nActual: " + actualErrorMessage
        );

        click(OK_BTN);
    }


    private static void logoutIfAuthenticated() {
        if (hasAnyComponent(LOGOUT_BUTTON)) {
            log("Detected previous authentication. Logging out.");
            click(LOGOUT_BUTTON);

            if (hasAnyComponent(LOGOUT_CONFIRM_YES)) {
                click(LOGOUT_CONFIRM_YES);
            }

            waitFor(() -> hasAnyComponent(OAUTH_RADIO));
        }
    }
}
