package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Environment;
import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.search.locators.Locators;
import com.intellij.remoterobot.stepsProcessing.StepLogger;
import com.intellij.remoterobot.stepsProcessing.StepWorker;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.UtilsKt;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;

import java.time.Duration;
import java.util.List;

public abstract class BaseUITest {


    @Language("XPath")
    protected static final String SETTINGS_ACTION = "//div[@myaction.key='SETTINGS_ACTION']";
    @Language("XPath")
    protected static final String EXPAND_ACTION = "//div[@myaction.key='EXPAND_ALL_ACTION']";
    @Language("XPath")
    protected static final String COLLAPSE_ACTION = "//div[@myaction.key='COLLAPSE_ALL_ACTION']";
    @Language("XPath")
    protected static final String CLONE_BUTTON = "//div[@text.key='clone.dialog.clone.button']";
    @Language("XPath")
    protected static final String FIELD_NAME = "//div[@name='%s']";
    @Language("XPath")
    protected static final String VALIDATE_BUTTON = "//div[@class='JButton' and @text.key='VALIDATE_BUTTON']";
    @Language("XPath")
    protected static final String SCAN_FIELD = "//div[@class='TextFieldWithProcessing']";
    @Language("XPath")
    protected static final String TREE = "//div[@class='Tree']";
    @Language("XPath")
    protected static final String LINK_LABEL = "//div[@class='LinkLabel']";
    @Language("XPath")
    protected static final String EDITOR = "//div[@class='EditorComponentImpl']";

    protected static final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8580");
    protected static final Duration waitDuration = Duration.ofSeconds(900);
    private static boolean initialized = false;

    @BeforeAll
    public static void init() {
        if (!initialized) {
            StepWorker.registerProcessor(new StepLogger());
            if (hasAnyComponent("//div[@class='FlatWelcomeFrame']")) {
                find("//div[@defaulticon='fromVCSTab.svg']").click();
                find(JTextFieldFixture.class, "//div[@class='BorderlessTextField']", Duration.ofSeconds(10))
                        .setText(Environment.REPO);
                RepeatUtilsKt.waitFor(waitDuration, () -> find(JButtonFixture.class, CLONE_BUTTON).isEnabled());
                find(CLONE_BUTTON).click();
                waitAndClick("//div[@text.key='untrusted.project.dialog.trust.button']");
                waitAndClick("//div[contains(@text.key, 'button.close')]");
                RepeatUtilsKt.waitFor(waitDuration, () -> hasAnyComponent("//div[@class='ContentTabLabel']"));
            }
            initialized = true;
        }
    }

    @BeforeAll
    public static void checkoutProject() {
    }

    protected static ComponentFixture find(@Language("XPath") String xpath) {
        return find(ComponentFixture.class, xpath);
    }

    protected static <T extends ComponentFixture> T find(Class<T> cls,
                                                         @Language("XPath") String xpath) {
        return find(cls, xpath, Duration.ofSeconds(2));
    }

    protected static <T extends ComponentFixture> T find(Class<T> cls,
                                                         @Language("XPath") String xpath,
                                                         Duration duration) {
        return remoteRobot.find(cls, Locators.byXpath(xpath), duration);
    }

    protected static List<ComponentFixture> findAll(@Language("XPath") String xpath) {
        return findAll(ComponentFixture.class, xpath);
    }

    protected static <T extends ComponentFixture> List<T> findAll(Class<T> cls,
                                                                  @Language("XPath") String xpath) {
        return remoteRobot.findAll(cls, Locators.byXpath(xpath));
    }

    protected static void waitAndClick(@Language("XPath") String xpath) {
        RepeatUtilsKt.waitFor(waitDuration,
                              () -> hasAnyComponent(xpath) && find(xpath).isShowing());
        find(xpath).click();
    }

    protected void setField(String fieldName, String value) {
        RepeatUtilsKt.waitFor(waitDuration, () -> {
            find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).click();
            return find(JTextFieldFixture.class,
                        String.format(FIELD_NAME, fieldName),
                        waitDuration).getHasFocus();
        });
        find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).setText(
                value);
    }

    protected static boolean hasAnyComponent(@Language("XPath") String xpath) {
        return UtilsKt.hasAnyComponent(remoteRobot, Locators.byXpath(xpath));
    }

    protected static void openCxToolWindow() {
        if (!hasAnyComponent(SETTINGS_ACTION)) {
            find("//div[@text='Checkmarx']").click();
            RepeatUtilsKt.waitFor(waitDuration, () -> hasAnyComponent(SETTINGS_ACTION));
        }
    }
}
