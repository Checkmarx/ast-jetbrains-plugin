package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.time.Instant;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistFindingsTabPage.checkIfVulnerableFileExists;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.CxOneAssistFindingsTabPage.isVulnerableFilePresentInCxAssistTree;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.hasAnyComponent;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

public class ASCARealTimeScanPage {

    private static final Duration SCAN_START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration SCAN_COMPLETE_TIMEOUT = Duration.ofSeconds(120);
    private static final long EXPECTED_SCAN_START_DELAY_MS = 5000; // 5 seconds tolerance for the ~2 second trigger
    private static final String ASSIGNMENT_FILE = "Assignment5.java";
    private static long lastScanStartDelayMs = -1;

    public static void openAndEditFileTriggerRealtimeScan() {
        // Wait for existing scans to complete
        waitFor(() -> !hasAnyComponent(SCAN_PROGRESS_BAR));
        hideToolWindows();
        // Open a test file with ASCA vulnerabilities
        openFileByPath("src/main/java/org/owasp/webgoat/challenges/challenge5/Assignment5.java");

        // Try to trigger scan and wait for results, retrying the edit if needed
        boolean scanCompleted = waitForScanWithRetry();

        if (scanCompleted) {
            log("ASCA real-time scan completed successfully");
        } else {
            log("ASCA scan did not complete, but vulnerability may already exist in Issues Tree");
        }

        // Reopen the checkmarx tool window as we closed all tool windows earlier
        openCxToolWindow();
    }

    /**
     * Edits the file to trigger a scan, then waits for the scan progress bar to appear and disappear,
     * or for the vulnerability to appear in the findings tree.
     * Retries the edit up to 3 times if the scan doesn't start.
     *
     * @return true if scan completed, false if vulnerability was found without observing the scan
     */
    private static boolean waitForScanWithRetry() {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log("Triggering file edit to start ASCA scan (attempt " + attempt + "/" + maxRetries + ")...");
            Instant editTime = Instant.now();
            editFile();

            // Wait for either the scan progress bar OR the vulnerability in the findings tree
            try {
                waitFor(() -> hasAnyComponent(FILE_SCAN_PROGRESS_BAR) || isVulnerabilityAlreadyInTree(),
                        SCAN_START_TIMEOUT);
            } catch (WaitForConditionTimeoutException e) {
                log("Neither scan progress bar nor vulnerability detected within " + SCAN_START_TIMEOUT.getSeconds() + "s");
                if (attempt == maxRetries) {
                    throw e;
                }
                continue;
            }

            // Track how long it took for the scan to start after the file edit
            lastScanStartDelayMs = Duration.between(editTime, Instant.now()).toMillis();
            log("ASCA scan started " + lastScanStartDelayMs + "ms after file edit");

            // Check what we found
            if (isVulnerabilityAlreadyInTree()) {
                log("Vulnerability already present in findings tree — scan may have completed quickly");
                return false;
            }

            // Progress bar appeared — wait for scan to complete
            log("ASCA scan progress bar detected, waiting for scan to complete...");
            waitFor(() -> !hasAnyComponent(FILE_SCAN_PROGRESS_BAR), SCAN_COMPLETE_TIMEOUT);
            return true;
        }

        return false;
    }

    /**
     * Verifies that the ASCA scan started within the expected delay after a file edit.
     * TC75: ASCA should trigger a scan approximately 2 seconds after a file edit.
     */
    public static void verifyScanStartedWithinExpectedDelay() {
        Assertions.assertTrue(lastScanStartDelayMs >= 0,
                "Scan start delay was not recorded — scan may not have been triggered");
        log("Verifying scan start delay: " + lastScanStartDelayMs + "ms (threshold: " + EXPECTED_SCAN_START_DELAY_MS + "ms)");
        Assertions.assertTrue(lastScanStartDelayMs <= EXPECTED_SCAN_START_DELAY_MS,
                "ASCA scan should start within " + EXPECTED_SCAN_START_DELAY_MS + "ms of file edit, but took " + lastScanStartDelayMs + "ms");
    }

    /**
     * Quick non-blocking check if the vulnerability file is already in the CxAssist findings tree.
     */
    private static boolean isVulnerabilityAlreadyInTree() {
        try {
            openCxToolWindow();
            if (!hasAnyComponent(CX_ASSIST_FINDING_TAB)) return false;
            return checkIfVulnerableFileExists(ASSIGNMENT_FILE);
        } catch (Exception e) {
            return false;
        }
    }

    public static void verifyAscaVulnerabilityFileInIssuesTree() {
        // Open checkmarx one assist findings tab
        openCxToolWindow();
        clickSafe(CX_ASSIST_FINDING_TAB);
        // Verify ASCA vulnerability is displayed in the Issues Tree (60s timeout — results should be there already)
        isVulnerableFilePresentInCxAssistTree(ASSIGNMENT_FILE, Duration.ofSeconds(60));
        // Move back to Scan Results tab to avoid interference with other tests
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
