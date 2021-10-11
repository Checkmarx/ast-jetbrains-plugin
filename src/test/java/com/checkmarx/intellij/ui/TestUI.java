package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;

public class TestUI extends BaseUITest {

    /**
     * From the welcome screen, fill and validate AST configurations.
     */
    @Test
    public void testEndToEnd() {
        applySettings();
        getResults();
        checkResultsPanel();
    }

    private void applySettings() {
        step("Apply settings", () -> {
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
        });
    }

    private void openSettings() {
        if (hasAnyComponent(SETTINGS_ACTION)) {
            find(SETTINGS_ACTION).click();
        } else if (hasAnyComponent(SETTINGS_BUTTON)) {
            find(SETTINGS_BUTTON).click();
        }
    }

    private void getResults() {
        step("Get results", () -> {
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                find(JTextFieldFixture.class, SCAN_FIELD).click();
                return find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus();
            });
            find(JTextFieldFixture.class, SCAN_FIELD).setText(Environment.SCAN_ID);
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> {
                                      new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
                                      ComponentFixture tree = find(TREE);
                                      return checkTreeState(tree);
                                  });
            find(COLLAPSE_ACTION).click();
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> hasAnyComponent(String.format(
                                          "//div[@class='Tree' and @visible_text='Scan %s']", Environment.SCAN_ID)));
        });
    }

    private void checkResultsPanel() {
        step("Test tree and code link", () -> {
            // navigate the tree for a result
            JTreeFixture tree = find(JTreeFixture.class, TREE);
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                find(EXPAND_ACTION).click();
                return tree.findAllText().size() > 1;
            });
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                find(COLLAPSE_ACTION).click();
                return tree.findAllText().size() == 1;
            });
            navigate(tree, "Scan", 1);
            navigate(tree, "sast", 3);
            navigate(tree, "HIGH", 5);
            // open first result for sast -> high
            String selected = tree.collectSelectedPaths().get(0).get(tree.collectSelectedPaths().get(0).size() - 1);
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
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                tree.clickRow(resultRow);
                return findAll(LINK_LABEL).size() > 0;
            });
            RepeatUtilsKt.waitFor(waitDuration, () -> {
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
        });
    }

    @Test
    public void testInvalidAuth() {
        step("Test invalid settings", () -> {
            openCxToolWindow();
            openSettings();
            // set the fields
            setFields();
            find(JCheckboxFixture.class,
                 String.format(FIELD_NAME, Constants.FIELD_NAME_USE_AUTH_URL),
                 waitDuration).setValue(true);
            setField(Constants.FIELD_NAME_AUTH_URL, "wrongauth");

            // click the validation button
            find(JButtonFixture.class, VALIDATE_BUTTON).click();
            // check for validation fail
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> !hasAnyComponent("//div[@accessiblename.key='VALIDATE_IN_PROGRESS']"));
            Assertions.assertThrows(WaitForConditionTimeoutException.class,
                                    () -> find(ComponentFixture.class,
                                               "//div[@accessiblename.key='VALIDATE_SUCCESS']"));
            find("//div[@text.key='button.cancel']").click();
        });
    }

    @Test
    public void testInvalidScanId() {
        step("test invalid scan id", () -> {
            openCxToolWindow();
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                find(JTextFieldFixture.class, SCAN_FIELD).click();
                return find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus();
            });
            find(JTextFieldFixture.class, SCAN_FIELD).setText("inva-lid");
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> {
                                      new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
                                      ComponentFixture tree = find(TREE);
                                      return tree.getData().getAll().size() == 1
                                             && tree.getData()
                                                    .getAll()
                                                    .get(0)
                                                    .getText()
                                                    .contains(Bundle.message(
                                                            Resource.INVALID_SCAN_ID));
                                  });

        });
    }

    private void navigate(JTreeFixture tree, String text, int expectedSize) {
        RepeatUtilsKt.waitFor(waitDuration, () -> {
            tree.doubleClickRowWithText(text, false);
            return tree.findAllText().size() > expectedSize;
        });
    }

    private void setFields() {
        setField(Constants.FIELD_NAME_SERVER_URL, Environment.BASE_URL);
        setField(Constants.FIELD_NAME_TENANT, Environment.TENANT);
        setField(Constants.FIELD_NAME_API_KEY, Environment.API_KEY);
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
