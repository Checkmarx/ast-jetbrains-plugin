package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.ast.test.integration.Environment;
import org.junit.jupiter.api.Assertions;

import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.TestConstants.CX_BASE_URI;
import static com.checkmarx.intellij.ast.test.ui.utils.TestConstants.TENANT;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ast.test.ui.BaseUITest.focusCxWindow;


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

    /**
     * Closes the Settings dialog via OK, falling back to Cancel if OK isn't present.
     * Used to guarantee the dialog is dismissed even when a test's assertions fail
     * partway through (e.g. engine checkboxes not selected as expected), so the
     * dialog doesn't stay open and block the next test's {@link #openSettings()} call.
     */
    public static void closeSettingsDialogIfOpen() {
        if (hasAnyComponent(OK_BTN)) {
            locateAndClickOnButton(OK_BTN);
        } else if (hasAnyComponent(CANCEL_BTN)) {
            locateAndClickOnButton(CANCEL_BTN);
        }
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

            // After logout, the settings dialog closes, so we need to reopen it
            log("Reopening settings after logout.");
            openSettings();
        }
    }

    /**
     * TC86: Verifies that after logout, the Base URI and API Key fields still show
     * their previously-entered values. Logout clears the session, not the form state,
     * so the user shouldn't have to retype credentials to log back in.
     * Assumes settings are already open (e.g. right after {@link #logoutIfUserIsAlreadyLoggedIn()}).
     */
    public static void verifyCredentialsRetainedAfterLogout() {
        String baseUrlXpath = String.format(FIELD_NAME, CX_BASE_URI);
        String apiKeyXpath = String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY);

        Assertions.assertFalse(isFieldEmpty(baseUrlXpath),
                "Base URI field should still show the previously entered value after logout");
        Assertions.assertFalse(isFieldEmpty(apiKeyXpath),
                "API Key field should still show the previously entered value after logout");
    }

    private static boolean isFieldEmpty(String fieldXpath) {
        String value = getText(fieldXpath);
        return value == null || value.isEmpty();
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
            assertElementAvailableAfterLogin(START_SCAN_BTN, "Start Scan");
            assertElementAvailableAfterLogin(CANCEL_SCAN_BTN, "Cancel Scan");
        } else {
            Assertions.assertFalse(hasAnyComponent(SUCCESS_CONNECTION));
            click(OK_BTN);
            waitFor(() -> !hasAnyComponent(START_SCAN_BTN) && !hasAnyComponent(CANCEL_SCAN_BTN));
        }
    }

    public static void validateWelcomePageLoadedSuccessfully(boolean isCodeSmartSelectedByDefault) {
        waitFor(() -> hasAnyComponent(WELCOME_TITLE)
                && hasAnyComponent(WELCOME_ASSIST_TITLE)
                && hasAnyComponent(CODE_SMART_CHECKBOX)
                && hasAnyComponent(WELCOME_PAGE_IMAGE));
        String welcomeTitle = getText(WELCOME_TITLE);
        Assertions.assertEquals("Welcome to Checkmarx", welcomeTitle);

        // Ensure the Code Smart with Checkmarx One Assist checkbox reflects the expected default,
        // selecting it if it should be on by default but isn't.
        if (isCodeSmartSelectedByDefault && !isComponentSelected(CODE_SMART_CHECKBOX)) {
            click(CODE_SMART_CHECKBOX);
        }

        // Wait for the checkbox selection state to match the expected default
        waitFor(() -> isComponentSelected(CODE_SMART_CHECKBOX) == isCodeSmartSelectedByDefault);
        boolean checkBoxSelected = isComponentSelected(CODE_SMART_CHECKBOX);
        if (isCodeSmartSelectedByDefault) {
            Assertions.assertTrue(checkBoxSelected);
        } else {
            Assertions.assertFalse(checkBoxSelected);
        }
    }


    public static void testASTSettingsPageTitlePresent() {
        openSettings();

        waitFor(() -> hasAnyComponent(HELP_PLUGIN_LINK));
        String titleText = getText(HELP_PLUGIN_LINK);
        Assertions.assertTrue(find(HELP_PLUGIN_LINK).isShowing(),
                "Help link is not visible on screen");
        Assertions.assertEquals(
                "Checkmarx One Jetbrains Plugin help page",
                titleText,
                "Settings page title text did not match"
        );
        Assertions.assertTrue(hasAnyComponent(HELP_PLUGIN_LINK),
                "Expected Checkmarx One Jetbrains Plugin help page label to be visible");
        click(OK_BTN);
    }

    /**
     * Opens settings, ensures a logged-out state, selects OAuth, fills in the
     * Base URI/Tenant fields and clicks Connect. Shared setup reused by all OAuth flows.
     */
    private static void openOAuthConnectDialog(String baseUrl, String tenant) {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        ensureOAuthSelected();

        setField(CX_BASE_URI, baseUrl);
        setField(TENANT, tenant);

        clickConnect();
    }

    public static void clickConnect() {
        waitFor(() -> hasAnyComponent(CONNECT_BUTTON));
        click(CONNECT_BUTTON);
    }

    public static void testASTOAuthRadioButton(boolean expectSuccess) {
        openOAuthConnectDialog(Environment.BASE_URL, Environment.TENANT);

        waitFor(() -> hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        click(OAUTH_POPUP_CANCEL_BUTTON);
        click(OK_BTN);

    }

    public static void verifyOAuthConfirmationPopupVisible() {
        openOAuthConnectDialog(Environment.BASE_URL, Environment.TENANT);

        // RemoteRobot detects the confirmation popup via its Cancel button
        waitFor(() -> hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        Assertions.assertTrue(hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON),
                "OAuth confirmation popup should be visible after clicking Connect");

        // Click Cancel and verify the popup closes
        click(OAUTH_POPUP_CANCEL_BUTTON);
        waitFor(() -> !hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        Assertions.assertFalse(hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON),
                "OAuth confirmation popup should be closed after clicking Cancel");

        click(OK_BTN);
    }

    /**
     * TC95: Verifies the OAuth confirmation popup exposes a button for each expected
     * label (e.g. "Continue", "Cancel"), then dismisses it via Cancel.
     * RemoteRobot detects the popup via its Cancel button, same as TC87.
     */
    public static void verifyPopupHasButtons(String... expectedButtonTexts) {
        waitFor(() -> hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        Assertions.assertTrue(hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON),
                "OAuth confirmation popup should be visible after clicking Connect");

        for (String buttonText : expectedButtonTexts) {
            String buttonXpath = String.format(OAUTH_POPUP_BUTTON, buttonText);
            Assertions.assertTrue(hasAnyComponent(buttonXpath),
                    "OAuth popup should contain a '" + buttonText + "' button");
        }

        click(OAUTH_POPUP_CANCEL_BUTTON);
        waitFor(() -> !hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        click(OK_BTN);
    }

    public static void verifyOAuthPopupHasContinueCancelButtons() {
        openOAuthConnectDialog(Environment.BASE_URL, Environment.TENANT);
        verifyPopupHasButtons("Continue", "Cancel");
    }

    /**
     * TC97: Verifies that clicking Cancel on the OAuth confirmation popup dismisses
     * only the popup, leaving the underlying settings dialog open and interactable.
     */
    public static void verifySettingsPageVisibleAfterPopupCancel() {
        openOAuthConnectDialog(Environment.BASE_URL, Environment.TENANT);
        clickCancelOnPopup();
        verifySettingsPageStillVisible();
    }

    public static void clickCancelOnPopup() {
        waitFor(() -> hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
        click(OAUTH_POPUP_CANCEL_BUTTON);
        waitFor(() -> !hasAnyComponent(OAUTH_POPUP_CANCEL_BUTTON));
    }

    public static void verifySettingsPageStillVisible() {
        Assertions.assertTrue(hasAnyComponent(CONNECT_BUTTON),
                "Settings dialog should remain open and interactable after dismissing the OAuth popup");
        click(OK_BTN);
    }

    private static void ensureOAuthSelected() {
        waitFor(() -> hasAnyComponent(OAUTH_RADIO));
        find(OAUTH_RADIO).click();
    }

    /**
     * TC89: Switches the login method to OAuth, waiting for the OAuth credential
     * fields (Base URI/Tenant) to become the active form.
     */
    public static void switchToOAuth() {
        ensureOAuthSelected();
        waitFor(() -> isElementClickable(String.format(FIELD_NAME, CX_BASE_URI))
                && isElementClickable(String.format(FIELD_NAME, TENANT)));
    }

    /**
     * TC89: Switches the login method to API Key, waiting for the API Key
     * credential field to become the active form.
     */
    public static void switchToApiKey() {
        selectRadioButton(API_KEY_RADIO);
        waitFor(() -> isElementClickable(String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY)));
    }

    public static void testASTOAuthInvalidInput(
            String baseUrl,
            String tenant,
            String expectedErrorXpath,
            String expectedErrorMessage,
            String scenarioName
    ) {
        log("Executing OAuth negative test: " + scenarioName);

        openOAuthConnectDialog(baseUrl, tenant);

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

}
