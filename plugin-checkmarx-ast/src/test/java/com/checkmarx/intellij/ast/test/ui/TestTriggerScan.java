package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.intellij.remoterobot.fixtures.ActionButtonFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.resetProjectSelection;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.find;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.hasAnyComponent;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestTriggerScan extends com.checkmarx.intellij.ast.test.ui.BaseUITest {

    @BeforeEach
    public void checkResults(TestInfo info) {
        if (info.getTags().contains("skip-check-results")) {
            return;
        }

        getResults();
    }

    @Test
    @Video
    @Order(1)
    @Tag("skip-check-results")
    @DisplayName("Scan buttons disabled when no project or branch is selected")
    public void testScanButtonsDisabledWhenMissingProjectOrBranch() {
        if (triggerScanNotAllowed()) return;

        resetProjectSelection(0);
        Assertions.assertFalse(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
        Assertions.assertFalse(find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
    }

    @Test
    @Video
    @Order(2)
    @DisplayName("Cancel a running scan and verify Start Scan is re-enabled")
    public void testCancelScan() {
        if (triggerScanNotAllowed()) return;

        waitForScanIdSelection();
        findRunScanButtonAndClick();
        waitFor(() -> find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
        find(CANCEL_SCAN_BTN).click();

        waitFor(() -> find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
        Assertions.assertTrue(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
    }

    @Test
    @Video
    @Order(3)
    @Tag("skip-check-results")
    @DisplayName("Project mismatch detected when scanning with a different organization")
    public void testTriggerScanProjectWithDifferentOrganizationsDontMatch() {
        waitForAllSelections();
        testSelectionAction(findSelection("Project"), "Project", "DiffOrg/WebGoat");
        testSelectionAction(findSelection("Branch"), "Branch", Environment.BRANCH_NAME);
        testSelectionAction(findSelection("Scan"), "Scan", Environment.SCAN_ID_NOT_MATCH_PROJECT);
        waitForAllSelections();
        clickStartScanButton();
        waitFor(() -> hasAnyComponent(PROJECT_DOES_NOT_MATCH), Duration.ofSeconds(15));
        Assertions.assertTrue(hasAnyComponent(PROJECT_DOES_NOT_MATCH));
    }

    @Test
    @Video
    @Order(4)
    @DisplayName("Trigger a local scan and load the new results")
    public void testTriggerScanAndLoadResults() {
        if (triggerScanNotAllowed()) return;

        waitForScanIdSelection();
        findRunScanButtonAndClick();
        // Wait for the scan to finish (local scan takes 2-3 minutes)
        waitFor(() -> hasAnyComponent(SCAN_FINISHED) || hasAnyComponent(LOAD_RESULTS));
        // Verify the load results link appeared and click it
        waitFor(() -> hasAnyComponent(LOAD_RESULTS));
        clickSafe(LOAD_RESULTS);
        // Verify tree updates with new scan results (different scan ID than the original)
        waitFor(() -> {
            JTreeFixture treeAfterScan = find(JTreeFixture.class, TREE);
            return !treeAfterScan.findAllText().isEmpty()
                    && treeAfterScan.getValueAtRow(0).startsWith("Scan")
                    && !treeAfterScan.getValueAtRow(0).contains(Environment.SCAN_ID);
        });
    }

    private void findRunScanButtonAndClick() {
        ActionButtonFixture runScanBtn = find(ActionButtonFixture.class, START_SCAN_BTN);
        waitFor(runScanBtn::isEnabled);
        runScanBtn.click();
        // Handle optional popup (appears locally, not in pipeline)
        if (hasProjectDoesNotMatchPopup()) {
            log("Project mismatch popup detected. Clicking Run Local.");
            if (hasAnyComponent(RUN_SCAN_LOCAL)) {
                clickSafe(RUN_SCAN_LOCAL, Duration.ofSeconds(15));
            } else {
                log("Run Local link not found in popup. Skipping.");
            }
        }
        else {
            log("No project mismatch popup detected. Continuing.");
        }
    }

    private void waitForAllSelections() {
        waitFor(() -> findSelection("Scan").isEnabled()
                && findSelection("Project").isEnabled()
                && findSelection("Branch").isEnabled());
    }

    private void clickStartScanButton() {
        ActionButtonFixture runScanBtn = find(ActionButtonFixture.class, START_SCAN_BTN);
        waitFor(runScanBtn::isEnabled);
        runScanBtn.click();
    }

    private boolean triggerScanNotAllowed() {
        return !hasAnyComponent(START_SCAN_BTN);
    }

    private boolean hasProjectDoesNotMatchPopup() {
        try {
            waitFor(() -> hasAnyComponent(PROJECT_DOES_NOT_MATCH), Duration.ofSeconds(15));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
