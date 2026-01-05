package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.utils.Keyboard;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.*;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;

public class TestGeneral extends BaseUITest {
    List<String> defaultState = List.of("CONFIRMED", "TO_VERIFY", "URGENT", "NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE", "IGNORED", "NOT_IGNORED");

    @BeforeEach
    public void checkResults() {
        getResults();
    }

    @Test
    @Video
    public void testEndToEnd() {checkResultsPanel();
    }

    @Test
    @Video
    public void testFilters() {
        waitForScanIdSelection();

        // disable all severities and check for empty tree
        Arrays.stream(Severity.values()).forEach(severity -> toggleFilter(severity, false));
        navigate("Scan", 1);

        // enable all severities and check for at least 1 result
        Arrays.stream(Severity.values()).forEach(severity -> toggleFilter(severity, true));
        navigate("Scan", 2);
    }

    @Test
    @Video
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
    public void testSelection() {
        clearSelection();
        testSelectionAction(findSelection("Project"), "Project", Environment.PROJECT_NAME);
        testSelectionAction(findSelection("Branch"), "Branch", Environment.BRANCH_NAME);
        findLatestScanSelection();

        testSelectionAction(findSelection("Scan"), "Scan", Environment.SCAN_ID);
        waitFor(() -> find(JTreeFixture.class, TREE).getData().getAll().size() > 0);
    }

    @Test
    @Video
    public void testClearSelection() {
        testSelection();
        clearSelection();
    }

    private void findLatestScanSelection() {
        waitFor(() -> hasAnyComponent(String.format(LATEST_SCAN, Utils.formatLatest(true), Utils.formatLatest(true))));
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

        // enable all severities and check for at least 1 result
        Arrays.stream(Severity.values()).forEach(severity -> toggleFilter(severity, true));

        navigate("Scan", 2);
        navigate("sast", 4);
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        Optional<String> dsvwRow = tree.collectRows().stream().filter(treeRow -> treeRow.contains("Absolute_Path_Traversal")).findFirst();
        int dsvwRowIdx = dsvwRow.map(s -> tree.collectRows().indexOf(s)).orElse(-1);

        Assertions.assertTrue(dsvwRowIdx > 1);
        waitFor(() -> {
            tree.clickRow(dsvwRowIdx);
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
            return findAll(TRIAGE_LOW).size() > 0;
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
            return findAll(TRIAGE_CONFIRMED).size() > 0;
        });

        JTextFieldFixture commentField = find(JTextFieldFixture.class, TRIAGE_COMMENT);
        commentField.setText(commentUUID);

        JButtonFixture updateBtn = find(JButtonFixture.class, UPDATE_BTN);
        waitFor(() -> {
            updateBtn.click();
            return !updateBtn.isEnabled();
        });

        waitFor(updateBtn::isEnabled);

        waitFor(() -> {
            find(TAB_CHANGES).click();
            @Language("XPath") String fieldXpath = String.format(CHANGES_COMMENT, commentUUID, commentUUID);

            find(TAB_CHANGES_CONTENT).isShowing();
            return findAll(fieldXpath).size() > 0;
        });

        testFileNavigation();

        waitFor(() -> {
            find(TAB_LEARN_MORE).click();
            return findAll(TAB_RISK).size() > 0 && findAll(CAUSE).size() > 0 && findAll(TAB_RECOMMENDATIONS).size() > 0;
        });

        waitFor(() -> {
            find(TAB_RECOMMENDATIONS_EXAMPLES).click();
            return find(TAB_RECOMMENDATIONS_EXAMPLES).isShowing();
        });
    }

    private void collapse() {
        waitFor(() -> {
            click(COLLAPSE_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void vulnerabilityType() {
        groupAction("Vulnerability");
    }

    private void state() {
        groupAction("State");
    }

    private void file() {
        groupAction("File");
    }

    private void urgent() {
        openFilterBy();
        waitFor(() -> {
            enter("Urgent");
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void groupAction(String value) {
        openGroupBy();
        waitFor(() -> {
            enter(value);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void openFilterBy() {
        expand();
        waitFor(() -> {
            click(FILTER_BY_ACTION);
            List<JListFixture> myList = findAll(JListFixture.class, MY_LIST);
            return myList.size() == 1 && myList.get(0).findAllText().size() >= defaultState.size();
        });
    }

    private void openGroupBy() {
        expand();
        waitFor(() -> {
            click(GROUP_BY_ACTION);
            List<JListFixture> myList = findAll(JListFixture.class, MY_LIST);
            return myList.size() == 1 && myList.get(0).findAllText().size() == GroupBy.values().length - GroupBy.HIDDEN_GROUPS.size();
        });
    }

    @Language("XPath")
    public static String filterXPath(Severity filter) {
        return String.format("//div[@myicon='%s.svg']", filter.tooltipSupplier().get().toLowerCase());
    }
}
