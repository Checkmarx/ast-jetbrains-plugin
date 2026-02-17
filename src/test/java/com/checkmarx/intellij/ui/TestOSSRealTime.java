package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.navigateToCxOneAssistPage;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.selectEngine;
import static com.checkmarx.intellij.ui.PageMethods.OSSRealTimeScanPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.BaseUITest.*;
public class TestOSSRealTime extends BaseUITest {

    @Test
    @Video
    @DisplayName("Verify OSS Real-Time Scan is enabled and start scanning as soon as user login")
    public void testAutoStartOssScanOnLoginWhenEnabled() {
        // Given: User is logged in and welcome page is loaded
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);

        // When: User enables OSS Real-Time Scan if it is disabled
        navigateToCxOneAssistPage();
        selectEngine(OSS_REALTIME_ENGINE_CHECKBOX, true);
        locateAndClickOnButton(OK_BTN);

        // Then: OSS Real-Time Scan should start automatically after login
        waitFor(() -> hasAnyComponent(SCAN_PROGRESS_BAR));
        Assertions.assertTrue(hasAnyComponent(SCAN_PROGRESS_BAR));
    }

    @Test
    @Video
    @DisplayName("Verify OSS vulnerabilities are displayed in the Issues Tree after scan completion")
    public void testDisplayOssFindingsAfterScanCompletion() {
        // Given: User is logged in and welcome page is loaded
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);

        // When: User enables OSS Real-Time Scan if it is disabled
        navigateToCxOneAssistPage();
        selectEngine(OSS_REALTIME_ENGINE_CHECKBOX,true);
        locateAndClickOnButton(OK_BTN);

        // When: Wait for OSS scan to complete
        waitFor(() -> !hasAnyComponent(SCAN_PROGRESS_BAR));

        // When: open Cx One Assist Findings tab
        clickSafe(CX_ASSIST_FINDING_TAB);

        // Then: Issues tree should not be empty and OSS vulnerabilities files are displayed in the Issues Tree
        verifyOssFindingsTreeNotEmpty();
        //Move back to Scan Results tab to avoid interference with other tests
        clickSafe(SCAN_RESULTS_TAB);
    }
}
