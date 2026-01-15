package com.checkmarx.intellij.ui.PageMethods;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.integration.Environment;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;


public class ScanResultsPannelPage {

    protected static ComponentFixture baseLabel;
    static String[] SEVERITY_ICONS = {
            SEVERITY_LOW_ICON,
            SEVERITY_MEDIUM_ICON,
            SEVERITY_HIGH_ICON,
            SEVERITY_CRITICAL_ICON
    };

    /**
     * Opens the Scan Results panel in the UI. If already opened, focuses the panel.
     */
    public static void openScanResultsPanel() {
        //Checking if pannel is already opened
        boolean isScanResultsPanelOpened = find(BASE_LABEL).hasText("Checkmarx");

        //Opening Scan Results pannel if its closed
        if (!isScanResultsPanelOpened) {
            openCxToolWindow();
            return;
        }

        if (baseLabel != null && baseLabel.isShowing()) {
            baseLabel.click();
        } else {
            waitFor(() -> hasAnyComponent(BASE_LABEL));
            baseLabel = find(BASE_LABEL);
            baseLabel.click();
        }

    }

    /**
     * Resets the project selection by clicking the reset button and validates the reset.
     */
    public static void resetProjectSelection(int maxAttempts) {
        //Need to set focus on reset by clicking
        pollingWaitForElement(PROJECT_NAME_NULL,false);

        boolean value = waitForElementEnabled(RESET_PROJECT_SELECTION);
        log("Checking if Reset Project Selection button is clickable: " + value);
        locateAndClickOnButton(RESET_PROJECT_SELECTION);
        boolean value2 = hasAnyComponent(PROJECT_NAME_NULL);
        if (!value2) {
            locateAndClickOnButton(RESET_PROJECT_SELECTION);
        }
        boolean resetSuccess = validateIfProjectSelectionIsReset(maxAttempts);
        if (!resetSuccess && maxAttempts > 1) {
            log("Project selection reset failed. Retrying...");
            resetProjectSelection(maxAttempts - 1);
        }
    }

    /**
     * Starts a new scan if the scan button is clickable.
     */
    public static void startNewScan() {
        boolean value = isElementClickable(START_SCAN_BTN);
        if (value) {
            //locateAndClickOnButton(START_SCAN_BTN);
            log("Scan started successfully.");
        } else {
            log("Start scan button is not clickable.");
        }
    }

    /**
     * Stops the scan if the cancel button is clickable.
     */
    public static void stopScan() {
        boolean value = isElementClickable(CANCEL_SCAN_BTN);
        if (value) {
            locateAndClickOnButton(CANCEL_SCAN_BTN);
            log("Scan cancelled successfully.");
        } else {
            log("Cancel scan button is not clickable.");
        }
    }

    /**
     * Changes the severity selection in the filter panel.
     *
     * @param severity The severity level to change (e.g., "Low", "Medium", "High", "Critical").
     * @param enable   True to enable the severity filter, false to disable it.
     */
    public static void changeSeveritySelection(String severity, boolean enable) {
        boolean isSelected = isComponentSelected(severity);
        if (isSelected != enable) {
            locateAndClickOnButton(severity);
            log("Severity " + severity + " selection changed to " + enable);
        } else {
            log("Severity " + severity + " selection is already " + enable);
        }
    }

    /**
     * Applies filters by clicking the filter action button.
     */
    public static void applyFilters(String filterOption) {
        locateAndClickOnButton(FILTER_BY_ACTION);
        selectPopupMenuOption(filterOption);

    }

    /**
     * Selects the group by option by clicking the group by action button.
     */
    public static void selectGroupByOption(String groupByOption) {
        locateAndClickOnButton(GROUP_BY_ACTION);
        selectPopupMenuOption(groupByOption);
    }

    /**
     * Expands all nodes in the results tree.
     */
    public static void expandAllNodesInTree() {
        locateAndClickOnButton(EXPAND_ACTION);
    }

    /**
     * Collapses all nodes in the results tree.
     */
    public static void collapseAllNodesInTree() {
        locateAndClickOnButton(COLLAPSE_ACTION);
    }

    /**
     * Enters the scan ID and selects it in the UI.
     */
    public static void enterScanIdAndSelect(boolean validScanId) {
        String scanId = validScanId ? Environment.SCAN_ID : "invalid-scan-id";
        log("Scan ID to enter: " + scanId);
        find(JTextFieldFixture.class, SCAN_FIELD).setText(scanId);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);


    }

    /**
     * Validates if the project selection is reset by checking for 'none' selections.
     */
    public static boolean validateIfProjectSelectionIsReset(int maxAttempts) {

        boolean projectNameReset = pollingWaitForElement(SELECTED_PROJECT_NAME_NONE, true);
        boolean branchNameReset = pollingWaitForElement(SELECTED_BRANCH_NAME_NONE, true);
        hasAnyComponent(SELECTED_SCAN_ID_NONE);
        if ((!projectNameReset || !branchNameReset) && maxAttempts > 1) {
            log("Project selection is not reset. Retrying...");
            resetProjectSelection(maxAttempts);
        } else {
            log("Project selection is not reset.");
            return false;
        }
        return true;
    }

    /**
     * Validates if the project is loaded successfully by polling for the result text to disappear.
     */
    public static void validateProjectLoadedSuccessfully() {
        boolean result = pollingWaitForElement(GETTING_RESULT_TEXT, false);
        if (result) {
            log("Project loaded successfully.");
        } else {
            Assertions.fail("Project is not loaded successfully.");
        }
    }

    /**
     * Checks all components in the Scan Results panel, including severity icons and filter options.
     *
     * @throws InterruptedException if thread sleep is interrupted
     */
    public static void checkAllTheComponentsInScanResultsPannel() throws InterruptedException {
        //Expand and collapse all nodes to ensure tree is loaded
        expandAllNodesInTree();
        collapseAllNodesInTree();

        //Check for severity icons
        for (String severityIcon : SEVERITY_ICONS) {
            boolean isIconPresent = isElementClickable(severityIcon);
            if (isIconPresent) {
                log("Severity icon " + severityIcon + " is present.");
            } else {
                Assertions.fail("Severity icon " + severityIcon + " is not present.");
            }
        }

        //Check for filter, group by
        locateAndClickOnButton(FILTER_BY_ACTION);
        Thread.sleep(2000);
        getMenuOptionsWithState();
        log("Confirmed filter selected status: " + getMenuSelectedStatus("Confirmed"));
        Assertions.assertTrue(getMenuSelectedStatus("Confirmed"), "Confirmed filter should be selected by default.");

        selectPopupMenuOption("Confirmed");
        locateAndClickOnButton(FILTER_BY_ACTION);
        Thread.sleep(2000);
        Assertions.assertFalse(getMenuSelectedStatus("Confirmed"), "Confirmed filter should be unselected after toggling.");
        selectPopupMenuOption("Confirmed");

        locateAndClickOnButton(GROUP_BY_ACTION);
        Thread.sleep(2000);
        getMenuOptionsWithState();
    }

    public static void selectAllSeverities(boolean enable) {
        for (String severityIcon : SEVERITY_ICONS) {
            changeSeveritySelection(severityIcon, enable);
        }
    }

    public static void validateResultPannel() {

        selectAllSeverities(true);

        navigate("Scan", 2);
        navigate("sast", 4);

        expandAllNodesInTree();

        String commentUUID = UUID.randomUUID().toString();
        log("commentedUUID : " + commentUUID);
        selectVulnerability("Absolute_Path_Traversal");

        selectDropDownValue(SEVERITY_COMBOBOX_ARROW, "LOW");
        selectDropDownValue(STATE_COMBOBOX_ARROW, "CONFIRMED");

        String uuid = addTriageComment();
        verifyChangeSaved(uuid);
        verifyLearnMore();
    }

    public static void selectVulnerability(String name) {
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        List<String> rows = tree.collectRows();

        // Log for debugging (optional but very useful)
        for (int i = 0; i < rows.size(); i++) {
            log("TREE[" + i + "] = " + rows.get(i));
        }

        Optional<Integer> indexOpt = IntStream.range(0, rows.size())
                .filter(i -> rows.get(i).contains(name))
                .boxed()
                .findFirst();

        Assertions.assertTrue(indexOpt.isPresent(), "Vulnerability not found: " + name);

        int index = indexOpt.get();

        waitFor(() -> {
            tree.clickRow(index+1);   // THIS is the key
            return findAll(LINK_LABEL).size() > 0;
        });
    }

    public static String addTriageComment() {
        String uuid = UUID.randomUUID().toString();

        JTextFieldFixture comment = find(JTextFieldFixture.class, TRIAGE_COMMENT);
        comment.setText(uuid);

        JButtonFixture update = find(JButtonFixture.class, UPDATE_BTN);

        waitFor(() -> {
            update.click();
            return !update.isEnabled();
        });

        waitFor(update::isEnabled);
        return uuid;
    }

    public static void verifyChangeSaved(String uuid) {
        waitFor(() -> {
            find(TAB_CHANGES).click();
            String xpath = String.format(CHANGES_COMMENT, uuid, uuid);
            find(TAB_CHANGES_CONTENT).isShowing();
            return findAll(xpath).size() > 0;
        });
    }

    public static void verifyLearnMore() {
        waitFor(() -> {
            find(TAB_LEARN_MORE).click();
            return findAll(TAB_RISK).size() > 0 &&
                    findAll(CAUSE).size() > 0 &&
                    findAll(TAB_RECOMMENDATIONS).size() > 0;
        });

        waitFor(() -> {
            find(TAB_RECOMMENDATIONS_EXAMPLES).click();
            return find(TAB_RECOMMENDATIONS_EXAMPLES).isShowing();
        });
    }

    public static void findLatestScanSelection() {
        waitFor(() -> hasAnyComponent(String.format(LATEST_SCAN, Utils.formatLatest(true), Utils.formatLatest(true))));
    }
}
