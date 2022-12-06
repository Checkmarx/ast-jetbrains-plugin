package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.ActionButtonFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTriggerScan extends BaseUITest {

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

        getResults();
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

        getResults();
        waitFor(() -> findScanSelection().isEnabled() && findProjectSelection().isEnabled() && findBranchSelection().isEnabled());
        testSelectionAction(this::findProjectSelection, "Project", Environment.NOT_MATCH_PROJECT_NAME);
        testSelectionAction(this::findBranchSelection, "Branch", Environment.BRANCH_NAME);
        waitFor(() -> findScanSelection().isEnabled() && findProjectSelection().isEnabled() && findBranchSelection().isEnabled());
        findRunScanButtonAndClick();
        Assertions.assertTrue(hasAnyComponent("//div[@accessiblename.key='PROJECT_DOES_NOT_MATCH_TITLE']"));
        testSelectionAction(this::findProjectSelection, "Project", Environment.PROJECT_NAME);
        testSelectionAction(this::findBranchSelection, "Branch", Environment.NOT_MATCH_BRANCH_NAME);
        waitFor(() -> findScanSelection().isEnabled() && findProjectSelection().isEnabled() && findBranchSelection().isEnabled());
        findRunScanButtonAndClick();
        Assertions.assertTrue(hasAnyComponent("//div[@accessiblename.key='BRANCH_DOES_NOT_MATCH_TITLE']"));
    }

    @Test
    @Video
    public void testTriggerScanAndLoadResults() {
        if (triggerScanNotAllowed()) return;

        getResults();
        waitForScanIdSelection();
        findRunScanButtonAndClick();
        JTreeFixture treeBeforeScan = find(JTreeFixture.class, TREE);
        Assertions.assertTrue(treeBeforeScan.getValueAtRow(0).contains(Environment.SCAN_ID));
        waitFor(() -> hasAnyComponent("//div[@accessiblename.key='SCAN_FINISHED']"));
        find("//div[@class='LinkLabel']").click();
        waitFor(() -> findRunScanButton().isEnabled() && findScanSelection().isEnabled() && findProjectSelection().isEnabled() && findBranchSelection().isEnabled());
        waitForScanIdSelection();
        JTreeFixture treeAfterScan = find(JTreeFixture.class, TREE);
        // Assert that new results were loaded for a new scan id
        Assertions.assertFalse(treeAfterScan.getValueAtRow(0).contains(Environment.SCAN_ID));
    }

    private void findRunScanButtonAndClick() {
        ActionButtonFixture runScanBtn = find(ActionButtonFixture.class, START_SCAN_BTN);
        waitFor(runScanBtn::isEnabled);
        runScanBtn.click();
    }

    private ActionButtonFixture findRunScanButton() {
        return find(ActionButtonFixture.class, START_SCAN_BTN);
    }

    private boolean triggerScanNotAllowed() {
        return !hasAnyComponent(START_SCAN_BTN);
    }
}
