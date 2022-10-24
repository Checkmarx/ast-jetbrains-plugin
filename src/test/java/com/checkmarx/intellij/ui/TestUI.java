package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.*;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.ResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.Keyboard;
import org.apache.commons.lang3.StringUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TestUI extends BaseUITest {

    @Test
    @Video
    public void testEndToEnd() {
        applySettings();
        getResults();
        checkResultsPanel();
    }

    @Test
    @Video
    public void testScaPanel() {
        applySettings();
        getResults();
        waitForScanIdSelection();

        navigate("Scan", 2);
        navigate("sca", 3);

        List<RemoteText> prefixNodes = find(JTreeFixture.class, TREE).getData()
                                                                     .getAll()
                                                                     .stream()
                                                                     .filter(t -> t.getText().startsWith("HIGH"))
                                                                     .collect(Collectors.toList());
        if (prefixNodes.size() != 0) {
            navigate("HIGH", 4);
        }
        navigate("Pip", 5);

        JTreeFixture tree = find(JTreeFixture.class, TREE);
        int row = -1;
        for (int i = 0; i < tree.collectRows().size(); i++) {
            if (tree.getValueAtRow(i).startsWith("CVE")) {
                row = i;
                break;
            }
        }
        // open first node of the opened result
        final int resultRow = row;
        Assertions.assertTrue(resultRow > 1);
        waitFor(() -> {
            tree.clickRow(resultRow);
            return findAll(LINK_LABEL).size() > 0;
        });

        Assertions.assertTrue(hasAnyComponent("//div[@disabledicon='magicResolve.svg']"));

        testFileNavigation();
    }

    @Test
    @Video
    public void testFilters() {
        applySettings();
        getResults();
        waitForScanIdSelection();
        // disable all severities and check for empty tree
        for (Severity s : Severity.values()) {
            toggleFilter(s, false);
        }
        navigate("Scan", 1);
        // enable all severities and check for at least 1 result
        for (Severity s : Severity.values()) {
            toggleFilter(s, true);
        }
        navigate("Scan", 2);
    }

    @Test
    @Video
    public void testInvalidAuth() {
        openSettings();

        setFields();
        setField(Constants.FIELD_NAME_API_KEY, "invalid");

        click(VALIDATE_BUTTON);

        waitFor(() -> !hasAnyComponent("//div[@accessiblename='Validating...']"));
        Assertions.assertFalse(hasAnyComponent("//div[@accessiblename='Successfully authenticated to AST server']"));
        click("//div[@text='OK']");
    }

    @Test
    @Video
    public void testInvalidScanId() {
        applySettings();
        setInvalidScanId();
    }

    @Test
    @Video
    public void testSelection() {
        applySettings();
        setInvalidScanId();
        clearSelection();
        testSelectionAction(this::findProjectSelection, "Project", Environment.PROJECT_NAME);
        testSelectionAction(this::findBranchSelection, "Branch", Environment.BRANCH_NAME);
        findLatestScanSelection();

        testSelectionAction(this::findScanSelection, "Scan", Environment.SCAN_ID);
        waitFor(() -> findAll(TREE).size() == 1 && checkTreeState(findAll(TREE).get(0)));
    }

    @Test
    @Video
    public void testClearSelection() {
        testSelection();
        clearSelection();
    }

    @Test
    @Video
    public void testScanButtonsDisabledWhenMissingProjectOrBranch() {
        if(triggerScanNotAllowed()) return;

        applySettings();
        clearSelection();
        Assertions.assertFalse(find(ActionButtonFixture.class, START_SCAN_BTN).isEnabled());
        Assertions.assertFalse(find(ActionButtonFixture.class, CANCEL_SCAN_BTN).isEnabled());
    }

    @Test
    @Video
    public void testCancelScan() {
        if(triggerScanNotAllowed()) return;

        applySettings();
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
        if(triggerScanNotAllowed()) return;

        applySettings();
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
    public void testTriggerScanAndLoadResults() throws InterruptedException {
        if(triggerScanNotAllowed()) return;

        applySettings();
        getResults();
        waitForScanIdSelection();
        findRunScanButtonAndClick();
        JTreeFixture treeBeforeScan = find(JTreeFixture.class, TREE);
        Assertions.assertTrue(treeBeforeScan.getValueAtRow(0).contains(Environment.SCAN_ID));
        waitFor(() -> hasAnyComponent("//div[@accessiblename.key='SCAN_FINISHED']"));
        find("//div[@class='LinkLabel']").click();
        wait(waitDuration.toMillis());
        waitFor(() -> findScanSelection().isEnabled() && findProjectSelection().isEnabled() && findBranchSelection().isEnabled());
        JTreeFixture treeAfterScan = find(JTreeFixture.class, TREE);
        // Assert that new results were loaded for a new scan id
        Assertions.assertFalse(treeAfterScan.getValueAtRow(0).contains(Environment.SCAN_ID));
    }

    private void findRunScanButtonAndClick() {
        ActionButtonFixture runScanBtn = find(ActionButtonFixture.class, START_SCAN_BTN);
        waitFor(runScanBtn::isEnabled);
        runScanBtn.click();
    }

    private boolean triggerScanNotAllowed(){
        return !hasAnyComponent(START_SCAN_BTN);
    }

    @NotNull
    private ActionButtonFixture findProjectSelection() {
        return findSelection("Project");
    }

    @NotNull
    private ActionButtonFixture findBranchSelection() {
        return findSelection("Branch");
    }

    @NotNull
    private ActionButtonFixture findScanSelection() {
        return findSelection("Scan");
    }

    private void findLatestScanSelection() {
        @Language("XPath") String xpath = String.format(
                "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']",
                Utils.formatLatest(true), Utils.formatLatest(true));
        waitFor(() -> hasAnyComponent(xpath));
    }

    private void testSelectionAction(Supplier<ActionButtonFixture> selectionSupplier, String prefix, String value) {
        waitFor(() -> {
            ActionButtonFixture selection = selectionSupplier.get();
            System.out.println(selection.getTemplatePresentationText());
            return selection.isEnabled() && selection.getTemplatePresentationText().contains(prefix);
        });
        waitFor(() -> {
            selectionSupplier.get().click();
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                    && findAll(JListFixture.class, "//div[@class='MyList']").get(0).findAllText().size() > 0;
        });
        enter(value);
    }

    @NotNull
    private ActionButtonFixture findSelection(String s) {
        @Language("XPath") String xpath = String.format(
                "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]",
                s);
        waitFor(() -> hasAnyComponent(xpath));
        return find(ActionButtonFixture.class, xpath);
    }

    private boolean hasSelection(String s) {
        return hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]",
                s));
    }

    private void clearSelection() {
        waitFor(() -> {
            if (hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Project: none']")
                    && findProjectSelection().isEnabled()
                    && hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Branch: none']")
                    && hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Scan: none']")
                    && !hasAnyComponent(TREE)
                    && StringUtils.isBlank(find(JTextFieldFixture.class, SCAN_FIELD).getText())) {
                log("clear selection done");
                return true;
            }
            ActionButtonFixture scanSelection = findScanSelection();
            ActionButtonFixture branchSelection = findBranchSelection();
            ActionButtonFixture projectSelection = findProjectSelection();
            if (!scanSelection.isShowing() || (scanSelection.hasText("Scan: ..."))
                    || (!branchSelection.isShowing() || branchSelection.hasText("Branch: ..."))
                    || (!projectSelection.isShowing() || projectSelection.hasText("Project: ..."))) {
                log("clear selection still in progress");
                return false;
            }
            log("clicking refresh action button");
            click("//div[@myicon='refresh.svg']");
            return false;
        });
    }

    private void applySettings() {
        openSettings();
        setFields();
        // click the validation button
        // wait for the validation success label
        click(VALIDATE_BUTTON);
        // the test fails if not found
        find(ComponentFixture.class, "//div[@accessiblename='Successfully authenticated to AST server']", waitDuration);
        click("//div[@text='OK']");
    }

    private void openSettings() {
        waitFor(() -> {
            if (hasAnyComponent(SETTINGS_ACTION)) {
                click(SETTINGS_ACTION);
            } else if (hasAnyComponent(SETTINGS_BUTTON)) {
                click(SETTINGS_BUTTON);
            }
            return hasAnyComponent(String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY));
        });
    }

    private void getResults() {
        waitFor(() -> hasAnyComponent(SCAN_FIELD));
        JTextFieldFixture scanField = find(JTextFieldFixture.class, SCAN_FIELD);
        waitFor(() -> hasSelection("Project") && hasSelection("Scan"));
        setInvalidScanId();
        scanField.setText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
        waitFor(() -> {
            if (scanField.isEnabled()) {
                scanField.click();
                if (scanField.getHasFocus()) {
                    new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
                }
            }
            return hasAnyComponent(String.format("//div[@class='Tree' and @visible_text='Scan %s']",
                    Environment.SCAN_ID));
        });
    }

    private void checkResultsPanel() {
        waitForScanIdSelection();
        // navigate the tree for a result
        expand();
        collapse();
        severity();

        // enable and disable state
        state();
        state();
        file();
        file();

        vulnerabilityType();
        urgent();


        navigate("Scan", 2);
        navigate("sast", 4);
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        int row = -1;
        for (int i = 0; i < tree.collectRows().size(); i++) {
            if (tree.getValueAtRow(i).contains("dsvw.py")) {
                row = i;
                break;
            }
        }
        // open first node of the opened result
        final int resultRow = row;
        Assertions.assertTrue(resultRow > 1); // at least scan (0) and sast (1)
        waitFor(() -> {
            tree.clickRow(resultRow);
            return findAll(LINK_LABEL).size() > 0;
        });

        String commentUUID = UUID.randomUUID().toString();

        waitFor(() -> {
            if (findAll(SEVERITY_COMBOBOX_ARROW).size() < 1) {
                return false;
            }
            find(SEVERITY_COMBOBOX_ARROW).click();

            List<JListFixture> lists = findAll(JListFixture.class, JLIST);
            if (lists.size() < 1) {
                return false;
            }
            JListFixture list = lists.get(0);
            if (list.isShowing()) {
                try {
                    list.clickItem("LOW", true);
                } catch (Throwable ice) {
                    return false;
                }
            }
            return findAll("//div[@class='ComboBox'][.//div[@visible_text='LOW']]").size() > 0;
        });

        waitFor(() -> {
            if (findAll(STATE_COMBOBOX_ARROW).size() < 1) {
                return false;
            }
            find(STATE_COMBOBOX_ARROW).click();

            if (findAll(JLIST).size() < 1) {
                return false;
            }
            JListFixture list = find(JListFixture.class, JLIST);
            if (list.isShowing()) {
                try {
                    list.clickItem("CONFIRMED", true);
                } catch (Throwable ice) {
                    return false;
                }
            }
            return findAll("//div[@class='ComboBox'][.//div[@visible_text='CONFIRMED']]").size() > 0;
        });

        find("//div[@class='JTextField']").click();
        enter(commentUUID);

        waitFor(() -> {
            find(JButtonFixture.class, "//div[@text='Update']").click();
            return !find(JButtonFixture.class, "//div[@text='Update']").isEnabled();
        });

        waitFor(() -> find(JButtonFixture.class, "//div[@text='Update']").isEnabled());

        waitFor(() -> {
            find("//div[@text='Changes']").click();
            @Language("XPath") String fieldXpath = String.format(CHANGES_COMMENT, commentUUID, commentUUID);

            find("//div[@accessiblename='Changes' and @accessiblename.key='changes.default.changelist.name CHANGES' and @class='JBTabbedPane']//div[@class='JPanel']").isShowing();
            return findAll(fieldXpath).size() > 0;
        });
        testFileNavigation();
        waitFor(() -> {
            find("//div[@text.key='LEARN_MORE']").click();
            return findAll("//div[@accessiblename.key='RISK']").size() > 0 && findAll("//div[@accessiblename.key='CAUSE']").size() > 0 && findAll("//div[@accessiblename.key='GENERAL_RECOMMENDATIONS']").size() > 0;
        });

        waitFor(() -> {
            find("//div[@text.key='CODE_SAMPLES']").click();
            return find("//div[@text.key='CODE_SAMPLES']").isShowing();
        });
    }

    private void waitForScanIdSelection() {
        // check scan selection for the scan id
        waitFor(() -> hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']",
                Environment.SCAN_ID,
                Environment.SCAN_ID)));
    }

    private void vulnerabilityType() {
        groupAction("Vulnerability");
    }

    private void severity() {
        groupAction("Severity");
    }

    private void state() {
        groupAction("State");
    }

    private void file() {
        groupAction("File");
    }

    private void urgent() {
        filterAction("Urgent");
    }

    private void groupAction(String value) {
        openGroupBy();
        waitFor(() -> {
            enter(value);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void filterAction(String value) {
        openFilterBy();
        waitFor(() -> {
            enter(value);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void openFilterBy() {
        expand();
        waitFor(() -> {
            click(FILTER_BY_ACTION);
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                    && findAll(JListFixture.class, "//div[@class='MyList']").get(0).findAllText().size()
                    == ResultState.values().length;
        });
    }

    private void openGroupBy() {
        expand();
        waitFor(() -> {
            click(GROUP_BY_ACTION);
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                    && findAll(JListFixture.class, "//div[@class='MyList']").get(0).findAllText().size()
                    == GroupBy.values().length - GroupBy.HIDDEN_GROUPS.size();
        });
    }

    private void collapse() {
        waitFor(() -> {
            click(COLLAPSE_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void expand() {
        waitFor(() -> {
            click(EXPAND_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() > 1;
        });
    }

    private void setInvalidScanId() {
        waitFor(() -> {
            click(SCAN_FIELD);
            return find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus();
        });
        find(JTextFieldFixture.class, SCAN_FIELD).setText("inva-lid");
        waitFor(() -> {
            click(SCAN_FIELD);
            if (!find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus()) {
                return false;
            }
            new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
            List<ComponentFixture> trees = findAll(TREE);
            if (trees.size() != 1) {
                return false;
            }
            ComponentFixture tree = trees.get(0);
            return tree.getData().getAll().size() == 1
                    && tree.getData()
                    .getAll()
                    .get(0)
                    .getText()
                    .contains(Bundle.message(
                            Resource.INVALID_SCAN_ID));
        });
    }

    private void navigate(String prefix, int minExpectedSize) {
        waitFor(() -> {
            List<RemoteText> prefixNodes = find(JTreeFixture.class, TREE).getData()
                                                                                    .getAll()
                                                                                    .stream()
                                                                                    .filter(t -> t.getText().startsWith(prefix))
                                                                                    .collect(Collectors.toList());
            if (prefixNodes.size() == 0) {
                return false;
            }
            prefixNodes.get(0).doubleClick();
            return find(JTreeFixture.class, TREE).findAllText().size() >= minExpectedSize;
        });
    }

    private void setFields() {
        setField(Constants.FIELD_NAME_API_KEY, Environment.API_KEY);
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");
    }

    private boolean checkTreeState(ComponentFixture tree) {
        return tree.getData().getAll().size() > 0 && !tree.getData()
                .getAll()
                .get(0)
                .getText()
                .contains(Bundle.message(Resource.GETTING_RESULTS_ERROR));
    }

    private void toggleFilter(Severity severity, boolean enabled) {
        @Language("XPath") String xpath = filterXPath(severity);
        waitFor(() -> {
            click(xpath);
            if (!hasAnyComponent(xpath)) {
                return false;
            }

            ActionButtonFixture filter = find(ActionButtonFixture.class, xpath);
            log(filter.popState().name());
            return filter.popState().equals(enabled
                    ? ActionButtonFixture.PopState.PUSHED
                    : ActionButtonFixture.PopState.POPPED);
        });
    }

    private static void testFileNavigation() {
        waitFor(() -> {
            click("//div[@class='BaseLabel']");
            findAll(LINK_LABEL).get(0).doubleClick();
            return hasAnyComponent(EDITOR);
        });
        Assertions.assertDoesNotThrow(() -> find(EditorFixture.class, EDITOR, waitDuration));
        //Confirming if editor is opened
        find(EditorFixture.class, EDITOR, waitDuration);
    }
}
