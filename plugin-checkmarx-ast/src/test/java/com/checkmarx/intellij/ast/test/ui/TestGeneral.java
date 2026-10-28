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
import java.util.UUID;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.resetProjectSelection;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.validateProjectLoadedSuccessfully;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.TriagePage.*;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.VulnerabilityDetailPage.selectVulnerabilityAndVerifyDescriptionTab;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.VulnerabilityDetailPage.verifyChangesTabContent;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.AttackVectorPage.*;
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
            "IaC Security", 63,
            "containers", 6599
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

    // TODO: replace placeholders with the actual source file (or path suffix) and 1-based line
    // number that the first Attack Vector node of VULNERABILITIES_TEXT should navigate to, from
    // the CxOne application's SAST results for this scan/project.
    public static final String EXPECTED_ATTACK_VECTOR_FILE = "REPLACE_WITH_FILE_NAME";
    public static final int EXPECTED_ATTACK_VECTOR_LINE = -1;

    // TODO: replace placeholder with the actual tooltip text (e.g. vulnerability title/severity)
    // expected when hovering over the Attack Vector marker for VULNERABILITIES_TEXT, from the
    // CxOne application's SAST results for this scan/project.
    public static final String EXPECTED_ATTACK_VECTOR_TOOLTIP_TEXT = "REPLACE_WITH_TOOLTIP_TEXT";


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

    //@Disabled("Flaky - TC14")
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
    @DisplayName("TC20 - Verify Group By Filter Retained When Fetching Results for Another Project")
    public void testGroupByRetainedOnProjectSwitch() {
        getResults();
        waitForScanIdSelection();

        // Select a non-default Group By option for the first project.
        selectGroupByOption(FILE_TEXT);
        Assertions.assertTrue(isGroupByOptionSelected(FILE_TEXT),
                "'File' Group By should be selected before switching projects.");

        // Switch to another project by loading results for a scan that belongs to it.
        getResults(Environment.SCAN_ID_NOT_MATCH_PROJECT);

        // The Group By selection should be retained, not reset to default, after the switch.
        Assertions.assertTrue(isGroupByOptionSelected(FILE_TEXT),
                "'File' Group By selection should be retained after fetching results for another project.");

        // Restore the default Group By state so this non-default selection doesn't leak into
        // other tests sharing this IDE session.
        uncheckGroupByOption(FILE_TEXT);
    }

    @Test
    @Video
    @DisplayName("TC22 - Verify No File Grouping When 'Group by File' Is Deselected")
    public void testNoFileGroupingWhenGroupByFileDeselected() {
        getResults();
        waitForScanIdSelection();

        verifyNoFileGroupingWhenDeselected();
    }

    @Test
    @Video
    @DisplayName("TC23 - Verify No Severity Grouping When 'Group by Severity' Is Deselected")
    public void testNoSeverityGroupingWhenGroupBySeverityDeselected() {
        getResults();
        waitForScanIdSelection();

        verifyNoSeverityGroupingWhenDeselected();
    }

    @Test
    @Video
    @DisplayName("TC24: Verify No State Grouping When 'Group by State' Is Deselected")
    public void testNoStateGroupingWhenGroupByStateDeselected() {
        getResults();
        waitForScanIdSelection();

        verifyNoStateGroupingWhenDeselected();
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

    //@Disabled("Flaky - TC44")
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
    @Order(13)
    @DisplayName("TC49: Verify Combined Filtering (Multiple Severities Off)")
    public void testCombinedFilters() {
        waitForScanIdSelection();
        verifyCombinedSeverityFilterToggle(Map.of(
                SEVERITY_CRITICAL_ICON, "critical",
                SEVERITY_MEDIUM_ICON, "medium"
        ));
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

    @Test
    @Video
    @Order(15)
    @DisplayName("TC25: Verify Only 'To Verify' Vulnerabilities Shown When State Filter Set to 'To Verify'")
    public void testOnlyToVerifyVulnerabilitiesShown() {
        waitForScanIdSelection();

        // Capture the Filter By and Group By selections before this test changes them, so they can be restored
        Map<String, Boolean> originalFilterState = captureFilterMenuState();
        boolean wasStateGroupBySelected = isGroupByOptionSelected("State");

        // Isolate the state filter to only 'To Verify' (deselecting all other defaults,
        // then deselecting/reselecting 'To Verify' itself)
        selectStateFilter(TO_VERIFY_TEXT);

        // Verify only 'To Verify' vulnerabilities remain visible in the results tree
        // (this also switches Group By to 'State')
        verifyOnlyStateVisible(TO_VERIFY_TEXT);

        // Restore Group By and Filter By selections to their prior state
        if (isGroupByOptionSelected("State") != wasStateGroupBySelected) {
            selectGroupByOption("State");
        }
        restoreFilterMenuState(originalFilterState);
    }

    @Test
    @Video
    @Order(16)
    @DisplayName("TC26: Verify Only 'Urgent' Vulnerabilities Shown When State Filter Set to 'Urgent'")
    public void testOnlyUrgentVulnerabilitiesShown() {
        waitForScanIdSelection();

        // Capture the Filter By and Group By selections before this test changes them, so they can be restored
        Map<String, Boolean> originalFilterState = captureFilterMenuState();
        boolean wasStateGroupBySelected = isGroupByOptionSelected("State");

        // Isolate the state filter to only 'Urgent' (deselecting all other defaults,
        // then deselecting/reselecting 'Urgent' itself)
        selectStateFilter(URGENT_TEXT);

        // Verify only 'Urgent' vulnerabilities remain visible in the results tree
        // (this also switches Group By to 'State')
        verifyOnlyStateVisible(URGENT_TEXT);

        // Restore Group By and Filter By selections to their prior state
        if (isGroupByOptionSelected("State") != wasStateGroupBySelected) {
            selectGroupByOption("State");
        }
        restoreFilterMenuState(originalFilterState);
    }

    @Test
    @Video
    @DisplayName("TC27: Verify Ability to Expand Result Tree Nodes")
    public void testNodeExpandsAndShowsChildren() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);

        verifyExpandAllRevealsNestedChildren();
    }

    @Test
    @Video
    @DisplayName("TC35: Verify Expand All and Collapse All Affect the Result Tree")
    public void testExpandAllAndCollapseAllAffectEntireTree() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);

        // Collapse everything, then select a single top-level row so that only that one node is
        // "current" before Expand All is triggered - proving the action affects the whole tree,
        // not just the previously selected node.
        collapseAllNodesInTree();
        find(JTreeFixture.class, TREE).clickRow(0);
        verifyAllNodesExpanded();

        // Select a single row again before Collapse All, for the same reason as above.
        find(JTreeFixture.class, TREE).clickRow(0);
        verifyAllNodesCollapsed();
    }

    @Test
    @Video
    @DisplayName("TC28: Verify SAST Vulnerability Details Shown in Description Tab")
    public void testSastVulnerabilityDescriptionTabDisplayed() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);

        // TODO: EXPECTED_SCAN_TYPE_COUNTS lists "sast" -> 0 and the SAST assertion in
        // testAllScanTypesDisplayed() is disabled, meaning the scan/project configured for this
        // environment may not always contain SAST results. Skip (rather than fail) until a scan
        // with guaranteed SAST findings is confirmed, then this guard can be removed.
        expandAllNodesInTree();
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        boolean hasSastResults = tree.collectRows().stream()
                .anyMatch(row -> row.trim().toLowerCase().startsWith("sast"));
        Assumptions.assumeTrue(hasSastResults, "Skipping TC28: no SAST results found in current scan.");

        navigate("sast", 4);
        selectVulnerabilityAndVerifyDescriptionTab(VULNERABILITIES_TEXT);
    }

    @Test
    @Video
    @DisplayName("TC32: Verify Attack Vector Node Opens Correct Source File and Line")
    public void testAttackVectorNodeOpensCorrectSourceFileAndLine() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);

        // Same guard as TC28: the configured scan/project may not always contain SAST results,
        // and the Attack Vector tab only appears for SAST findings with call-path nodes.
        expandAllNodesInTree();
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        boolean hasSastResults = tree.collectRows().stream()
                .anyMatch(row -> row.trim().toLowerCase().startsWith("sast"));
        Assumptions.assumeTrue(hasSastResults, "Skipping TC32: no SAST results found in current scan.");

        navigate("sast", 4);
        clickAttackVectorNode(VULNERABILITIES_TEXT, 1);
        verifyEditorOpensFile(EXPECTED_ATTACK_VECTOR_FILE, EXPECTED_ATTACK_VECTOR_LINE);
    }

    @Test
    @Video
    @DisplayName("TC34: Verify Hovering Over Vulnerability Marker Shows Tooltip")
    public void testHoverOverVulnerabilityMarkerShowsTooltip() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);

        // Same guard as TC28/TC32: the configured scan/project may not always contain SAST
        // results, and the Attack Vector marker only appears for SAST findings with call-path nodes.
        expandAllNodesInTree();
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        boolean hasSastResults = tree.collectRows().stream()
                .anyMatch(row -> row.trim().toLowerCase().startsWith("sast"));
        Assumptions.assumeTrue(hasSastResults, "Skipping TC34: no SAST results found in current scan.");

        navigate("sast", 4);
        hoverOverMarkerAndVerifyTooltip(VULNERABILITIES_TEXT, 1, EXPECTED_ATTACK_VECTOR_TOOLTIP_TEXT);
    }

    @Test
    @Video
    @DisplayName("TC31: Verify Remediation Examples Tab Shows Sample Vulnerable and Fixed Code")
    public void testRemediationExamplesTabShowsVulnerableAndFixedCode() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);

        // Same guard as TC28/TC32: the configured scan/project may not always contain SAST
        // results, and the Remediation Examples tab only appears nested under the Attack Vector
        // tab, which itself only appears for SAST findings with call-path nodes.
        expandAllNodesInTree();
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        boolean hasSastResults = tree.collectRows().stream()
                .anyMatch(row -> row.trim().toLowerCase().startsWith("sast"));
        Assumptions.assumeTrue(hasSastResults, "Skipping TC31: no SAST results found in current scan.");

        navigate("sast", 4);
        selectVulnerabilityAndVerifyRemediationExamplesTab(VULNERABILITIES_TEXT);
    }

    @Test
    @Video
    @DisplayName("TC39: Verify User Can Paste Scan ID Into Checkmarx Panel")
    public void testPasteScanId() {
        resetProjectSelection(1);
        enterScanId(Environment.SCAN_ID);

        waitForScanIdSelection();
        waitFor(() -> find(JTreeFixture.class, TREE).getData().getAll().size() > 0);
    }

    @Test
    @Video
    @DisplayName("TC40: Verify Scan Results Load After Entering Valid Scan ID")
    public void testScanResultsLoadAfterValidScanId() {
        resetProjectSelection(1);
        enterScanId(Environment.SCAN_ID);
        verifyResultsLoaded();
    }

    @Test
    @Video
    @DisplayName("TC58: Verify User Can Change Vulnerability Severity via Dropdown")
    public void testChangeVulnerabilitySeverityViaDropdown() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String currentSeverity = getCurrentSeverity();
        String targetSeverity = SEVERITY_OPTIONS.stream()
                .filter(severity -> !severity.equals(currentSeverity))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate severity value available to select."));

        changeSeverity(currentSeverity, targetSeverity);
        clickUpdate();
        verifySeverityUpdated();
    }

    @Test
    @Video
    @DisplayName("TC59: Verify User Can Update Vulnerability State")
    public void testUpdateVulnerabilityState() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String currentState = getCurrentState();
        String targetState = STATE_OPTIONS.stream()
                .filter(state -> !state.equals(currentState))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate state value available to select."));

        changeState(targetState);
        clickUpdate();
        verifyStateUpdated();
    }

    @Test
    @Video
    @DisplayName("TC60: Verify User Can Enter and Update Notes")
    public void testEnterAndUpdateNotes() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String note = UUID.randomUUID().toString();
        enterNote(note);
        clickUpdate();
        verifyNoteVisible();
    }

    @Test
    @Video
    @DisplayName("TC61: Verify Predicate Changes Persist to Future Scans")
    public void testPredicateChangesPersistToFutureScans() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String currentSeverity = getCurrentSeverity();
        String targetSeverity = SEVERITY_OPTIONS.stream()
                .filter(severity -> !severity.equals(currentSeverity))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate severity value available to select."));

        changeSeverity(currentSeverity, targetSeverity);
        clickUpdate();
        verifySeverityUpdated();

        triggerScanAndLoadNewResults();

        selectAllSeverities(true);
        navigate("Scan", 2);
        verifyPredicatePersisted();
    }

    @Test
    @Video
    @DisplayName("TC66: Verify UI Shows Correct Values After Refreshing Scan")
    public void testUiShowsCorrectValuesAfterRefreshingScan() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String currentSeverity = getCurrentSeverity();
        String targetSeverity = SEVERITY_OPTIONS.stream()
                .filter(severity -> !severity.equals(currentSeverity))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate severity value available to select."));

        changeSeverity(currentSeverity, targetSeverity);
        clickUpdate();
        verifySeverityUpdated();

        enterScanId(Environment.SCAN_ID);

        selectAllSeverities(true);
        navigate("Scan", 2);
        verifyPredicateValuesMatch();
    }

    @Test
    @Video
    @DisplayName("TC64: Verify Changes Are Only Applied When Update Button Is Clicked")
    public void testChangesOnlyAppliedWhenUpdateClicked() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        editAllFieldsWithoutUpdate();

        // Navigate away without clicking Update.
        find(JTreeFixture.class, TREE).clickRow(0);

        verifyNoChangesPersisted();
    }

    @Test
    @Video
    @DisplayName("TC29: Verify Changes Tab Displays Predicate Modification History")
    public void testChangesTabDisplaysPredicateModificationHistory() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String firstChange = addTriageComment();
        String secondChange = addTriageComment();
        String thirdChange = addTriageComment();

        verifyChangesTabContent(List.of(firstChange, secondChange, thirdChange));
    }

    @Test
    @Video
    @DisplayName("TC62: Verify All Predicate Changes Are Logged in Changes Tab")
    public void testAllPredicateChangesAreLoggedInChangesTab() {
        waitForScanIdSelection();
        selectAllSeverities(true);
        navigate("Scan", 2);
        selectFirstIacOrContainerVulnerability();

        String currentSeverity = getCurrentSeverity();
        String targetSeverity = SEVERITY_OPTIONS.stream()
                .filter(severity -> !severity.equals(currentSeverity))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate severity value available to select."));

        changeSeverity(currentSeverity, targetSeverity);
        clickUpdate();
        verifySeverityUpdated();

        String currentState = getCurrentState();
        String targetState = STATE_OPTIONS.stream()
                .filter(state -> !state.equals(currentState))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate state value available to select."));

        changeState(targetState);
        clickUpdate();
        verifyStateUpdated();

        String note = UUID.randomUUID().toString();
        enterNote(note);
        clickUpdate();
        verifyNoteVisible();

        verifyChangesTabContent(List.of(targetSeverity, targetState, note));
    }

}