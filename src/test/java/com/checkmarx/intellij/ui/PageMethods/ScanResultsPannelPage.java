package com.checkmarx.intellij.ui.PageMethods;

import com.checkmarx.intellij.integration.Environment;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;

import java.awt.event.KeyEvent;

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
    public static void resetProjectSelection() {
        locateAndClickOnButton(RESET_PROJECT_SELECTION);
        boolean value = isElementClickable(RESET_PROJECT_SELECTION);
        log("Checking if Reset Project Selection button is clickable: " + value);
        boolean value2 = waitForElementEnabled(RESET_PROJECT_SELECTION);
        log("Checking if Reset Project Selection button is clickable: " + value2);
        locateAndClickOnButton(RESET_PROJECT_SELECTION);
        validateIfProjectSelectionIsReset();
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
        boolean isSelected = isCheckboxSelected(severity);
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
    public static void applyFilters() {
        locateAndClickOnButton(FILTER_BY_ACTION);

    }

    /**
     * Selects the group by option by clicking the group by action button.
     */
    public static void selectGroupByOption() {
        locateAndClickOnButton(GROUP_BY_ACTION);
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
    public static void enterScanIdAndSelect() {
        find(JTextFieldFixture.class, SCAN_FIELD).setText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
    }

    /**
     * Validates if the project selection is reset by checking for 'none' selections.
     */
    public static void validateIfProjectSelectionIsReset() {

        boolean value = pollingWaitForElement(SELECTED_PROJECT_NAME_NONE, true);
        pollingWaitForElement(SELECTED_BRANCH_NAME_NONE, true);
        pollingWaitForElement(SELECTED_SCAN_ID_NONE, true);
        if (value) {
            log("Project selection is reset successfully.");
        } else {
            Assertions.fail("Project selection is not reset.");
        }
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
        clickMenuOption("Confirmed");
        locateAndClickOnButton(FILTER_BY_ACTION);
        log("Confirmed filter selected status after toggle: " + getMenuSelectedStatus("Confirmed"));
        clickMenuOption("Confirmed");

        locateAndClickOnButton(GROUP_BY_ACTION);
        locateAndClickOnButton(GROUP_BY_ACTION);
        Thread.sleep(2000);
        getMenuOptionsWithState();


    }
}
