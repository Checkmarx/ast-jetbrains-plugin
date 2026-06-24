package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.remoterobot.fixtures.ActionButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.utils.Keyboard;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.*;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.List;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.resetProjectSelection;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.validateProjectLoadedSuccessfully;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestGeneral extends com.checkmarx.intellij.ast.test.ui.BaseUITest {
    static List<String> defaultState = List.of("CONFIRMED", "TO_VERIFY", "URGENT", "NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE", "IGNORED", "NOT_IGNORED");
    EnumSet<SeverityFilter> exclude = EnumSet.of(SeverityFilter.MALICIOUS, SeverityFilter.INFO);

    @Language("XPath")
    public static String filterXPath(SeverityFilter filter) {
        return String.format("//div[@myicon='%s.svg']", filter.tooltipSupplier().get().toLowerCase());
    }

    @AfterEach
    public void cleanupDialogs() {
        if (hasAnyComponent(WELCOME_CLOSE_BUTTON)) {
            click(WELCOME_CLOSE_BUTTON);
        }
        if (hasAnyComponent(OK_BTN)) {
            click(OK_BTN);
        }
    }

    @BeforeEach
    public void checkResults() {
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateSuccessfulLogin(true);
        resetProjectSelection(1);
        enterScanIdAndSelect(true);
        validateProjectLoadedSuccessfully();
    }

    @Test
    @Video
    @Order(1)
    @DisplayName("End-to-End Scan Results Panel and Result Validation")
    public void testEndToEnd() throws InterruptedException {
        checkAllTheComponentsInScanResultsPanel();
        validateResultPanel();
    }

    @Test
    @Video
    @Order(2)
    @DisplayName("Filter Functionality: Enable/Disable Severities and Tree Validation")
    public void testFilters() {
        waitForScanIdSelection();

        // disable all severities and check for empty tree
        selectAllSeverities(false);
        navigate("Scan", 1);

        // enable all severities and check for at least 1 result
        selectAllSeverities(true);
        navigate("Scan", 2);
    }

    @Test
    @Video
    @Order(3)
    @DisplayName("Invalid Scan ID Handling")
    public void testInvalidScanId() {
        waitFor(() -> {
            find(JTextFieldFixture.class, SCAN_FIELD).click();

            if (!find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus()) {
                return false;
            }

            find(JTextFieldFixture.class, SCAN_FIELD).setText("inva-lid");
            new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);

            JTreeFixture tree = find(JTreeFixture.class, TREE);
            boolean invalidMsgDisplayed = tree.getData().getAll().get(0).getText().contains(Bundle.message(Resource.INVALID_SCAN_ID));

            return tree.getData().getAll().size() == 1 && invalidMsgDisplayed;
        });
    }

    @Test
    @Video
    @Order(4)
    @DisplayName("Verify latest scan auto-populates after selecting Project and Branch")
    public void testLatestScanAutoPopulates() {
        resetProjectSelection(1);
        testSelectionAction(findSelection("Project"), "Project", Environment.PROJECT_NAME);
        testSelectionAction(findSelection("Branch"), "Branch", Environment.BRANCH_NAME);

        // TC11: Verify scan dropdown auto-populates with latest scan (not "none")
        findLatestScanSelection();
        Assertions.assertFalse(
                findSelection("Scan").hasText("Scan: none"),
                "Scan should auto-populate after selecting Project and Branch");

        waitFor(() -> find(JTreeFixture.class, TREE).getData().getAll().size() > 0);
        log("Latest scan auto-populated and results loaded successfully");
    }

    @Test
    @Video
    @Order(5)
    @DisplayName("Selection of Project, Branch, and Scan")
    public void testSelection() {
        resetProjectSelection(1);
        testSelectionAction(findSelection("Project"), "Project", Environment.PROJECT_NAME);
        testSelectionAction(findSelection("Branch"), "Branch", Environment.BRANCH_NAME);
        findLatestScanSelection();

        testSelectionAction(findSelection("Scan"), "Scan", Environment.SCAN_ID);
        waitFor(() -> find(JTreeFixture.class, TREE).getData().getAll().size() > 0);
    }

    @Test
    @Video
    @Order(6)
    @DisplayName("TC43: Verify refresh icon clears selections and project dropdown reloads")
    public void testRefreshClearsAndReloads() {
        // First make a selection so there's something to clear
        testSelection();

        // Click refresh to clear
        resetProjectSelection(1);

        // Verify all dropdowns reset to "none"
        Assertions.assertTrue(hasAnyComponent(NO_PROJECT_SELECTED), "Project should be reset to 'none'");
        Assertions.assertTrue(hasAnyComponent(NO_BRANCH_SELECTED), "Branch should be reset to 'none'");
        Assertions.assertTrue(hasAnyComponent(NO_SCAN_SELECTED), "Scan should be reset to 'none'");

        // Verify project dropdown is still functional (can be clicked and reloads projects)
        ActionButtonFixture projectBtn = findSelection("Project");
        Assertions.assertTrue(projectBtn.isEnabled(), "Project dropdown should be enabled and ready to reload after reset");
        log("Refresh icon cleared all selections and project dropdown is reloadable");
    }

    @Test
    @Video
    @Order(7)
    @DisplayName("Verify all scan types are displayed in scan results tree")
    public void testAllScanTypesDisplayed() {
        // TC13: Verify all scan engine types (sast, sca, iac security) are present in the results tree
        waitForScanIdSelection();
        expandAllNodesInTree();

        JTreeFixture tree = find(JTreeFixture.class, TREE);
        List<String> rows = tree.collectRows();
        String treeContent = String.join(", ", rows).toLowerCase();

        log("Tree content: " + treeContent);
        Assertions.assertTrue(treeContent.contains("sast"), "SAST scan type should be present in results");
        Assertions.assertTrue(treeContent.contains("sca"), "SCA scan type should be present in results");
        Assertions.assertTrue(treeContent.contains("iac security"), "IaC Security scan type should be present in results");
    }

    @Test
    @Video
    @Order(8)
    @DisplayName("TC44: Verify Critical severity filter toggles critical results visibility")
    public void testFilterCriticalSeverity() {
        verifySeverityFilterToggles(SEVERITY_CRITICAL_ICON, "critical");
    }

    @Test
    @Video
    @Order(9)
    @DisplayName("TC45: Verify High severity filter toggles high results visibility")
    public void testFilterHighSeverity() {
        verifySeverityFilterToggles(SEVERITY_HIGH_ICON, "high");
    }

    @Test
    @Video
    @Order(10)
    @DisplayName("TC46: Verify Medium severity filter toggles medium results visibility")
    public void testFilterMediumSeverity() {
        verifySeverityFilterToggles(SEVERITY_MEDIUM_ICON, "medium");
    }

    @Test
    @Video
    @Order(11)
    @DisplayName("TC47: Verify Low severity filter toggles low results visibility")
    public void testFilterLowSeverity() {
        verifySeverityFilterToggles(SEVERITY_LOW_ICON, "low");
    }

    @Test
    @Video
    @Order(12)
    @DisplayName("TC48: Verify Info severity filter toggles informational results visibility")
    public void testFilterInfoSeverity() {
        verifySeverityFilterToggles(SEVERITY_INFO_ICON, "info");
    }

    @Test
    @Video
    @Order(13)
    @DisplayName("TC05: Verify all severity icons are selected by default on first install")
    public void testDefaultSeverityFiltersSelected() {
        // After login + scan load, all default severity filters should be selected (CRITICAL, HIGH, MEDIUM, LOW)
        // INFO should NOT be selected by default
        waitForScanIdSelection();

        Assertions.assertTrue(isComponentSelected(SEVERITY_CRITICAL_ICON),
                "Critical severity should be selected by default");
        Assertions.assertTrue(isComponentSelected(SEVERITY_HIGH_ICON),
                "High severity should be selected by default");
        Assertions.assertTrue(isComponentSelected(SEVERITY_MEDIUM_ICON),
                "Medium severity should be selected by default");
        Assertions.assertTrue(isComponentSelected(SEVERITY_LOW_ICON),
                "Low severity should be selected by default");
        Assertions.assertFalse(isComponentSelected(SEVERITY_INFO_ICON),
                "Info severity should NOT be selected by default");
        log("All default severity filters are correctly selected/deselected");
    }

    @Test
    @Video
    @Order(14)
    @DisplayName("TC06: Verify 'Proposed Not Exploitable' and 'Not Exploitable' state filters are NOT selected by default")
    public void testDefaultStateFiltersNotSelected() {
        // Open filter menu and verify that NOT_EXPLOITABLE and PROPOSED_NOT_EXPLOITABLE are not selected
        waitForScanIdSelection();

        locateAndClickOnButton(FILTER_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        Assertions.assertFalse(getMenuSelectedStatus("Not Exploitable"),
                "'Not Exploitable' state filter should NOT be selected by default");
        Assertions.assertFalse(getMenuSelectedStatus("Proposed Not Exploitable"),
                "'Proposed Not Exploitable' state filter should NOT be selected by default");

        // Close the popup by pressing Escape
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);
        log("'Not Exploitable' and 'Proposed Not Exploitable' state filters are correctly not selected by default");
    }

    /**
     * Verifies that toggling a specific severity filter icon hides/shows the corresponding nodes in the tree.
     * Steps: enable all severities, expand tree, count nodes of target severity,
     * disable target severity, verify those nodes disappear, re-enable, verify they reappear.
     */
    private void verifySeverityFilterToggles(String severityIcon, String severityName) {
        waitForScanIdSelection();
        selectAllSeverities(true);
        expandAllNodesInTree();

        // Count tree nodes containing the severity name before disabling
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        long countBefore = tree.collectRows().stream()
                .filter(row -> row.toLowerCase().contains(severityName))
                .count();
        log(severityName + " nodes before disabling filter: " + countBefore);

        // Disable the target severity
        changeSeveritySelection(severityIcon, false);

        // Wait for tree to collapse/reload after filter change, then expand
        sleep(2000);
        expandAllNodesInTree();

        // Verify nodes of that severity are no longer visible
        waitFor(() -> {
            JTreeFixture updatedTree = find(JTreeFixture.class, TREE);
            return updatedTree.collectRows().stream()
                    .noneMatch(row -> row.toLowerCase().contains(severityName));
        });
        log(severityName + " nodes hidden after disabling filter");

        // Re-enable the severity
        changeSeveritySelection(severityIcon, true);

        // Wait for tree to collapse/reload after filter change, then expand
        sleep(2000);
        expandAllNodesInTree();

        // Verify nodes reappear
        if (countBefore > 0) {
            waitFor(() -> {
                JTreeFixture restoredTree = find(JTreeFixture.class, TREE);
                return restoredTree.collectRows().stream()
                        .anyMatch(row -> row.toLowerCase().contains(severityName));
            });
            log(severityName + " nodes visible again after re-enabling filter");
        }
    }
}
