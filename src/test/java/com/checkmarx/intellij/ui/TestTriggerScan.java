package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.ActionButtonFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import com.checkmarx.intellij.Constants;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class TestTriggerScan extends BaseUITest {

    @BeforeEach
    public void checkResults(TestInfo info) {
        if (info.getDisplayName().equals("testScanButtonsDisabledWhenMissingProjectOrBranch")) {
            return;
        }

        getResults();
    }

    @Test
    @Video
    public void testScanButtonsDisabledWhenMissingProjectOrBranch() {
        if (triggerScanNotAllowed()) return;

        clearSelection();
        Assertions.assertFalse(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
        Assertions.assertFalse(find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
    }

    @Test
    @Video
    public void testCancelScan() {
        if (triggerScanNotAllowed()) return;

        waitForScanIdSelection();
        findRunScanButtonAndClick();
        waitFor(() -> find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
        find(CANCEL_SCAN_BTN).click();

        waitFor(() -> hasAnyComponent(String.format("//div[@class='JEditorPane'and @visible_text='%s']", Bundle.message(Resource.SCAN_CANCELED_SUCCESSFULLY))));

        Assertions.assertTrue(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
    }

    @Test
    @Video
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

        if (triggerScanNotAllowed()) return;
        waitFor(() -> findSelection("Scan").isEnabled() && findSelection("Project").isEnabled() && findSelection("Branch").isEnabled() && findSelection("Scan").isEnabled());
        testSelectionAction(findSelection("Project"), "Project", Constants.NOT_MATCH_PROJECT_NAME_DIFF_ORGANIZATION);
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
        waitFor(() -> hasAnyComponent(SCAN_FINISHED));
        find(LOAD_RESULTS).click();
        waitFor(() -> {
            JTreeFixture treeAfterScan = find(JTreeFixture.class, TREE);
            return treeAfterScan.getValueAtRow(0).startsWith("Scan") && !treeAfterScan.getValueAtRow(0).contains(Environment.SCAN_ID);
        });
    }

    private void findRunScanButtonAndClick() {
        ActionButtonFixture runScanBtn = find(ActionButtonFixture.class, START_SCAN_BTN);
        waitFor(runScanBtn::isEnabled);
        runScanBtn.click();
    }

    private boolean triggerScanNotAllowed() {
        return !hasAnyComponent(START_SCAN_BTN);
    }
}
