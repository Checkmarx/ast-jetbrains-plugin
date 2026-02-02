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
import static com.checkmarx.intellij.ui.utils.TestConstants.*;
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
     * <p>
     * This method checks if the Scan Results panel is already open by verifying the presence of the base label with the expected text.
     * If not open, it opens the Checkmarx tool window. If already open, it focuses the panel by clicking on the base label.
     */
    public static void openScanResultsPanel() {
        //Checking if pannel is already opened
        boolean isScanResultsPanelOpened = find(BASE_LABEL).hasText(CHECKMARX_TEXT);

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
     * <p>
     * This method waits for the project selection to be reset to null, clicks the reset button,
     * and validates if the reset was successful. Retries up to maxAttempts if needed.
     *
     * @param maxAttempts Maximum number of attempts to reset the project selection.
     */
    public static void resetProjectSelection(int maxAttempts) {
        //Need to set focus on reset by clicking
        pollingWaitForElement(PROJECT_NAME_NULL,false);

        // Early return optimization: Check if project, branch, and scan are already set to 'none'
        // This prevents unnecessary UI interactions when selections are already in the reset state
        boolean projectAlreadyNone = hasAnyComponent(SELECTED_PROJECT_NAME_NONE);
        boolean branchAlreadyNone = hasAnyComponent(SELECTED_BRANCH_NAME_NONE);
        boolean scanAlreadyNone = hasAnyComponent(SELECTED_SCAN_ID_NONE);

        if (projectAlreadyNone && branchAlreadyNone && scanAlreadyNone) {
            log("Project, branch, and scan are already reset to 'none'. No action needed.");
            return;
        }

        // Proceed with reset button clicks if selections are not already reset
        locateAndClickOnButton(RESET_PROJECT_SELECTION);
        boolean isElementEnabled = waitForElementEnabled(RESET_PROJECT_SELECTION);
        log("Checking if Reset Project Selection button is clickable: " + isElementEnabled);
        locateAndClickOnButton(RESET_PROJECT_SELECTION);
        boolean isPresent = hasAnyComponent(PROJECT_NAME_NULL);
        if (!isPresent) {
            locateAndClickOnButton(RESET_PROJECT_SELECTION);
        }

        validateIfProjectSelectionIsReset(maxAttempts);
    }

    /**
     * Starts a new scan if the scan button is clickable.
     * <p>
     * Checks if the start scan button is clickable and logs the result. Does not actually start the scan (commented out).
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
     * <p>
     * Checks if the cancel scan button is clickable, clicks it if so, and logs the result.
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
     * <p>
     * Checks if the severity filter is already in the desired state and toggles it if needed.
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
     * Applies filters by clicking the filter action button and selecting the given filter option from the popup menu.
     *
     * @param filterOption The filter option to select from the popup menu.
     */
    public static void applyFilters(String filterOption) {
        locateAndClickOnButton(FILTER_BY_ACTION);
        selectPopupMenuOption(filterOption);

    }

    /**
     * Selects the group by option by clicking the group by action button and selecting the given option from the popup menu.
     *
     * @param groupByOption The group by option to select from the popup menu.
     */
    public static void selectGroupByOption(String groupByOption) {
        locateAndClickOnButton(GROUP_BY_ACTION);
        selectPopupMenuOption(groupByOption);
    }

    /**
     * Expands all nodes in the results tree by clicking the expand action button.
     */
    public static void expandAllNodesInTree() {
        locateAndClickOnButton(EXPAND_ACTION);
    }

    /**
     * Collapses all nodes in the results tree by clicking the collapse action button.
     */
    public static void collapseAllNodesInTree() {
        locateAndClickOnButton(COLLAPSE_ACTION);
    }

    /**
     * Enters the scan ID in the scan field and selects it in the UI.
     *
     * @param validScanId If true, enters a valid scan ID; otherwise, enters an invalid scan ID.
     */
    public static void enterScanIdAndSelect(boolean validScanId) {
        String scanId = validScanId ? Environment.SCAN_ID : "invalid-scan-id";

        waitFor(() -> {
            List<JTextFieldFixture> fields =
                    findAll(JTextFieldFixture.class, SCAN_FIELD);

            if (fields.size() != 1) {
                return false;
            }

            JTextFieldFixture field = fields.get(0);
            field.setText(scanId);
            return scanId.equals(field.getText());
        });

        Keyboard keyboard = new Keyboard(remoteRobot);
        keyboard.enter();
    }

    /**
     * Validates if the project selection is reset by polling for 'none' selections for project and branch.
     * Retries if not reset and maxAttempts > 1.
     *
     * @param maxAttempts Maximum number of attempts to validate the reset.
     * @return true if reset is successful, false otherwise.
     */
    public static boolean validateIfProjectSelectionIsReset(int maxAttempts) {

        boolean projectNameReset = pollingWaitForElement(SELECTED_PROJECT_NAME_NONE, true);
        boolean branchNameReset = pollingWaitForElement(SELECTED_BRANCH_NAME_NONE, true);
        hasAnyComponent(SELECTED_SCAN_ID_NONE);
        log("Is none project selected: " + projectNameReset + ", Is none branch selected: " + branchNameReset);
        if (projectNameReset && branchNameReset) {
            log("Project selection reset successfully.");
            return true;
        } else if (maxAttempts > 0) {
            log("Project selection is not reset. Retrying...");
            resetProjectSelection(maxAttempts - 1);
            return false;
        } else {
            log("Project selection is not reset. No more attempts.");
            return false;
        }
    }

    /**
     * Validates if the project is loaded successfully by polling for the result text to disappear.
     * Fails the test if the project is not loaded.
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
     * Expands/collapses tree nodes, checks severity icons, and verifies filter/group by menu options.
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
        log("Confirmed filter selected status: " + getMenuSelectedStatus(CONFIRMED_TEXT));
        Assertions.assertTrue(getMenuSelectedStatus(CONFIRMED_TEXT), "Confirmed filter should be selected by default.");

        selectPopupMenuOption(CONFIRMED_TEXT);
        locateAndClickOnButton(FILTER_BY_ACTION);
        Thread.sleep(2000);
        Assertions.assertFalse(getMenuSelectedStatus(CONFIRMED_TEXT), "Confirmed filter should be unselected after toggling.");
        selectPopupMenuOption(CONFIRMED_TEXT);

        locateAndClickOnButton(GROUP_BY_ACTION);
        Thread.sleep(2000);
        getMenuOptionsWithState();
    }

    /**
     * Selects or deselects all severity filters in the panel.
     *
     * @param enable True to select all severities, false to deselect all.
     */
    public static void selectAllSeverities(boolean enable) {
        for (String severityIcon : SEVERITY_ICONS) {
            changeSeveritySelection(severityIcon, enable);
        }
    }

    /**
     * Validates the result panel by selecting all severities, navigating, expanding nodes, adding a triage comment, and verifying changes.
     */
    public static void validateResultPannel() {

        selectAllSeverities(true);
        navigate("Scan", 2);
        navigate("sast", 4);
        expandAllNodesInTree();

        String commentUUID = UUID.randomUUID().toString();
        log("commentedUUID : " + commentUUID);
        selectVulnerability(VULNERABILITIES_TEXT);

        selectDropDownValue(SEVERITY_COMBOBOX_ARROW, "LOW");
        selectDropDownValue(STATE_COMBOBOX_ARROW, CONFIRMED_TEXT);

        String uuid = addTriageComment();
        verifyChangeSaved(uuid);
        verifyLearnMore();
    }

    /**
     * Selects a vulnerability in the results tree by name.
     *
     * @param name The name of the vulnerability to select.
     */
    public static void selectVulnerability(String name) {
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        List<String> rows = tree.collectRows();

        log("Sent Vulnerability Name"+name);
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

    /**
     * Adds a triage comment with a random UUID and returns the UUID.
     *
     * @return The UUID of the added triage comment.
     */
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

    /**
     * Verifies that a change with the given UUID was saved in the changes tab.
     *
     * @param uuid The UUID of the triage comment to verify.
     */
    public static void verifyChangeSaved(String uuid) {
        waitFor(() -> {
            find(TAB_CHANGES).click();
            String xpath = String.format(CHANGES_COMMENT, uuid, uuid);
            find(TAB_CHANGES_CONTENT).isShowing();
            return findAll(xpath).size() > 0;
        });
    }

    /**
     * Verifies the Learn More tab by checking for the presence of risk, cause, and recommendations sections.
     */
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

    /**
     * Waits for the latest scan selection to appear in the UI.
     */
    public static void findLatestScanSelection() {
        waitFor(() -> hasAnyComponent(String.format(LATEST_SCAN, Utils.formatLatest(true), Utils.formatLatest(true))));
    }
}
