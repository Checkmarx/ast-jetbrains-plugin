package com.checkmarx.intellij.ui.PageMethods;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.integration.Environment;
import org.junit.jupiter.api.Assertions;

import static com.checkmarx.intellij.ui.BaseUITest.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.click;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.hasAnyComponent;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;


public class CheckmarxSettingsPage {

    public static void openSettings() {
        log("unning openSettings()");
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
        if (hasAnyComponent(LOGOUT_BUTTON)) {
            log("Detected previous authentication. Logging out.");
            click(LOGOUT_BUTTON);

            if (hasAnyComponent(LOGOUT_CONFIRM_YES)) {
                click(LOGOUT_CONFIRM_YES);
            }

            waitFor(() -> hasAnyComponent(API_KEY_RADIO));
        }

        //Select API Key radio
        waitFor(() -> hasAnyComponent(API_KEY_RADIO));
        find(API_KEY_RADIO).click();

        // Set API key
        String apiKey = validCredentials ? Environment.API_KEY : "invalid-api-key";
        setField(Constants.FIELD_NAME_API_KEY, apiKey);

        // Set additional parameter
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");

        // Attempt connection
        click(CONNECT_BUTTON);
        waitFor(() -> !hasAnyComponent(VALIDATING_CONNECTION));

        // Expect success or expect failure
        if (validCredentials) {
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
}
