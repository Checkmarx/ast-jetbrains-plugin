package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;

import static com.checkmarx.intellij.ast.test.ui.BaseUITest.focusCxWindow;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistFindingsTabPage.checkIfVulnerableFileExists;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistFindingsTabPage.isVulnerableFilePresentInCxAssistTree;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.hasAnyComponent;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

public class ASCARealTimeScanPage {

    private static final Duration ASCA_SCAN_TIMEOUT = Duration.ofSeconds(400);
    private static final String ASSIGNMENT_FILE = "Assignment5.java";

    public static void openAndEditFileTriggerRealtimeScan() {
        // Implementation for opening and editing a file to trigger ASCA Real-Time Scan
        //Wait for existing scans to complete
        waitFor(() -> !hasAnyComponent(SCAN_PROGRESS_BAR));
        hideToolWindows();
        //Open a test file with ASCA vulnerabilities
        openFileByPath("src/main/java/org/owasp/webgoat/challenges/challenge5/Assignment5.java");
        editFile();

        // Wait for scan to start with custom 400 second timeout
        // If it times out, check if vulnerability is already in tree before retrying
        boolean scanStarted = waitForScanToStartWithFallback();

        if (scanStarted) {
            // Wait for scan to complete with custom 400 second timeout
            waitFor(() -> !hasAnyComponent(FILE_SCAN_PROGRESS_BAR), ASCA_SCAN_TIMEOUT);
        } else {
            log("Scan progress bar never appeared, but vulnerability was found in Issues Tree");
        }

        //Reopen the checkmarx tool window as we are closing all the tool windows earlier
        openCxToolWindow();
    }

    /**
     * Waits for the ASCA scan to start (progress bar appears) with retry logic.
     * After first timeout, checks if vulnerability already exists in tree before retrying.
     *
     * @return true if scan started, false if vulnerability was found without scan progress
     */
    private static boolean waitForScanToStartWithFallback() {
        int maxRetries = 3;
        int currentTry = 0;

        while (currentTry < maxRetries) {
            try {
                log("Waiting for ASCA scan to start (attempt " + (currentTry + 1) + "/" + maxRetries + ")...");
                waitFor(() -> hasAnyComponent(FILE_SCAN_PROGRESS_BAR), ASCA_SCAN_TIMEOUT);
                log("ASCA scan progress bar detected");
                return true;
            } catch (WaitForConditionTimeoutException e) {
                currentTry++;
                log("Scan progress bar not detected within " + ASCA_SCAN_TIMEOUT.getSeconds() + " seconds");

                // Before retrying, check if the vulnerability is already in the Issues Tree
                log("Checking if ASCA vulnerability already exists in Issues Tree...");
                openCxToolWindow();
                clickSafe(CX_ASSIST_FINDING_TAB);

                boolean fileExists = checkIfVulnerableFileExists(ASSIGNMENT_FILE);

                if (fileExists) {
                    log("ASCA vulnerability file '" + ASSIGNMENT_FILE + "' found in Issues Tree. Scan may have completed without progress bar.");
                    // Switch back to Scan Results tab
                    clickSafe(SCAN_RESULTS_TAB);
                    return false; // Scan completed, no need to wait for progress bar
                } else {
                    log("Vulnerability not found in Issues Tree yet.");
                    // Switch back to Scan Results tab
                    clickSafe(SCAN_RESULTS_TAB);
                }

                if (currentTry < maxRetries) {
                    log("Retrying wait for scan progress bar...");
                    focusCxWindow();
                } else {
                    log("Maximum retries reached. Scan progress bar never appeared and vulnerability not found.");
                    throw e; // Re-throw the exception after all retries exhausted
                }
            }
        }

        return false;
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

    public static void toggleAscaEngineAndVerifySuccessMessage() {
        // Implementation for toggling ASCA engine and verifying success message
        //If ASCA Real-Time Scan is already enabled, uncheck and re-check to verify success message
        boolean checkboxState = isComponentSelected(ASCA_ENGINE_SELECTION_CHECKBOX);
        if(checkboxState) {
            locateAndClickOnButton(ASCA_ENGINE_SELECTION_CHECKBOX);
            locateAndClickOnButton(ASCA_ENGINE_SELECTION_CHECKBOX);
        }else {
            locateAndClickOnButton(ASCA_ENGINE_SELECTION_CHECKBOX);
        }
        //Verify success message is displayed
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
        locateAndClickOnButton(OK_BTN);
    }
}
