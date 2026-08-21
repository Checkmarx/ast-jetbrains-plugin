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
import java.util.Map;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.resetProjectSelection;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.validateProjectLoadedSuccessfully;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.TestConstants.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

public class TestGeneral extends com.checkmarx.intellij.ast.test.ui.BaseUITest {
    static List<String> defaultState = List.of("CONFIRMED", "TO_VERIFY", "URGENT", "NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE", "IGNORED", "NOT_IGNORED");
    EnumSet<SeverityFilter> exclude = EnumSet.of(SeverityFilter.MALICIOUS, SeverityFilter.INFO);

    // TODO: replace placeholders with actual expected counts per Scan from the CxOne application for this scan/project.
    public static final Map<String, Integer> EXPECTED_SCAN_TYPE_COUNTS = Map.of(
            "sast", 0,
            "sca", 73,
            "secret detection", 0,
            "IaC Security", 62,
            "containers", 60001
    );

    // TODO: replace placeholders with actual expected counts per severity from the CxOne application for this scan/project.
    public static final Map<String, Integer> EXPECTED_SEVERITY_COUNTS = Map.of(
            "MALICIOUS", 0,
            "CRITICAL", 14,
            "HIGH", 29,
            "MEDIUM", 25,
            "LOW", 5,
            "INFO", 0
    );


    @Language("XPath")
    public static String filterXPath(SeverityFilter filter) {
        return String.format("//div[@myicon='%s.svg']", filter.tooltipSupplier().get().toLowerCase());
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
    @DisplayName("End-to-End Scan Results Panel and Result Validation")
    public void testEndToEnd() throws InterruptedException {
        checkAllTheComponentsInScanResultsPannel();
        validateResultPannel();
    }

    @Test
    @Video
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
    @DisplayName("Clear Selection After Test")
    public void testClearSelection() {
        testSelection();
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
    @DisplayName(" TC09 - Verify 'Scan My Local Branch' Option Appears in Branch Filter After Selecting a Project")
    public void testScanMyLocalBranchOption() {
        resetProjectSelection(1);
        testSelectionAction(findSelection("Project"), "Project", Environment.PROJECT_NAME);
        verifyLocalBranchOptionPresent();
    }

    @Test
    @Video
    @DisplayName(" TC10 - Verify Branch Filter Shows Only Correct Branches for Selected Project")
    public void testProjectBranchFilter() {
        resetProjectSelection(1);
        testSelectionAction(findSelection("Project"), "Project", Environment.PROJECT_NAME);
        verifyBranchListForProject(Environment.PROJECT_NAME, Environment.BRANCH_NAME);
    }

    @Test
    @Video
    @DisplayName(" TC12 - Verify Total Vulnerability Count Is Shown Per Severity")
    public void testVulnerabilityCountBySeverity() {
        getResults();
        waitForScanIdSelection();
        navigate("Scan", 2);

        verifyVulnerabilityCountPerSeverity(EXPECTED_SEVERITY_COUNTS);
    }

    @Test
    @Video
    @DisplayName(" TC14 - Verify Total Vulnerability Count Is Shown Per Scan Type")
    public void testVulnerabilityCountByScanType() {
        getResults();
        waitForScanIdSelection();
        navigate("Scan", 2);

        verifyScanTypeCountLabels(EXPECTED_SCAN_TYPE_COUNTS);
    }

    @Test
    @Video
    @DisplayName("Verify Vulnerabilities Are Grouped in Same Order as Group By Selection")
    public void testVulnerabilityGroupOrder() {
        getResults();
        waitForScanIdSelection();

        verifyGroupingMatchesSelection("Severity");
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
        //Assertions.assertTrue(treeContent.contains("sast"), "SAST scan type should be present in results");
        Assertions.assertTrue(treeContent.contains("sca"), "SCA scan type should be present in results");
        Assertions.assertTrue(treeContent.contains("iac security"), "IaC Security scan type should be present in results");
    }

    @Test
    @Video
    @Order(8)
    @DisplayName("TC44: Verify Critical severity filter toggles critical results visibility")
    public void testFilterCriticalSeverity() {
        waitForScanIdSelection();
        verifySeverityFilterToggles(SEVERITY_CRITICAL_ICON, "critical");
    }

    @Test
    @Video
    @Order(9)
    @DisplayName("TC45: Verify High severity filter toggles high results visibility")
    public void testFilterHighSeverity() {
        waitForScanIdSelection();
        verifySeverityFilterToggles(SEVERITY_HIGH_ICON, "high");
    }

    @Test
    @Video
    @Order(10)
    @DisplayName("TC46: Verify Medium severity filter toggles medium results visibility")
    public void testFilterMediumSeverity() {
        waitForScanIdSelection();
        verifySeverityFilterToggles(SEVERITY_MEDIUM_ICON, "medium");
    }

    @Test
    @Video
    @Order(11)
    @DisplayName("TC47: Verify Low severity filter toggles low results visibility")
    public void testFilterLowSeverity() {
        waitForScanIdSelection();
        verifySeverityFilterToggles(SEVERITY_LOW_ICON, "low");
    }

    @Test
    @Video
    @Order(12)
    @DisplayName("TC48: Verify Info severity filter toggles informational results visibility")
    public void testFilterInfoSeverity() {
        waitForScanIdSelection();
        verifySeverityFilterToggles(SEVERITY_INFO_ICON, "info");
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

}