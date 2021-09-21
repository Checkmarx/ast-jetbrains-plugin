package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
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
        step("Apply settings", () -> {
            openCxToolWindow();
            waitAndClick("//div[@myaction.key='SETTINGS_ACTION']");
            setFields();
            find(JCheckboxFixture.class,
                 String.format("//div[@name='%s']", Constants.FIELD_NAME_USE_AUTH_URL),
                 waitDuration).setValue(false);
            // click the validation button
            find(JButtonFixture.class, "//div[@class='JButton' and @text.key='VALIDATE_BUTTON']").click();
            // wait for the validation success label
            // the test fails if not found
            find(ComponentFixture.class,
                 "//div[@accessiblename.key='VALIDATE_SUCCESS']",
                 waitDuration);
            find("//div[@text.key='button.ok']").click();
        });
        step("Get results", () -> {
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                find(JTextFieldFixture.class, "//div[@class='TextFieldWithProcessing']").click();
                return find(JTextFieldFixture.class, "//div[@class='TextFieldWithProcessing']").getHasFocus();
            });
            find(JTextFieldFixture.class, "//div[@class='TextFieldWithProcessing']").setText(
                    Environment.SCAN_ID);
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> {
                                      new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
                                      ComponentFixture tree = find("//div[@class='Tree']");
                                      return checkTreeState(tree);
                                  });
            find("//div[@myaction.key='COLLAPSE_ALL_ACTION']").click();
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> hasAnyComponent(String.format(
                                          "//div[@class='Tree' and @visible_text='Scan %s']", Environment.SCAN_ID)));
        });
        step("Test tree and code link", () -> {
            // navigate the tree for a result
            JTreeFixture tree = find(JTreeFixture.class, "//div[@class='Tree']");
            find("//div[@myaction.key='EXPAND_ALL_ACTION']").click();
            Assertions.assertTrue(tree.findAllText().size() > 1);
            find("//div[@myaction.key='COLLAPSE_ALL_ACTION']").click();
            Assertions.assertEquals(1, tree.findAllText().size());
            tree.doubleClickRowWithText("Scan", false);
            tree.doubleClickRowWithText("sast", false);
            tree.doubleClickRowWithText("HIGH", false);
            // open first result for sast -> high
            String selected = tree.collectSelectedPaths().get(0).get(tree.collectSelectedPaths().get(0).size() - 1);
            for (int i = 0; i < tree.collectRows().size(); i++) {
                if (selected.equals(tree.getValueAtRow(i))) {
                    tree.clickRow(i + 1);
                    break;
                }
            }
            // open first node of the opened result
            RepeatUtilsKt.waitFor(waitDuration, () -> findAll("//div[@class='LinkLabel']").size() > 0);
            ComponentFixture linkLabel = findAll("//div[@class='LinkLabel']").get(0);
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                linkLabel.click();
                return hasAnyComponent("//div[@class='EditorComponentImpl']");
            });
            EditorFixture editor = find(EditorFixture.class,
                                        "//div[@class='EditorComponentImpl']",
                                        waitDuration);
            // check we opened the correct line:column
            String token = linkLabel.getData().getAll().get(2).getText();
            String cleanToken = token.substring(1, token.length() - 2);
            Assertions.assertTrue(editor.getText()
                                        .substring(editor.getCaretOffset())
                                        .startsWith(cleanToken),
                                  String.format("editor: %s | token: %s", editor.getText().substring(
                                          editor.getCaretOffset()).substring(0, cleanToken.length()), cleanToken));
        });
    }

    @Test
    public void testInvalidAuth() {
        step("Test invalid settings", () -> {
            openCxToolWindow();
            waitAndClick("//div[@myaction.key='SETTINGS_ACTION']");
            // set the fields
            setFields();
            find(JCheckboxFixture.class,
                 String.format("//div[@name='%s']", Constants.FIELD_NAME_USE_AUTH_URL),
                 waitDuration).setValue(true);
            setField(Constants.FIELD_NAME_AUTH_URL, "wrongauth");

            // click the validation button
            find(JButtonFixture.class, "//div[@class='JButton' and @text.key='VALIDATE_BUTTON']").click();
            // wait thirty seconds for the validation success label
            // the test fails if not found
            find(ComponentFixture.class, "//div[@accessiblename.key='VALIDATE_FAIL']", waitDuration);
            find("//div[@text.key='button.cancel']").click();
        });
    }

    @Test
    public void testInvalidScanId() {
        step("test invalid scan id", () -> {
            openCxToolWindow();
            RepeatUtilsKt.waitFor(waitDuration, () -> {
                find(JTextFieldFixture.class, "//div[@class='TextFieldWithProcessing']").click();
                return find(JTextFieldFixture.class, "//div[@class='TextFieldWithProcessing']").getHasFocus();
            });
            find(JTextFieldFixture.class, "//div[@class='TextFieldWithProcessing']").setText("inva-lid");
            RepeatUtilsKt.waitFor(waitDuration,
                                  () -> {
                                      new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
                                      ComponentFixture tree = find("//div[@class='Tree']");
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
