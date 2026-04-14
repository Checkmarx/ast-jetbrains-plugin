package com.checkmarx.intellij.ast.test.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
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

public class TestGeneral extends com.checkmarx.intellij.ast.test.ui.BaseUITest {
    static List<String> defaultState = List.of("CONFIRMED", "TO_VERIFY", "URGENT", "NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE", "IGNORED", "NOT_IGNORED");
    EnumSet<SeverityFilter> exclude = EnumSet.of(SeverityFilter.MALICIOUS, SeverityFilter.INFO);


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

            Keyboard kb = new Keyboard(remoteRobot);
            kb.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
            kb.enterText("inva-lid");
            kb.key(KeyEvent.VK_ENTER);

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
    }
}
