package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.utils.Keyboard;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;

public class TestUI extends BaseUITest {

    /**
     * Apply valid settings, get results and test the results UI
     */
    @Test
    public void testEndToEnd() {
        applySettings();
        getResults();
        checkResultsPanel();
    }

    @Test
    public void testInvalidAuth() {
        openSettings();

        setFields();
        find(JCheckboxFixture.class,
             String.format(FIELD_NAME, Constants.FIELD_NAME_USE_AUTH_URL),
             waitDuration).setValue(true);
        setField(Constants.FIELD_NAME_AUTH_URL, "http://wrongauth");

        find(JButtonFixture.class, VALIDATE_BUTTON).click();

        waitFor(() -> !hasAnyComponent("//div[@accessiblename.key='VALIDATE_IN_PROGRESS']"));
        Assertions.assertFalse(hasAnyComponent("//div[@accessiblename.key='VALIDATE_SUCCESS']"));
        find("//div[@text.key='button.cancel']").click();
    }

    @Test
    public void testInvalidScanId() {
        applySettings();
        setInvalidScanId();
    }

    @Test
    public void testSelection() {
        applySettings();
        setInvalidScanId();
        waitFor(() -> findProjectSelection().isEnabled());
        waitFor(() -> {
            findProjectSelection().click();
            return find(JListFixture.class,
                        "//div[@class='MyList']").findAllText().size() > 0;
        });
        new Keyboard(remoteRobot).enterText(Environment.PROJECT_NAME);
        new Keyboard(remoteRobot).enter();
        waitFor(() -> {
            ActionButtonFixture scanSelection = findScanSelection();
            return scanSelection.isEnabled() && scanSelection.hasText("Scan: none");
        });
        waitFor(() -> {
            findScanSelection().click();
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                   && findAll(JListFixture.class,
                              "//div[@class='MyList']").get(0).findAllText().size() > 0;
        });
        new Keyboard(remoteRobot).enterText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).enter();
        waitFor(() -> findAll(TREE).size() == 1 && checkTreeState(findAll(TREE).get(0)));
    }

    @NotNull
    private ActionButtonFixture findProjectSelection() {
        return findSelection("Project");
    }

    @NotNull
    private ActionButtonFixture findScanSelection() {
        return findSelection("Scan");
    }

    @NotNull
    private ActionButtonFixture findSelection(String s) {
        return find(ActionButtonFixture.class,
                    String.format("//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]", s));
    }

    private void applySettings() {
        openCxToolWindow();
        openSettings();
        setFields();
        find(JCheckboxFixture.class,
             String.format(FIELD_NAME, Constants.FIELD_NAME_USE_AUTH_URL),
             waitDuration).setValue(false);
        // click the validation button
        find(JButtonFixture.class, VALIDATE_BUTTON).click();
        // wait for the validation success label
        // the test fails if not found
        find(ComponentFixture.class, "//div[@accessiblename.key='VALIDATE_SUCCESS']", waitDuration);
        find("//div[@text.key='button.ok']").click();
    }

    private void openSettings() {
        openCxToolWindow();
        waitFor(() -> {
            if (hasAnyComponent(SETTINGS_ACTION)) {
                find(SETTINGS_ACTION).click();
            } else if (hasAnyComponent(SETTINGS_BUTTON)) {
                find(SETTINGS_BUTTON).click();
            }
            return hasAnyComponent(String.format(FIELD_NAME, Constants.FIELD_NAME_SERVER_URL));
        });
    }

    private void getResults() {
        JTextFieldFixture scanField = find(JTextFieldFixture.class, SCAN_FIELD);
        setInvalidScanId();
        scanField.setText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
        waitFor(() -> hasAnyComponent(String.format("//div[@class='Tree' and @visible_text='Scan %s']",
                                                    Environment.SCAN_ID)));
    }

    private void checkResultsPanel() {
        // check project selection for the project name
        waitFor(() -> hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and @visible_text='Project: %s']",
                Environment.PROJECT_NAME)));
        // check scan selection for the scan id
        waitFor(() -> hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']",
                Environment.SCAN_ID,
                Environment.SCAN_ID)));
        // navigate the tree for a result
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        waitFor(() -> {
            find(EXPAND_ACTION).click();
            return tree.findAllText().size() > 1;
        });
        waitFor(() -> {
            find(COLLAPSE_ACTION).click();
            return tree.findAllText().size() == 1;
        });
        navigate(tree, "Scan", 2);
        navigate(tree, "sast", 4);
        navigate(tree, "HIGH", 6);
        List<String> selectedPaths = tree.collectSelectedPaths().get(0);
        // 3 is scan -> sast -> high
        Assertions.assertEquals(3, selectedPaths.size());
        String selected = selectedPaths.get(2);
        Assertions.assertTrue(selected.startsWith("HIGH"));
        // open first result for sast -> high
        int row = -1;
        for (int i = 0; i < tree.collectRows().size(); i++) {
            if (selected.equals(tree.getValueAtRow(i))) {
                row = i;
                tree.clickRow(i + 1);
                break;
            }
        }
        // open first node of the opened result
        final int resultRow = row;
        Assertions.assertTrue(resultRow > 2);
        waitFor(() -> {
            tree.clickRow(resultRow);
            return findAll(LINK_LABEL).size() > 0;
        });
        waitFor(() -> {
            findAll(LINK_LABEL).get(0).click();
            return hasAnyComponent(EDITOR);
        });
        EditorFixture editor = find(EditorFixture.class, EDITOR, waitDuration);
        // check we opened the correct line:column
        // token is the string in a link label, enclosed in parens, e.g. (token)
        String token = findAll(LINK_LABEL).get(0).getData().getAll().get(2).getText().trim();
        // cleanToken removes the parens
        String cleanToken = token.substring(1, token.length() - 2);
        String editorAtCaret = editor.getText().substring(editor.getCaretOffset());
        Assertions.assertTrue(editorAtCaret.startsWith(cleanToken),
                              String.format("editor: %s | token: %s",
                                            editorAtCaret.substring(0, token.length()),
                                            token));
    }

    private void setInvalidScanId() {
        waitFor(() -> {
            find(JTextFieldFixture.class, SCAN_FIELD).click();
            return find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus();
        });
        find(JTextFieldFixture.class, SCAN_FIELD).setText("inva-lid");
        waitFor(() -> {
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

    private void navigate(JTreeFixture tree, String prefix, int minExpectedSize) {
        waitFor(() -> {
            tree.doubleClickRowWithText(prefix, false);
            return tree.findAllText().size() >= minExpectedSize;
        });
    }

    private void setFields() {
        setField(Constants.FIELD_NAME_SERVER_URL, Environment.BASE_URL);
        setField(Constants.FIELD_NAME_TENANT, Environment.TENANT);
        setField(Constants.FIELD_NAME_API_KEY, Environment.API_KEY);
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");
    }

    private boolean checkTreeState(ComponentFixture tree) {
        return tree.getData().getAll().size() > 1 || (tree.getData().getAll().size() == 1
                                                      && tree.getData()
                                                             .getAll()
                                                             .get(0)
                                                             .getText()
                                                             .contains(Bundle.message(
                                                                     Resource.GETTING_RESULTS)));
    }
}
