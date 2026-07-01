package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.ASCARealTimeScanPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

public class TestAscaRealTime extends com.checkmarx.intellij.ast.test.ui.BaseUITest {

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
    @Video
    @DisplayName("Verify ASCA Real-Time Scan is enabled and success message is shown")
    public void testASCACheckBoxEnableSuccessMsg() {
        // Given: User is logged in and welcome page is loade
        openSettings();

        // When: User navigates to CxOne Assist page
        navigateToCxOneAssistPage();

        //Then: Validate success message when ASCA Real-Time Scan is enabled
        toggleAscaEngineAndVerifySuccessMessage();
    }

    @Test
    @Video
    @DisplayName("Verify ASCA vulnerability is displayed in the Issues Tree after file scan completion")
    public void testASCAVulnerabilityDisplayedInProblemTree() {
        // Given: User is logged in and welcome page is loaded
        openSettings();

        // When: User enables ASCA Real-Time Scan if it is disabled
        navigateToCxOneAssistPage();
        selectEngine(ASCA_ENGINE_SELECTION_CHECKBOX, true);
        locateAndClickOnButton(OK_BTN);

        // When: User opens a file with ASCA vulnerabilities and edits it to trigger a real-time scan
        openAndEditFileTriggerRealtimeScan();

        // Then: Verify scan started within ~2 seconds of file edit (TC75)
        verifyScanStartedWithinExpectedDelay();

        // Then: ASCA vulnerability file name should be displayed in the Issues Tree
        verifyAscaVulnerabilityFileInIssuesTree();
    }
}