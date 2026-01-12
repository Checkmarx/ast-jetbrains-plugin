package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.PageMethods.ASCARealTimeScanPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;

public class TestAscaRealTime extends BaseUITest{

    @Test
    @Video
    @DisplayName("Verify ASCA Real-Time Scan is enabled and success message is shown")
    public void testASCACheckBoxEnableSuccessMsg() {
        // Given: User is logged in and welcome page is loaded
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);

        // When: User navigates to CxOne Assist page
        navigateToCxOneAssistPage();

        //When: user disables and enables ASCA Real-Time Scan
        toggleAscaEngineAndVerifySuccess();

        // Then: Success message should be displayed
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
        clickSafe(OK_BTN);
    }

    @Test
    @Video
    @DisplayName("Verify ASCA vulnerability is displayed in the Issues Tree after file scan completion")
    public void testASCAVulnerabilityDisplayedInProblemTree() {
        // Given: User is logged in and welcome page is loaded
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);

        // When: User enables ASCA Real-Time Scan if it is disabled
        navigateToCxOneAssistPage();
        selectEngine(ASCA_ENGINE_SELECTION_CHECKBOX);
        locateAndClickOnButton(OK_BTN);

        // When: User opens a file with ASCA vulnerabilities and edits it to trigger a real-time scan
        openAndEditFileTriggerRealtimeScan();

        // Then: ASCA vulnerability Present file name should be displayed in the Issues Tree
        verifyAscaVulnerabilityFileInIssuesTree();
    }
}