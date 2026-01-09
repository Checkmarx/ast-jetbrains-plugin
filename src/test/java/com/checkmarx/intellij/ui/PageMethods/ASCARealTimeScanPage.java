package com.checkmarx.intellij.ui.PageMethods;

import static com.checkmarx.intellij.ui.BaseUITest.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistFindingsTabPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class ASCARealTimeScanPage {

    public static void openAndEditFileTriggerRealtimeScan() {
        // Implementation for opening and editing a file to trigger ASCA Real-Time Scan
        //Wait for existing scans to complete
        waitFor(() -> !hasAnyComponent(SCAN_PROGRESS_BAR));
        hideToolWindows();
        //Open a test file with ASCA vulnerabilities
        openFileByPath("src/main/java/org/owasp/webgoat/challenges/challenge5/Assignment5.java");
        editFile();
        //Wait for scan to complete
        waitFor(() -> hasAnyComponent(FILE_SCAN_PROGRESS_BAR));
        waitFor(() -> !hasAnyComponent(FILE_SCAN_PROGRESS_BAR));
        openCxToolWindow();
    }

    public static void verifyAscaVulnerabilityFileInIssuesTree() {
        // Implementation for verifying ASCA vulnerability file in Cx Findings Issues Tree
        //Open checkmarx one assist findings tab
        clickSafe(CX_ASSIST_FINDING_TAB);
        //Verify ASCA vulnerability is displayed in the Issues Tree
        isVulnerableFilePresentInCxAssistTree("Assignment5.java");
        //Move back to Scan Results tab to avoid interference with other tests
        clickSafe(SCAN_RESULTS_TAB);
    }

    public static void toggleAscaEngineAndVerifySuccess() {
        // Implementation for toggling ASCA engine and verifying success message
        //If ASCA Real-Time Scan is already enabled, uncheck and re-check to verify success message
        if (isCheckboxSelected(ASCA_ENGINE_SELECTION_CHECKBOX))
            clickSafe(ASCA_ENGINE_SELECTION_CHECKBOX);
        clickSafe(ASCA_ENGINE_SELECTION_CHECKBOX);
    }
}
