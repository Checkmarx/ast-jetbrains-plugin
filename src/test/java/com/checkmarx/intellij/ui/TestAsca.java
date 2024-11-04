package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.ASCA.AscaService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import org.junit.jupiter.api.Assertions;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.click;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.hasAnyComponent;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.Xpath.CANCEL_SCAN_BTN;

public class TestAsca extends BaseUITest{
    public static void testASTAscaWithValidateConnections(boolean validCredentials) {
        openSettings();

        setField(Constants.FIELD_NAME_API_KEY, validCredentials ? Environment.API_KEY : "invalidAPIKey");
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");


        click(VALIDATE_BUTTON);

        waitFor(() -> !hasAnyComponent(ASCA_INSTALL_SUCCESS));

            Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
            click(OK_BTN);
            // Ensure that start scan button and cancel scan button are visible with valid credentials
            waitFor(() -> {
                focusCxWindow();
                return hasAnyComponent(START_SCAN_BTN) && hasAnyComponent(CANCEL_SCAN_BTN);
            });
    }

}
