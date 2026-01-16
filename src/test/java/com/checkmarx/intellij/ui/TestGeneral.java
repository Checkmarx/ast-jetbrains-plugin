package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.remoterobot.fixtures.JListFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static com.checkmarx.intellij.ui.PageMethods.ScanResultsPannelPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class TestGeneral extends BaseUITest {
    static List<String> defaultState = List.of("CONFIRMED", "TO_VERIFY", "URGENT", "NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE", "IGNORED", "NOT_IGNORED");
    EnumSet<Severity> exclude = EnumSet.of(Severity.MALICIOUS, Severity.INFO);


    @Language("XPath")
    public static String filterXPath(Severity filter) {
        return String.format("//div[@myicon='%s.svg']", filter.tooltipSupplier().get().toLowerCase());
    }

    @BeforeEach
    public void checkResults() {
        openScanResultsPanel();
        resetProjectSelection(1);
        enterScanIdAndSelect(true);
        validateProjectLoadedSuccessfully();
    }

    @Test
    @Video
    public void testEndToEnd() throws InterruptedException {
        checkAllTheComponentsInScanResultsPannel();
        validateResultPannel();
    }

    @Test
    @Video
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
    public void testInvalidScanId() {
        waitFor(() -> {
            enterScanIdAndSelect(false);

            JTreeFixture tree = find(JTreeFixture.class, TREE);
            boolean invalidMsgDisplayed = tree.getData().getAll().get(0).getText().contains(Bundle.message(Resource.INVALID_SCAN_ID));

            return tree.getData().getAll().size() == 1 && invalidMsgDisplayed;
        });
    }

    @Test
    @Video
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
    public void testClearSelection() {
        testSelection();
        resetProjectSelection(1);
    }
}
