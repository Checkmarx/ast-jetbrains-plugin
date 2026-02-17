package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.ActionButtonFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static com.checkmarx.intellij.ui.PageMethods.ScanResultsPannelPage.resetProjectSelection;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;

public class TestTriggerScan extends BaseUITest {

    @BeforeEach
    public void checkResults(TestInfo info) {
        if (info.getTags().contains("skip-check-results")) {
            return;
        }

        getResults();
    }

    @Test
    @Video
    @Tag("skip-check-results")
    public void testScanButtonsDisabledWhenMissingProjectOrBranch() {
        if (triggerScanNotAllowed()) return;

        resetProjectSelection(0);
        Assertions.assertFalse(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
        Assertions.assertFalse(find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
    }

    @Test
    @Video
    //@Tag("skip-check-results")
    public void testCancelScan() {
        if (triggerScanNotAllowed()) return;

        //resetProjectSelection(0);
        waitForScanIdSelection();
        findRunScanButtonAndClick();
        waitFor(() -> find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
        find(CANCEL_SCAN_BTN).click();

        //waitFor(() -> hasAnyComponent(String.format("//div[@class='JEditorPane'and @visible_text='%s']", Bundle.message(Resource.SCAN_CANCELED_SUCCESSFULLY))));
        waitFor(() -> find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
        Assertions.assertTrue(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
    }

//    @Test
//    @Video
    public void testTriggerScanProjectAndBranchDontMatch() {
        if (triggerScanNotAllowed()) return;
        waitFor(() -> findSelection("Scan").isEnabled() && findSelection("Project").isEnabled() && findSelection("Branch").isEnabled() && findSelection("Scan").isEnabled());
        testSelectionAction(findSelection("Project"), "Project", Environment.NOT_MATCH_PROJECT_NAME);
        testSelectionAction(findSelection("Branch"), "Branch", Environment.BRANCH_NAME);
        testSelectionAction(findSelection("Scan"), "Scan", Environment.SCAN_ID_NOT_MATCH_PROJECT);
        waitFor(() -> findSelection("Scan").isEnabled() && findSelection("Project").isEnabled() && findSelection("Branch").isEnabled());
        findRunScanButtonAndClick();
        Assertions.assertTrue(hasAnyComponent(PROJECT_DOES_NOT_MATCH));
        testSelectionAction(findSelection("Project"), "Project", Environment.PROJECT_NAME);
        testSelectionAction(findSelection("Branch"), "Branch", Environment.NOT_MATCH_BRANCH_NAME);
        waitFor(() -> findSelection("Scan").isEnabled() && findSelection("Project").isEnabled() && findSelection("Branch").isEnabled());
        findRunScanButtonAndClick();
        Assertions.assertTrue(hasAnyComponent(BRANCH_DOES_NOT_MATCH));
    }

    @Test
    @Video
    public void testTriggerScanProjectWithDifferentOrganizationsDontMatch() {
        waitFor(() -> findSelection("Scan").isEnabled() && findSelection("Project").isEnabled() && findSelection("Branch").isEnabled() && findSelection("Scan").isEnabled());
        testSelectionAction(findSelection("Project"), "Project", "DiffOrg/WebGoat");
        testSelectionAction(findSelection("Branch"), "Branch", Environment.BRANCH_NAME);
        testSelectionAction(findSelection("Scan"), "Scan", Environment.SCAN_ID_NOT_MATCH_PROJECT);
        waitFor(() -> findSelection("Scan").isEnabled() && findSelection("Project").isEnabled() && findSelection("Branch").isEnabled());
        findRunScanButtonAndClick();
        Assertions.assertTrue(hasAnyComponent(PROJECT_DOES_NOT_MATCH));
    }


    @Test
    @Video
    public void testTriggerScanAndLoadResults() {
        if (triggerScanNotAllowed()) return;

        waitForScanIdSelection();
        findRunScanButtonAndClick();
        JTreeFixture treeBeforeScan = find(JTreeFixture.class, TREE);
        Assertions.assertTrue(treeBeforeScan.getValueAtRow(0).contains(Environment.SCAN_ID));
        waitFor(() -> hasAnyComponent(LOAD_RESULTS));
        clickSafe(LOAD_RESULTS);
        waitFor(() -> {
            JTreeFixture treeAfterScan = find(JTreeFixture.class, TREE);
            return treeAfterScan.getValueAtRow(0).startsWith("Scan") && !treeAfterScan.getValueAtRow(0).contains(Environment.SCAN_ID);
        });
    }

    private void findRunScanButtonAndClick() {
        ActionButtonFixture runScanBtn = find(ActionButtonFixture.class, START_SCAN_BTN);
        waitFor(runScanBtn::isEnabled);
        runScanBtn.click();
        // Handle optional popup (appears locally, not in pipeline)
        if (hasProjectDoesNotMatchPopup()) {
            log("Project mismatch popup detected. Clicking Run Local.");
            clickSafe(RUN_SCAN_LOCAL);
        }
        else {
            log("No project mismatch popup detected. Continuing.");
        }
    }

    private boolean triggerScanNotAllowed() {
        return !hasAnyComponent(START_SCAN_BTN);
    }

    private boolean hasProjectDoesNotMatchPopup() {
        try {
            waitFor(() -> hasAnyComponent(PROJECT_DOES_NOT_MATCH));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
