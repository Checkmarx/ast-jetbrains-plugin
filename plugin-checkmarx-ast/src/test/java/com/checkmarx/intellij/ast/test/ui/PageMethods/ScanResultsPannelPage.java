package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.utils.Keyboard;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.checkmarx.intellij.ast.test.ui.BaseUITest.focusCxWindow;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.TestConstants.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;

import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ScanResultsPannelPage {
    private static final Logger LOG = LogManager.getLogger(ScanResultsPannelPage.class);
    protected static ComponentFixture baseLabel;
    // Scan type ("engine") node, e.g. "sast (12)" — the parent of the first grouping level.
    private static final Pattern ENGINE_NODE_PATTERN =
            Pattern.compile("^(sast|sca|secret detection|IaC Security)\\s*\\(\\d+\\)$");
    // Any non-leaf node, e.g. "CRITICAL (7)" — group value plus its sub tree size.
    private static final Pattern GROUP_NODE_PATTERN = Pattern.compile("^(.+?)\\s*\\((\\d+)\\)$");
    // Engine node pattern including "containers" — used only to locate IaC/Container vulnerabilities.
    private static final Pattern IAC_OR_CONTAINER_ENGINE_PATTERN =
            Pattern.compile("^(sast|sca|secret detection|iac security|containers)\\s*\\(\\d+\\)$", Pattern.CASE_INSENSITIVE);
    static String[] SEVERITY_ICONS = {
            SEVERITY_LOW_ICON,
            SEVERITY_MEDIUM_ICON,
            SEVERITY_HIGH_ICON,
            SEVERITY_CRITICAL_ICON
    };
    // Expected order of severity group nodes under a scan type when grouped by Severity.
    private static final List<String> SEVERITY_GROUP_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    // Severity-shaped group node values — used to confirm no severity grouping remains.
    private static final Set<String> SEVERITY_SHAPED_GROUPS = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
    // Expected fixed order of the Group By popup menu items.
    public static final List<String> GROUP_BY_OPTIONS_ORDER =
            List.of("Severity", "State", "Vulnerability Type", "File", "Direct Dependency");
    // Raw state group node values (as rendered in the tree when grouped by "State").
    private static final Set<String> KNOWN_STATE_GROUPS = Set.of(
            "CONFIRMED", "TO_VERIFY", "URGENT", "NOT_EXPLOITABLE",
            "PROPOSED_NOT_EXPLOITABLE", "IGNORED", "NOT_IGNORED", "SCA_HIDE_DEV_TEST_DEPENDENCIES");

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
     * Starts a new scan, waits for it to finish, and loads its results into the tree.
     * <p>
     * Handles the optional "project does not match" popup that can appear locally by choosing
     * Run Local, then waits for the scan-finished notification and clicks the Load Results link.
     * Finally waits for the tree to refresh with a scan ID different from {@link Environment#SCAN_ID}.
     */
    public static void triggerScanAndLoadNewResults() {
        locateAndClickOnButton(START_SCAN_BTN);

        if (hasAnyComponent(PROJECT_DOES_NOT_MATCH) && hasAnyComponent(RUN_SCAN_LOCAL)) {
            log("Project mismatch popup detected. Clicking Run Local.");
            clickSafe(RUN_SCAN_LOCAL);
        }

        waitFor(() -> hasAnyComponent(SCAN_FINISHED) || hasAnyComponent(LOAD_RESULTS));
        waitFor(() -> hasAnyComponent(LOAD_RESULTS));
        clickSafe(LOAD_RESULTS);

        waitFor(() -> {
            JTreeFixture treeAfterScan = find(JTreeFixture.class, TREE);
            return !treeAfterScan.findAllText().isEmpty()
                    && treeAfterScan.getValueAtRow(0).startsWith("Scan")
                    && !treeAfterScan.getValueAtRow(0).contains(Environment.SCAN_ID);
        });

        log("New scan triggered and its results were loaded into the tree.");
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
     * Selects the group by option by clicking the group by action button and selecting the given option from the popup menu.
     *
     * @param groupByOption The group by option to select from the popup menu.
     */
    public static void selectGroupByOption(String groupByOption) {
        locateAndClickOnButton(GROUP_BY_ACTION);
        selectPopupMenuOption(groupByOption);
    }

    /**
     * Returns whether the given Group By option is currently checked in the Group By popup menu,
     * without changing the selection.
     *
     * @param groupByOption The label of the Group By option to check (e.g. "File").
     */
    public static boolean isGroupByOptionSelected(String groupByOption) {
        locateAndClickOnButton(GROUP_BY_ACTION);
        boolean isSelected = getMenuSelectedStatus(groupByOption);

        //Close popup without changing the selection
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        return isSelected;
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
     * Verifies that expanding all nodes in the results tree reveals every nested level -
     * top-level scan type/group nodes as well as their child and grandchild rows down to
     * individual findings - not just the first level. Repeatedly triggers the toolbar
     * "Expand All" action until the row count stabilizes, since deeper nodes can be populated
     * lazily and may need more than one click to fully cascade open.
     */
    public static void verifyExpandAllRevealsNestedChildren() {
        focusCxWindow();
        int rowCountBefore = find(JTreeFixture.class, TREE).getData().getAll().size();
        log("Row count before Expand All: " + rowCountBefore);

        int[] previousCount = {rowCountBefore};
        waitFor(() -> {
            expandAllNodesInTree();
            int currentCount = find(JTreeFixture.class, TREE).getData().getAll().size();
            boolean stable = currentCount == previousCount[0] && currentCount > rowCountBefore;
            previousCount[0] = currentCount;
            return stable;
        });

        List<String> rowsAfter = find(JTreeFixture.class, TREE).collectRows();
        int rowCountAfter = rowsAfter.size();
        log("Row count after Expand All: " + rowCountAfter);

        Assertions.assertTrue(rowCountAfter > rowCountBefore,
                "Expand All should reveal more rows in the results tree. Before="
                        + rowCountBefore + ", after=" + rowCountAfter);

        // Prove the expansion cascaded past the first level: at least one row must be a
        // leaf/child finding rather than a "label (count)" group node.
        boolean nestedLeafRowVisible = rowsAfter.stream()
                .map(String::trim)
                .filter(row -> !row.isEmpty())
                .anyMatch(row -> !GROUP_NODE_PATTERN.matcher(row).matches() && !row.startsWith("Scan "));
        Assertions.assertTrue(nestedLeafRowVisible,
                "Expand All should cascade down to leaf/child finding rows nested under the group nodes, not just the top-level groups.");

        log("Verified Expand All reveals every nested child node down to individual findings.");
    }

    /**
     * Verifies that clicking the toolbar's "Expand All" action expands every node in the results
     * tree - not just whichever node happened to be selected beforehand. Repeatedly triggers the
     * action until the row count stabilizes, since deeper nodes can be populated lazily, then
     * confirms that more than one top-level scan type/group node ended up with its children
     * revealed.
     */
    public static void verifyAllNodesExpanded() {
        focusCxWindow();
        int rowCountBefore = find(JTreeFixture.class, TREE).getData().getAll().size();
        log("Row count before Expand All: " + rowCountBefore);

        int[] previousCount = {rowCountBefore};
        waitFor(() -> {
            expandAllNodesInTree();
            int currentCount = find(JTreeFixture.class, TREE).getData().getAll().size();
            boolean stable = currentCount == previousCount[0] && currentCount > rowCountBefore;
            previousCount[0] = currentCount;
            return stable;
        });

        List<String> rowsAfter = find(JTreeFixture.class, TREE).collectRows();
        int rowCountAfter = rowsAfter.size();
        log("Row count after Expand All: " + rowCountAfter);

        Assertions.assertTrue(rowCountAfter > rowCountBefore,
                "Expand All should expand every node in the results tree, increasing the row count. Before="
                        + rowCountBefore + ", after=" + rowCountAfter);

        long distinctEngineNodesVisible = rowsAfter.stream()
                .map(String::trim)
                .filter(row -> ENGINE_NODE_PATTERN.matcher(row).matches())
                .distinct()
                .count();
        Assertions.assertTrue(distinctEngineNodesVisible > 1,
                "Expand All should affect the entire tree, revealing more than one top-level scan "
                        + "type/group node - not just the branch containing the previously selected node.");

        log("Verified Expand All expands every node in the results tree, not just the selected one.");
    }

    /**
     * Verifies that clicking the toolbar's "Collapse All" action collapses every node in the
     * results tree back down - not just whichever node happened to be selected beforehand.
     * Repeatedly triggers the action until the row count stabilizes, then confirms no leaf/child
     * finding rows remain visible under any branch.
     */
    public static void verifyAllNodesCollapsed() {
        focusCxWindow();
        int rowCountBefore = find(JTreeFixture.class, TREE).getData().getAll().size();
        log("Row count before Collapse All: " + rowCountBefore);

        int[] previousCount = {rowCountBefore};
        waitFor(() -> {
            collapseAllNodesInTree();
            int currentCount = find(JTreeFixture.class, TREE).getData().getAll().size();
            boolean stable = currentCount == previousCount[0] && currentCount < rowCountBefore;
            previousCount[0] = currentCount;
            return stable;
        });

        List<String> rowsAfter = find(JTreeFixture.class, TREE).collectRows();
        int rowCountAfter = rowsAfter.size();
        log("Row count after Collapse All: " + rowCountAfter);

        Assertions.assertTrue(rowCountAfter < rowCountBefore,
                "Collapse All should collapse every node in the results tree, decreasing the row count. Before="
                        + rowCountBefore + ", after=" + rowCountAfter);

        boolean leafRowStillVisible = rowsAfter.stream()
                .map(String::trim)
                .filter(row -> !row.isEmpty())
                .anyMatch(row -> !GROUP_NODE_PATTERN.matcher(row).matches() && !row.startsWith("Scan "));
        Assertions.assertFalse(leafRowStillVisible,
                "Collapse All should affect the entire tree, leaving no leaf/child finding rows "
                        + "visible under any branch - not just the branch containing the previously selected node.");

        log("Verified Collapse All collapses every node in the results tree, not just the selected one.");
    }

    /**
     * Enters the scan ID in the scan field and selects it in the UI.
     *
     * @param validScanId If true, enters a valid scan ID; otherwise, enters an invalid scan ID.
     */
    public static void enterScanIdAndSelect(boolean validScanId) {
        String scanId = validScanId ? Environment.SCAN_ID : "invalid-scan-id";
        enterScanId(scanId);
    }

    /**
     * Enters the given scan ID into the scan field (simulating a paste/typed value) and confirms it.
     *
     * @param scanId The scan ID to enter into the scan field.
     */
    public static void enterScanId(String scanId) {
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
     * Verifies that scan results have loaded after entering a scan ID: waits for the
     * "Getting results" loading indicator to clear, then asserts the results tree has rows.
     */
    public static void verifyResultsLoaded() {
        validateProjectLoadedSuccessfully();
        waitFor(() -> find(JTreeFixture.class, TREE).getData().getAll().size() > 0);
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
    }

    /**
     * Selects a vulnerability in the results tree by name.
     *
     * @param name The name of the vulnerability to select.
     */
    public static void selectVulnerability(String name) {
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        List<String> rows = tree.collectRows();

        log("Sent Vulnerability Name "+name);
        // Match the leaf row exactly. A "contains" match would also hit the group
        // header row (e.g. "Missing User Instruction (1)"), which sits above the
        // severity sub-node (e.g. "LOW (1)") and the actual leaf row in the tree.
        Optional<Integer> indexOpt = IntStream.range(0, rows.size())
                .filter(i -> rows.get(i).trim().equals(name))
                .boxed()
                .findFirst();

        Assertions.assertTrue(indexOpt.isPresent(), "Vulnerability not found: " + name);

        int index = indexOpt.get();

        waitFor(() -> {
            tree.clickRow(index);
            return findAll(LINK_LABEL).size() > 0;
        });
    }

    /**
     * Expands the results tree and selects the first vulnerability found under the "IaC Security"
     * or "containers" engine node (skipping "sca"), opening its detail side panel.
     */
    public static void selectFirstIacOrContainerVulnerability() {
        expandAllNodesInTree();

        JTreeFixture tree = find(JTreeFixture.class, TREE);
        List<String> rows = tree.collectRows();

        boolean insideTargetEngine = false;
        int rowIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            String text = rows.get(i).trim();
            Matcher engineMatch = IAC_OR_CONTAINER_ENGINE_PATTERN.matcher(text);
            if (engineMatch.matches()) {
                String engine = engineMatch.group(1).toLowerCase();
                insideTargetEngine = engine.equals("iac security") || engine.equals("containers");
                continue;
            }
            if (insideTargetEngine && !GROUP_NODE_PATTERN.matcher(text).matches()) {
                rowIdx = i;
                break;
            }
        }

        Assertions.assertTrue(rowIdx >= 0, "No IaC Security or Containers vulnerability found in results tree.");

        int selectedRow = rowIdx;
        waitFor(() -> {
            tree.clickRow(selectedRow);
            return !findAll(LINK_LABEL).isEmpty();
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
//    public static void verifyLearnMore() {
//        focusCxWindow();   // added: verifyChangeSaved() may leave the Cx tool window unfocused
//        waitFor(() -> {
//            find(TAB_LEARN_MORE).click();
//            return findAll(TAB_RISK).size() > 0 &&
//                    findAll(CAUSE).size() > 0 &&
//                    findAll(TAB_RECOMMENDATIONS).size() > 0;
//        });
//
//        waitFor(() -> {
//            find(TAB_RECOMMENDATIONS_EXAMPLES).click();
//            return find(TAB_RECOMMENDATIONS_EXAMPLES).isShowing();
//        });
//    }

    /**
     * Waits for the latest scan selection to appear in the UI.
     */
    public static void findLatestScanSelection() {
        waitFor(() -> hasAnyComponent(String.format(LATEST_SCAN, Utils.formatLatest(true), Utils.formatLatest(true))));
    }

    /**
     * Verifies that severity filters are in their code-defined default state:
     * CRITICAL/HIGH/MEDIUM/LOW selected, INFO not selected.
     * Caller is responsible for ensuring this reflects a true "no persisted selection" state
     * (see RemoteRobotUtils#resetGlobalFiltersToDefault).
     */
    public static void verifyDefaultSeverityFiltersSelected() {
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

    public static void verifyLocalBranchOptionPresent() {
        //Implementation to verify 'scan my local branch' option appears in the Branch dropdown
        List<String> branchOptions = getBranchDropdownOptions("Branch");

        //Close popup without selecting anything, so no branch gets committed
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        Assertions.assertTrue(
                branchOptions.stream().anyMatch(text -> text.equalsIgnoreCase(SCAN_MY_LOCAL_BRANCH_TEXT)),
                "'" + SCAN_MY_LOCAL_BRANCH_TEXT + "' option not found in Branch dropdown."
        );
    }


    /**
     * Verifies that the Branch dropdown for the given project contains exactly the expected branches
     * (ignoring the synthetic "scan my local branch" entry and list ordering).
     *
     * @param project             The project to select before opening the Branch dropdown.
     * @param branchName Comma-separated list of branch names expected to appear in the dropdown.
     */
    public static void verifyBranchListForProject(String project, String branchName ) {
        List<String> branchOptions = getBranchDropdownOptions("Branch");

        //Close popup without selecting anything, so no branch gets committed
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        List<String> actualBranches = branchOptions.stream()
                .filter(text -> !text.equalsIgnoreCase(SCAN_MY_LOCAL_BRANCH_TEXT))
                .sorted()
                .collect(Collectors.toList());

        List<String> expectedBranches = Arrays.stream(branchName .split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        Assertions.assertEquals(
                expectedBranches,
                actualBranches,
                "Branch dropdown for project '" + project + "' does not match expected branches."
        );
    }


    public static void verifyVulnerabilityCountPerSeverity(Map<String, Integer> expectedAll) {
        focusCxWindow();

        // Force a fresh "group by severity" render: deselect, then reselect.
        selectGroupByOption("Severity");
        selectGroupByOption("Severity");

        // Group By change collapses the tree back to the root — re-expand to reveal scan type nodes.
        navigate("Scan", 2);

        JTreeFixture tree = find(JTreeFixture.class, TREE);
        Pattern scanTypePattern = Pattern.compile("^(sast|sca|secret detection|IaC Security)\\s*\\((\\d+)\\)$");
        Pattern severityPattern = Pattern.compile("^(MALICIOUS|CRITICAL|HIGH|MEDIUM|LOW|INFO)\\s*\\((\\d+)\\)$");

        String firstScanType = tree.getData().getAll().stream()
                .map(RemoteText::getText).map(String::trim)
                .filter(t -> scanTypePattern.matcher(t).matches())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No scan type nodes found in results tree."));

        navigate(firstScanType, 1);

        List<RemoteText> nodes = tree.getData().getAll();
        int start = IntStream.range(0, nodes.size())
                .filter(i -> nodes.get(i).getText().trim().equals(firstScanType))
                .findFirst().orElse(-1);

        Map<String, Integer> actual = new HashMap<>();
        for (int i = start + 1; i < nodes.size(); i++) {
            String text = nodes.get(i).getText().trim();
            if (scanTypePattern.matcher(text).matches()) break; // next scan type sibling
            Matcher m = severityPattern.matcher(text);
            if (m.matches()) actual.merge(m.group(1), Integer.parseInt(m.group(2)), Integer::sum);
        }

        Assertions.assertFalse(actual.isEmpty(), "No severity nodes found under scan type '" + firstScanType + "'.");

        Map<String, Integer> expected = new HashMap<>();
        actual.keySet().forEach(s -> { if (expectedAll.containsKey(s)) expected.put(s, expectedAll.get(s)); });

        Assertions.assertEquals(expected, actual,
                "Vulnerability counts per severity for '" + firstScanType + "' do not match expected CxOne application data.");
    }

    /**
     First reads whichever scan-type nodes are actually present in the tree,
     then only compares those against the hardcoded expected map — works
     regardless of how many scan types/scans the project has.
     **/
    public static void verifyScanTypeCountLabels(Map<String, Integer> expectedAll) {
        focusCxWindow();
        JTreeFixture tree = find(JTreeFixture.class, TREE);

        Pattern scanTypeCountPattern = Pattern.compile("^(sast|sca|secret detection|IaC Security)\\s*\\((\\d+)\\)$");

        Map<String, Integer> actual = tree.getData()
                .getAll()
                .stream()
                .map(RemoteText::getText)
                .map(String::trim)
                .map(scanTypeCountPattern::matcher)
                .filter(Matcher::matches)
                .collect(Collectors.toMap(
                        m -> m.group(1),
                        m -> Integer.parseInt(m.group(2)),
                        Integer::sum));

        Assertions.assertFalse(actual.isEmpty(), "No scan type nodes found in results tree.");

        Map<String, Integer> expected = new HashMap<>();
        for (String scanType : actual.keySet()) {
            if (expectedAll.containsKey(scanType)) {
                expected.put(scanType, expectedAll.get(scanType));
            }
        }

        Assertions.assertEquals(expected, actual,
                "Vulnerability counts per scan type do not match expected CxOne application data.");
    }


    /**
      Verifies that "Severity" is the pre-selected option when the Group By dropdown is opened.
     **/
    public static void verifyDefaultGroupByIsSeverity() {
        locateAndClickOnButton(GROUP_BY_ACTION);
        boolean isSeveritySelected = getMenuSelectedStatus("Severity");

        //Close popup without changing the selection
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        Assertions.assertTrue(isSeveritySelected, "'Severity' should be the default Group By selection.");
    }

    /**
     * Verifies that the Group By popup menu items are rendered in the given fixed order.
     *
     * @param expected The expected order of the Group By popup menu item labels.
     */
    public static void verifyGroupByOptionsOrder(List<String> expected) {
        locateAndClickOnButton(GROUP_BY_ACTION);
        List<String> actual = getMenuTextItems();

        //Close popup without changing the selection
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        Assertions.assertEquals(expected, actual,
                "Group By popup menu items are not rendered in the expected order.");
    }


    /**
     * Verifies that vulnerabilities under the first scan type are grouped in the same
     * order as the current Group By selection (CRITICAL, HIGH, MEDIUM, LOW).
     * @param groupByOption The Group By option currently selected (e.g. "Severity").
     */
    public static void verifyGroupingMatchesSelection(String groupByOption) {
        focusCxWindow();
        navigate("Scan", 2);

        List<String> actualOrder = expandFirstScanTypeAndGetGroupOrder();

        if (actualOrder.isEmpty()) {
            // Force a fresh grouped render: deselect, then reselect.
            selectGroupByOption(groupByOption);
            selectGroupByOption(groupByOption);

            // Group By change collapses the tree back to the root - re-expand.
            navigate("Scan", 2);
            actualOrder = expandFirstScanTypeAndGetGroupOrder();
        }

        Assertions.assertFalse(actualOrder.isEmpty(),
                "No " + groupByOption + " group nodes found under the first scan type.");

        List<String> expectedOrder = SEVERITY_GROUP_ORDER.stream()
                .filter(actualOrder::contains)
                .collect(Collectors.toList());

        Assertions.assertEquals(expectedOrder, actualOrder,
                "Vulnerabilities under the first scan type are not grouped in " + groupByOption + " order.");
    }

    /**
     * Expands the first scan type node in the results tree and returns the severity
     * group nodes found directly under it, in the order they appear in the tree.
     */
    private static List<String> expandFirstScanTypeAndGetGroupOrder() {
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        String firstScanType = tree.getData().getAll().stream()
                .map(RemoteText::getText).map(String::trim)
                .filter(t -> ENGINE_NODE_PATTERN.matcher(t).matches())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No scan type nodes found in results tree."));

        navigate(firstScanType, 1);

        List<RemoteText> nodes = find(JTreeFixture.class, TREE).getData().getAll();
        int start = IntStream.range(0, nodes.size())
                .filter(i -> nodes.get(i).getText().trim().equals(firstScanType))
                .findFirst().orElse(-1);

        List<String> order = new ArrayList<>();
        for (int i = start + 1; i < nodes.size(); i++) {
            String text = nodes.get(i).getText().trim();
            if (ENGINE_NODE_PATTERN.matcher(text).matches()) break; // next scan type sibling
            Matcher m = GROUP_NODE_PATTERN.matcher(text);
            if (m.matches() && SEVERITY_GROUP_ORDER.contains(m.group(1)) && !order.contains(m.group(1))) {
                order.add(m.group(1));
            }
        }
        return order;
    }

    /**
     * Verifies that toggling a specific severity filter icon hides/shows the corresponding nodes in the tree.
     * Steps: enable all severities, expand tree, count nodes of target severity,
     * disable target severity, verify those nodes disappear, re-enable, verify they reappear.
     * Restores every severity filter to the selection state it had prior to this method running.
     */
    public static void verifySeverityFilterToggles(String severityIcon, String severityName) {
        Map<String, Boolean> originalSeverityStates = new HashMap<>();
        for (String icon : SEVERITY_ICONS) {
            originalSeverityStates.put(icon, isComponentSelected(icon));
        }

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

        // Restore every severity filter to its state prior to this method running
        originalSeverityStates.forEach(ScanResultsPannelPage::changeSeveritySelection);
        sleep(2000);
        log("Severity filters restored to their prior state");
    }

    /**
     * Verifies that toggling several severity filter icons off at once hides all of their
     * corresponding nodes in the tree together, while severities left enabled remain visible.
     * Steps: enable all severities, expand tree, disable every target severity, verify their
     * nodes disappear as a combined set, then re-enable them and verify they reappear.
     * Restores every severity filter to the selection state it had prior to this method running.
     *
     * @param severitiesToDisable map of severity icon xpath to the severity name it renders in the tree.
     */
    public static void verifyCombinedSeverityFilterToggle(Map<String, String> severitiesToDisable) {
        Map<String, Boolean> originalSeverityStates = new HashMap<>();
        for (String icon : SEVERITY_ICONS) {
            originalSeverityStates.put(icon, isComponentSelected(icon));
        }

        selectAllSeverities(true);
        expandAllNodesInTree();

        // Count tree nodes containing each target severity name before disabling
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        List<String> rowsBefore = tree.collectRows();
        Map<String, Long> countsBefore = severitiesToDisable.values().stream()
                .collect(Collectors.toMap(name -> name, name -> rowsBefore.stream()
                        .filter(row -> row.toLowerCase().contains(name))
                        .count()));
        log("Severity node counts before disabling filters: " + countsBefore);

        // Disable every target severity
        severitiesToDisable.keySet().forEach(icon -> changeSeveritySelection(icon, false));

        // Wait for tree to collapse/reload after filter change, then expand
        sleep(2000);
        expandAllNodesInTree();

        // Verify nodes of every disabled severity are no longer visible, together
        waitFor(() -> {
            JTreeFixture updatedTree = find(JTreeFixture.class, TREE);
            List<String> rows = updatedTree.collectRows();
            return severitiesToDisable.values().stream()
                    .allMatch(name -> rows.stream().noneMatch(row -> row.toLowerCase().contains(name)));
        });
        log(severitiesToDisable.values() + " nodes hidden after combined filtering");

        // Re-enable the severities
        severitiesToDisable.keySet().forEach(icon -> changeSeveritySelection(icon, true));

        // Wait for tree to collapse/reload after filter change, then expand
        sleep(2000);
        expandAllNodesInTree();

        // Verify nodes reappear for severities that had results before
        waitFor(() -> {
            JTreeFixture restoredTree = find(JTreeFixture.class, TREE);
            List<String> rows = restoredTree.collectRows();
            return countsBefore.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .allMatch(entry -> rows.stream().anyMatch(row -> row.toLowerCase().contains(entry.getKey())));
        });
        log(severitiesToDisable.values() + " nodes visible again after re-enabling filters");

        // Restore every severity filter to its state prior to this method running
        originalSeverityStates.forEach(ScanResultsPannelPage::changeSeveritySelection);
        sleep(2000);
        log("Severity filters restored to their prior state");
    }

    /**
     * Sets the state filter so that only the given state is selected.
     * <p>
     * Step 1: deselects every other currently-selected state option (leaving the target as-is
     * if it's already selected), retrying until no other option remains selected. Step 2:
     * selects the target state if it isn't already selected.
     *
     * @param stateLabel The label of the state filter to select (e.g. "To Verify").
     */
    public static void selectStateFilter(String stateLabel) {
        locateAndClickOnButton(FILTER_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        // Step 1: deselect all other selected options, retrying until none remain selected
        waitFor(() -> {
            List<String> othersSelected = getMenuOptionsWithState().entrySet().stream()
                    .filter(entry -> entry.getValue() && !entry.getKey().equals(stateLabel))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (othersSelected.isEmpty()) {
                return true;
            }

            for (String option : othersSelected) {
                selectPopupMenuOption(option);
                locateAndClickOnButton(FILTER_BY_ACTION);
                waitFor(() -> hasAnyComponent(MY_LIST));
            }
            return false;
        });

        // Step 2: select the target state if it isn't already selected
        if (!getMenuSelectedStatus(stateLabel)) {
            selectPopupMenuOption(stateLabel);
        }
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        log("State filter set to only: " + stateLabel);
    }

    /**
     * Opens the Filter By popup menu and captures the selected/unselected state of every
     * option in it, without changing any selection.
     *
     * @return A map of filter menu option label to whether it was selected.
     */
    public static Map<String, Boolean> captureFilterMenuState() {
        locateAndClickOnButton(FILTER_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        Map<String, Boolean> state = getMenuOptionsWithState();

        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);
        log("Captured Filter By menu state: " + state);
        return state;
    }

    /**
     * Restores the Filter By popup menu options to a previously captured selection state,
     * toggling only the options whose current selection differs from the captured one.
     *
     * @param originalStates A map of filter menu option label to its previously captured selected state,
     *                        as returned by {@link #captureFilterMenuState()}.
     */
    public static void restoreFilterMenuState(Map<String, Boolean> originalStates) {
        locateAndClickOnButton(FILTER_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        originalStates.forEach((label, wasSelected) -> {
            if (getMenuSelectedStatus(label) != wasSelected) {
                selectPopupMenuOption(label);
                locateAndClickOnButton(FILTER_BY_ACTION);
                waitFor(() -> hasAnyComponent(MY_LIST));
            }
        });

        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);
        log("Filter By menu state restored to: " + originalStates);
    }

    /**
     * Navigates to the results tree, groups it by "State", and verifies that the only state
     * group node(s) present anywhere in the tree correspond to the given state label.
     *
     * @param stateLabel The label of the state expected to be the only one visible (e.g. "To Verify").
     */
    public static void verifyOnlyStateVisible(String stateLabel) {
        focusCxWindow();
        String expectedState = stateLabel.replace(" ", "_").toUpperCase();

        selectGroupByOption("State");
        navigate("Scan", 2);
        expandAllNodesInTree();

        JTreeFixture tree = find(JTreeFixture.class, TREE);
        Set<String> statesFound = tree.getData().getAll().stream()
                .map(RemoteText::getText).map(String::trim)
                .map(GROUP_NODE_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(m -> m.group(1))
                .filter(KNOWN_STATE_GROUPS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Assertions.assertFalse(statesFound.isEmpty(), "No state group nodes found in results tree.");
        Assertions.assertEquals(Set.of(expectedState), statesFound,
                "Only '" + stateLabel + "' vulnerabilities should be visible, but found state groups: " + statesFound);

        log("Verified only '" + stateLabel + "' state vulnerabilities are visible in results tree.");
    }

    /**
     * Toggles a custom state filter (e.g. "SCA Hide Dev & Test Dependencies") in the Filter By
     * popup menu to the desired enabled state, leaving all other selected filters untouched.
     *
     * @param filterLabel The label of the custom state filter as shown in the Filter By menu.
     * @param enable      True to select/enable the filter, false to deselect/disable it.
     */
    public static void toggleCustomStateFilter(String filterLabel, boolean enable) {
        locateAndClickOnButton(FILTER_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        boolean isSelected = getMenuSelectedStatus(filterLabel);
        if (isSelected != enable) {
            selectPopupMenuOption(filterLabel);
            log("Custom state filter '" + filterLabel + "' toggled to " + enable);
        } else {
            new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);
            log("Custom state filter '" + filterLabel + "' is already " + enable);
        }
    }

    /**
     * Deselects the given Group By option in the popup menu if it is currently selected, leaving
     * all other Group By selections untouched. One-directional version of
     * {@link #toggleCustomStateFilter(String, boolean)} - only unchecks, never checks.
     *
     * @param groupByOption The label of the Group By option to deselect (e.g. "File").
     */
    public static void uncheckGroupByOption(String groupByOption) {
        locateAndClickOnButton(GROUP_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        if (getMenuSelectedStatus(groupByOption)) {
            selectPopupMenuOption(groupByOption);
            log("Group By option '" + groupByOption + "' unchecked.");
        } else {
            new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);
            log("Group By option '" + groupByOption + "' is already unchecked.");
        }
    }

    /**
     * Verifies that when "File" is deselected in the Group By menu (its default state, since
     * {@code GroupBy.FILE} is not part of {@code GroupBy.DEFAULT_GROUP_BY}), the results tree
     * contains no file-level grouping nodes.
     */
    public static void verifyNoFileGroupingWhenDeselected() {
        focusCxWindow();
        Assertions.assertFalse(isGroupByOptionSelected(FILE_TEXT),
                "'File' Group By should be deselected by default.");

        navigate("Scan", 2);
        expandAllNodesInTree();

        List<String> fileShapedNodes = find(JTreeFixture.class, TREE).getData().getAll().stream()
                .map(RemoteText::getText).map(String::trim)
                .map(GROUP_NODE_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(m -> m.group(1))
                .filter(ScanResultsPannelPage::looksLikeFilePath)
                .collect(Collectors.toList());

        Assertions.assertTrue(fileShapedNodes.isEmpty(),
                "No file-level grouping nodes should be present when 'File' is deselected, but found: " + fileShapedNodes);

        log("Verified no file grouping nodes are present when 'File' is deselected.");
    }

    /**
     * Verifies that when "Severity" is deselected in the Group By menu (its default state is
     * checked, since {@code GroupBy.SEVERITY} is part of {@code GroupBy.DEFAULT_GROUP_BY}), the
     * results tree re-groups by the remaining default ({@code GroupBy.VULNERABILITY_TYPE_NAME})
     * and contains no severity-level grouping nodes.
     */
    public static void verifyNoSeverityGroupingWhenDeselected() {
        focusCxWindow();
        boolean wasSelected = isGroupByOptionSelected(SEVERITY_TEXT);

        try {
            if (wasSelected) {
                uncheckGroupByOption(SEVERITY_TEXT);
            }

            navigate("Scan", 2);
            expandAllNodesInTree();

            List<String> severityShapedNodes = find(JTreeFixture.class, TREE).getData().getAll().stream()
                    .map(RemoteText::getText).map(String::trim)
                    .map(GROUP_NODE_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(m -> m.group(1))
                    .filter(SEVERITY_SHAPED_GROUPS::contains)
                    .collect(Collectors.toList());

            Assertions.assertTrue(severityShapedNodes.isEmpty(),
                    "No severity-level grouping nodes should be present when 'Severity' is deselected, but found: " + severityShapedNodes);

            log("Verified no severity grouping nodes are present when 'Severity' is deselected.");
        } finally {
            if (wasSelected && !isGroupByOptionSelected(SEVERITY_TEXT)) {
                selectGroupByOption(SEVERITY_TEXT);
                log("Restored 'Severity' Group By option to its original (selected) state.");
            }
        }
    }

    /**
     * Verifies that when "State" is deselected in the Group By menu (its default state, since
     * {@code GroupBy.STATE} is not part of {@code GroupBy.DEFAULT_GROUP_BY}), the results tree
     * contains no state-level grouping nodes.
     */
    public static void verifyNoStateGroupingWhenDeselected() {
        focusCxWindow();
        boolean wasSelected = isGroupByOptionSelected(STATE_TEXT);

        try {
            if (wasSelected) {
                uncheckGroupByOption(STATE_TEXT);
            }

            navigate("Scan", 2);
            expandAllNodesInTree();

            List<String> stateShapedNodes = find(JTreeFixture.class, TREE).getData().getAll().stream()
                    .map(RemoteText::getText).map(String::trim)
                    .map(GROUP_NODE_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(m -> m.group(1))
                    .filter(KNOWN_STATE_GROUPS::contains)
                    .collect(Collectors.toList());

            Assertions.assertTrue(stateShapedNodes.isEmpty(),
                    "No state-level grouping nodes should be present when 'State' is deselected, but found: " + stateShapedNodes);

            log("Verified no state grouping nodes are present when 'State' is deselected.");
        } finally {
            if (wasSelected && !isGroupByOptionSelected(STATE_TEXT)) {
                selectGroupByOption(STATE_TEXT);
                log("Restored 'State' Group By option to its original (selected) state.");
            }
        }
    }

    /**
     * @return true if the given group node label is shaped like a file path (contains a path
     * separator or ends with a file extension).
     */
    private static boolean looksLikeFilePath(String label) {
        return label.contains("/") || label.contains("\\") || label.matches(".*\\.[a-zA-Z0-9]{1,6}$");
    }

    /**
     * Counts the tree rows nested under the given scan type node (e.g. "sca"), stopping at the
     * next sibling scan type node. Assumes the tree is already fully expanded.
     *
     * @param scanTypeLabel The scan type node label to count rows under (case-insensitive prefix match).
     * @return Number of rows found under the scan type node.
     */
    public static int countRowsUnderScanType(String scanTypeLabel) {
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        List<RemoteText> nodes = tree.getData().getAll();

        int start = IntStream.range(0, nodes.size())
                .filter(i -> nodes.get(i).getText().trim().toLowerCase().startsWith(scanTypeLabel.toLowerCase()))
                .findFirst().orElse(-1);
        Assertions.assertTrue(start >= 0, "Scan type node not found in results tree: " + scanTypeLabel);

        int count = 0;
        for (int i = start + 1; i < nodes.size(); i++) {
            String text = nodes.get(i).getText().trim();
            if (ENGINE_NODE_PATTERN.matcher(text).matches()) break; // next scan type sibling
            count++;
        }
        return count;
    }

    /**
     * Verifies that the "SCA Hide Dev & Test Dependencies" filter is unchecked by default when the
     * Filter By popup is opened fresh, guarding against a regression where the default flips to "on".
     */
    public static void verifyScaDevTestFilterUncheckedByDefault() {
        focusCxWindow();
        locateAndClickOnButton(FILTER_BY_ACTION);
        waitFor(() -> hasAnyComponent(MY_LIST));

        boolean isSelected = getMenuSelectedStatus(SCA_DEV_TEST_FILTER_TEXT);

        // Close popup without changing the selection
        new Keyboard(remoteRobot).key(KeyEvent.VK_ESCAPE);

        Assertions.assertFalse(isSelected,
                "'" + SCA_DEV_TEST_FILTER_TEXT + "' filter should be unchecked by default.");
        log("Verified '" + SCA_DEV_TEST_FILTER_TEXT + "' filter is unchecked by default.");
    }

    /**
     * Verifies that enabling the "SCA Hide Dev & Test Dependencies" filter reduces the number of
     * nodes shown under the "sca" scan type node, and restores the filter to its disabled state afterward.
     */
    public static void verifyScaDevTestDependencyFilterHidesNodes() {
        focusCxWindow();
        selectAllSeverities(true);
        navigate("Scan", 2);
        expandAllNodesInTree();

        // Ensure the filter starts disabled so the "before" count reflects all dependencies
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, false);
        sleep(2000);
        navigate("Scan", 2);
        expandAllNodesInTree();
        int countBefore = countRowsUnderScanType("sca");
        log("SCA tree node count with Dev & Test filter disabled: " + countBefore);

        // Enable the filter and re-count
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, true);
        sleep(2000);
        navigate("Scan", 2);
        expandAllNodesInTree();
        int countAfter = countRowsUnderScanType("sca");
        log("SCA tree node count with Dev & Test filter enabled: " + countAfter);

        Assertions.assertTrue(countAfter < countBefore,
                "SCA tree node count should decrease after enabling 'Hide Dev & Test Dependencies' filter. Before: "
                        + countBefore + ", After: " + countAfter);

        // Restore filter to its default disabled state
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, false);
        log("Verified SCA Dev & Test filter hides dev/test dependencies (before: " + countBefore + ", after: " + countAfter + ")");
    }

    /**
     * Verifies that toggling the "SCA Hide Dev & Test Dependencies" filter has no effect on a
     * scan that contains no SCA results at all (e.g. a SAST-only scan): the row count under the
     * "sast" scan type node must stay identical before and after enabling the filter, and toggling
     * it must not error out just because there is no "sca" node in the tree.
     */
    public static void verifyScaDevTestFilterNoEffectOnSastOnlyScan() {
        focusCxWindow();
        selectAllSeverities(true);
        navigate("Scan", 2);
        expandAllNodesInTree();

        // Ensure the filter starts disabled so the "before" count reflects the default state
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, false);
        sleep(2000);
        navigate("Scan", 2);
        expandAllNodesInTree();
        int countBefore = countRowsUnderScanType("sast");
        log("SAST tree node count with Dev & Test filter disabled: " + countBefore);

        // Enable the filter and re-count - must not throw even though no "sca" node exists
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, true);
        sleep(2000);
        navigate("Scan", 2);
        expandAllNodesInTree();
        int countAfter = countRowsUnderScanType("sast");
        log("SAST tree node count with Dev & Test filter enabled: " + countAfter);

        Assertions.assertEquals(countBefore, countAfter,
                "SAST tree node count should be unaffected by the 'Hide Dev & Test Dependencies' filter "
                        + "on a SAST-only scan. Before: " + countBefore + ", After: " + countAfter);

        // Restore filter to its default disabled state
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, false);
        log("Verified SCA Dev & Test filter has no effect on SAST-only scan (before: " + countBefore + ", after: " + countAfter + ")");
    }

    /**
     * Verifies that on a combined scan (SAST + SCA + IaC Security), enabling the
     * "SCA Hide Dev & Test Dependencies" filter reduces the row count only under the "sca" scan
     * type node, while the "sast" and "IaC Security" scan type node row counts stay identical.
     */
    public static void verifyScaDevTestFilterOnCombinedScan() {
        focusCxWindow();
        selectAllSeverities(true);
        navigate("Scan", 2);
        expandAllNodesInTree();

        // Ensure the filter starts disabled so the "before" counts reflect all dependencies
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, false);
        sleep(2000);
        navigate("Scan", 2);
        expandAllNodesInTree();
        int scaCountBefore = countRowsUnderScanType("sca");
        int sastCountBefore = countRowsUnderScanType("sast");
        int iacCountBefore = countRowsUnderScanType("IaC Security");
        log("Combined scan tree node counts with Dev & Test filter disabled - sca: " + scaCountBefore
                + ", sast: " + sastCountBefore + ", IaC Security: " + iacCountBefore);

        // Enable the filter and re-count
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, true);
        sleep(2000);
        navigate("Scan", 2);
        expandAllNodesInTree();
        int scaCountAfter = countRowsUnderScanType("sca");
        int sastCountAfter = countRowsUnderScanType("sast");
        int iacCountAfter = countRowsUnderScanType("IaC Security");
        log("Combined scan tree node counts with Dev & Test filter enabled - sca: " + scaCountAfter
                + ", sast: " + sastCountAfter + ", IaC Security: " + iacCountAfter);

        Assertions.assertTrue(scaCountAfter < scaCountBefore,
                "SCA tree node count should decrease after enabling 'Hide Dev & Test Dependencies' filter. Before: "
                        + scaCountBefore + ", After: " + scaCountAfter);
        Assertions.assertEquals(sastCountBefore, sastCountAfter,
                "SAST tree node count should be unaffected by the 'Hide Dev & Test Dependencies' filter. Before: "
                        + sastCountBefore + ", After: " + sastCountAfter);
        Assertions.assertEquals(iacCountBefore, iacCountAfter,
                "IaC Security tree node count should be unaffected by the 'Hide Dev & Test Dependencies' filter. Before: "
                        + iacCountBefore + ", After: " + iacCountAfter);

        // Restore filter to its default disabled state
        toggleCustomStateFilter(SCA_DEV_TEST_FILTER_TEXT, false);
        log("Verified SCA Dev & Test filter is scoped to SCA results on a combined scan (sca before: "
                + scaCountBefore + ", after: " + scaCountAfter + ")");
    }

}
