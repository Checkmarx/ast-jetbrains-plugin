package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.BaseUITest.*;
import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistFindingsTabPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;

public class TestAscaRealTime {

    @Test
    @Video
    @DisplayName("Verify ASCA Real-Time Scan is enabled and success message is shown")
    public void testASCACheckBoxEnableSuccessMsg() {
        //To verify ASCA Real-Time Scan is enabled and success message is shown
        //open settings page
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateWelcomePageLoadedSuccessfully(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);
        //Navigate to ASCA Settings tab
        navigateToCxOneAssistPage();
        //If ASCA Real-Time Scan is already enabled, uncheck and re-check to verify success message
        if (isCheckboxSelected(ASCA_ENGINE_SELECTION_CHECKBOX))
            clickSafe(ASCA_ENGINE_SELECTION_CHECKBOX);
        clickSafe(ASCA_ENGINE_SELECTION_CHECKBOX);
        //verify success message is shown
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
        clickSafe(OK_BTN);
    }

    @Test
    @Video
    @DisplayName("Verify ASCA vulnerability is displayed in the Issues Tree after file scan completion")
    public void testASCAVulnerabilityDisplayedInProblemTree() {
        //To verify ASCA vulnerability is displayed in the Issues Tree
        enableRealTimeScanIfDisabled(ASCA_ENGINE_SELECTION_CHECKBOX);
        //Wait for existing OSS scans to complete
        waitFor(() -> !hasAnyComponent(SCAN_PROGRESS_BAR));
        hideToolWindows();
        //Open a test file with ASCA vulnerabilities
        openFileByPath("src/main/java/org/owasp/webgoat/challenges/challenge5/Assignment5.java");
        editFile();
        //Wait for scan to complete
        waitFor(() -> hasAnyComponent(FILE_SCAN_PROGRESS_BAR));
        waitFor(() -> !hasAnyComponent(FILE_SCAN_PROGRESS_BAR));
        openCxToolWindow();
        //Open checkmarx one assist findings tab
        clickSafe(CX_ASSIST_FINDING_TAB);
        //Verify ASCA vulnerability is displayed in the Issues Tree
        isVulnerableFilePresentInCxAssistTree("Assignment5.java");
        //Move back to Scan Results tab to avoid interference with other tests
        clickSafe(SCAN_RESULTS_TAB);
    }
}