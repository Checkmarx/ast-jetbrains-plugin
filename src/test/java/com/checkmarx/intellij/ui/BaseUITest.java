package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.tool.window.ResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.search.locators.Locators;
import com.intellij.remoterobot.stepsProcessing.StepLogger;
import com.intellij.remoterobot.stepsProcessing.StepWorker;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.UtilsKt;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public abstract class BaseUITest {

    @Language("XPath")
    protected static final String SETTINGS_ACTION = "//div[@myaction.key='SETTINGS_ACTION']";
    @Language("XPath")
    protected static final String SETTINGS_BUTTON = "//div[@text.key='OPEN_SETTINGS_BUTTON']";
    @Language("XPath")
    protected static final String EXPAND_ACTION = "//div[@myaction.key='EXPAND_ALL_ACTION']";
    @Language("XPath")
    protected static final String COLLAPSE_ACTION = "//div[@myaction.key='COLLAPSE_ALL_ACTION']";
    @Language("XPath")
    protected static final String FILTER_BY_ACTION = "//div[@myicon='filter.svg']";
    @Language("XPath")
    protected static final String GROUP_BY_ACTION = "//div[@myicon='groupBy.svg']";
    @Language("XPath")
    protected static final String CLONE_BUTTON = "//div[@text.key='clone.dialog.clone.button']";
    @Language("XPath")
    protected static final String FIELD_NAME = "//div[@name='%s']";
    @Language("XPath")
    protected static final String CHANGES_COMMENT = "//div[@accessiblename='%s' and @class='JLabel' and @text='<html>%s</html>']";
    @Language("XPath")
    protected static final String VALIDATE_BUTTON = "//div[@class='JButton' and @text.key='VALIDATE_BUTTON']";
    @Language("XPath")
    protected static final String STATE_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='TO_VERIFY']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='CONFIRMED']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='URGENT']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    protected static final String SEVERITY_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='MEDIUM']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='HIGH']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='LOW']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    protected static final String SCAN_FIELD = "//div[@class='TextFieldWithProcessing']";
    @Language("XPath")
    protected static final String TREE = "//div[@class='Tree']";
    @Language("XPath")
    protected static final String LINK_LABEL = "//div[@class='CxLinkLabel']";
    @Language("XPath")
    protected static final String BOLD_LABEL = "//div[@class='BoldLabel']";
    @Language("XPath")
    protected static final String EDITOR = "//div[@class='EditorComponentImpl']";
    @Language("XPath")
    protected static final String JLIST = "//div[@class='JList']";

    protected static final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8580");

    protected static final Duration waitDuration = Duration.ofSeconds(Integer.getInteger("uiWaitDuration"));
    private static boolean initialized = false;

    @BeforeAll
    public static void init() {
        if (!initialized) {
            log("Initializing the tests");
            log("Wait duration set for " + waitDuration.getSeconds());
            StepWorker.registerProcessor(new StepLogger());
            if (hasAnyComponent("//div[@class='FlatWelcomeFrame']")) {
                find("//div[@defaulticon='fromVCSTab.svg']").click();
                find(JTextFieldFixture.class, "//div[@class='BorderlessTextField']", Duration.ofSeconds(10))
                        .setText(Environment.REPO);
                waitFor(() -> hasAnyComponent(CLONE_BUTTON) && find(JButtonFixture.class, CLONE_BUTTON).isEnabled());
                find(CLONE_BUTTON).click();
                waitAndClick("//div[@text.key='untrusted.project.dialog.trust.button']");
                waitAndClick("//div[contains(@text.key, 'button.close')]");
                waitFor(() -> hasAnyComponent("//div[@class='ContentTabLabel']"));
            }
            initialized = true;
            log("Initialization finished");
        } else {
            log("Tests already initialized, skipping");
        }
    }

    protected static void resizeToolBar() {
        Keyboard keyboard = new Keyboard(remoteRobot);
        if (remoteRobot.isMac()) {
            keyboard.hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_META, KeyEvent.VK_UP);
        } else {
            keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_UP);
        }
    }

    protected static void enter(String value) {
        Keyboard keyboard = new Keyboard(remoteRobot);
        waitFor(() -> {
            for (int i = 0; i < value.length(); i++) {
                keyboard.backspace();
            }
            keyboard.enterText(value);
            return hasAnyComponent("//div[@visible_text='" + value + "']");
        });
        keyboard.enter();
    }

    protected static void click(@Language("XPath") String xpath) {
        find(xpath).click();
    }

    protected static ComponentFixture find(@Language("XPath") String xpath) {
        return find(ComponentFixture.class, xpath);
    }

    protected static <T extends ComponentFixture> T find(Class<T> cls,
                                                         @Language("XPath") String xpath) {
        return find(cls, xpath, Duration.ofSeconds(60));
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
        waitFor(() -> hasAnyComponent(xpath) && find(xpath).isShowing());
        find(xpath).click();
    }

    protected void setField(String fieldName, String value) {
        log("Setting field " + fieldName);
        @Language("XPath") String fieldXpath = String.format(FIELD_NAME, fieldName);
        waitFor(() -> hasAnyComponent(fieldXpath) && find(fieldXpath).isShowing());
        find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).setText(value);
    }

    protected static boolean hasAnyComponent(@Language("XPath") String xpath) {
        return UtilsKt.hasAnyComponent(remoteRobot, Locators.byXpath(xpath));
    }

    protected static void waitFor(Supplier<Boolean> condition) {
        RepeatUtilsKt.waitFor(waitDuration, condition::get);
    }

    protected static void openCxToolWindow() {
        log("Opening Cx Tool Window");
        @Language("XPath") String xpath = "//div[@text='Checkmarx' and @class='StripeButton']";
        waitFor(() -> hasAnyComponent(xpath));
        if (!(hasAnyComponent(SETTINGS_ACTION) || hasAnyComponent(SETTINGS_BUTTON))) {
            find(xpath).click();
        }
    }

    @Language("XPath")
    protected static String filterXPath(Severity filter) {
        return String.format("//div[@tooltiptext='%s']", filter.tooltipSupplier().get());
    }

    protected static void log(String msg) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.printf("%s | %s: %s%n", Instant.now().toString(), st[2], msg);
    }
}
