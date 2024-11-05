package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.click;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.hasAnyComponent;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class TestAsca extends BaseUITest {
    @Test
    @Video
    public void testClickAscaCheckbox() {
        // Open the settings window
        openSettings();

        // Log the presence of the ASCA checkbox
        log("Checking for the presence of the ASCA checkbox");

        // Wait for the ASCA checkbox to be present
        waitFor(() -> hasAnyComponent(ASCA_CHECKBOX));

        // Click the ASCA checkbox
        click(ASCA_CHECKBOX);

        // Wait for the ASCA installation message to appear
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));

        // Verify that the ASCA installation message is displayed
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }
}