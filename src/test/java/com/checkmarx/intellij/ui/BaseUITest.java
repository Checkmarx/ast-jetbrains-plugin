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

    protected static final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8580");
    protected static final Duration waitDuration = Duration.ofSeconds(180);
    private static boolean initialized = false;

    @BeforeAll
    public static void init() {
        if (!initialized) {
            StepWorker.registerProcessor(new StepLogger());
            if (hasAnyComponent("//div[@class='FlatWelcomeFrame']")) {
                find(
                        "//div[@defaulticon='fromVCSTab.svg']").click();
                find(JTextFieldFixture.class, "//div[@class='BorderlessTextField']", Duration.ofSeconds(10))
                        .setText(Environment.REPO);
                RepeatUtilsKt.waitFor(waitDuration,
                                      () -> find(JButtonFixture.class,
                                                 "//div[@text.key='clone.dialog.clone.button']").isEnabled());
                find("//div[@text.key='clone.dialog.clone.button']").click();
                waitAndClick("//div[@text.key='untrusted.project.dialog.trust.button']");
                waitAndClick("//div[contains(@text.key, 'button.close')]");
                RepeatUtilsKt.waitFor(waitDuration,
                                      () -> hasAnyComponent("//div[@class='ContentTabLabel']"));
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
                              () -> hasAnyComponent(xpath));
        find(xpath).click();
    }

    protected void setField(String fieldName, String value) {
        RepeatUtilsKt.waitFor(waitDuration, () -> {
            find(JTextFieldFixture.class, String.format("//div[@name='%s']", fieldName), waitDuration).click();
            return find(JTextFieldFixture.class, String.format("//div[@name='%s']", fieldName), waitDuration).getHasFocus();
        });
        find(JTextFieldFixture.class, String.format("//div[@name='%s']", fieldName), waitDuration).setText(
                value);
    }

    protected static boolean hasAnyComponent(@Language("XPath") String xpath) {
        return UtilsKt.hasAnyComponent(remoteRobot, Locators.byXpath(xpath));
    }

    protected static void openCxToolWindow() {
        if (!hasAnyComponent("//div[@myaction.key='SETTINGS_ACTION']")) {
            find("//div[@text='Checkmarx']").click();
        }
    }
}
