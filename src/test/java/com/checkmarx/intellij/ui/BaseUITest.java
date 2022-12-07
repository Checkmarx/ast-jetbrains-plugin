package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.search.locators.Locators;
import com.intellij.remoterobot.stepsProcessing.StepLogger;
import com.intellij.remoterobot.stepsProcessing.StepWorker;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.UtilsKt;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class BaseUITest {
    @Language("XPath")
    protected static final String SETTINGS_ACTION = "//div[@myicon='settings.svg']";
    @Language("XPath")
    protected static final String SETTINGS_BUTTON = "//div[@text='Open Settings']";
    @Language("XPath")
    protected static final String EXPAND_ACTION = "//div[@tooltiptext='Expand all']";
    @Language("XPath")
    protected static final String COLLAPSE_ACTION = "//div[@tooltiptext='Collapse all']";
    @Language("XPath")
    protected static final String FILTER_BY_ACTION = "//div[@myicon='filter.svg']";
    @Language("XPath")
    protected static final String GROUP_BY_ACTION = "//div[@myicon='groupBy.svg']";
    @Language("XPath")
    protected static final String CLONE_BUTTON = "//div[@text='Clone']";
    @Language("XPath")
    protected static final String FIELD_NAME = "//div[@name='%s']";
    @Language("XPath")
    protected static final String CHANGES_COMMENT = "//div[@accessiblename='%s' and @class='JLabel' and @text='<html>%s</html>']";
    @Language("XPath")
    protected static final String VALIDATE_BUTTON = "//div[@class='JButton' and @text='Validate connection']";
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
    protected static final String EDITOR = "//div[@class='EditorComponentImpl']";
    @Language("XPath")
    protected static final String JLIST = "//div[@class='JList']";
    @Language("XPath")
    protected static final String START_SCAN_BTN = "//div[contains(@myaction.key, 'START_SCAN_ACTION')]";
    @Language("XPath")
    protected static final String CANCEL_SCAN_BTN = "//div[@myaction.key='CANCEL_SCAN_ACTION']";
    protected static final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8580");
    protected static final Duration waitDuration = Duration.ofSeconds(Integer.getInteger("uiWaitDuration"));
    private static boolean initialized = false;
    private static int retries = 0;

    protected static ComponentFixture baseLabel;
    protected static JTextFieldFixture scanIdTextBox;
    protected static ActionButtonFixture projectCombobox;
    protected static ActionButtonFixture branchCombobox;
    protected static ActionButtonFixture scanCombobox;

    @BeforeAll
    public static void init() {
        if (!initialized) {
            log("Initializing the tests");
            log("Wait duration set for " + waitDuration.getSeconds());
            StepWorker.registerProcessor(new StepLogger());
            if (hasAnyComponent("//div[@class='FlatWelcomeFrame']")) {
                find("//div[@defaulticon='fromVCSTab.svg']").click();
                find(JTextFieldFixture.class, "//div[@class='BorderlessTextField']", Duration.ofSeconds(10)).setText(Environment.REPO);
                waitFor(() -> hasAnyComponent(CLONE_BUTTON) && find(JButtonFixture.class, CLONE_BUTTON).isEnabled());
                find(CLONE_BUTTON).click();
                trustClonedProject();
            }

            // Open Checkmarx One plugin
            openCxToolWindow();

            // Resize Checkmarx One plugin so that all toolbar icons are visible
            resizeToolBar();

            // Connect to AST
            testASTConnection(true);

            initializeElements();

            initialized = true;
            log("Initialization finished");
        } else {
            log("Tests already initialized, skipping");
        }
    }

    private static void resizeToolBar() {
        baseLabel = find("//div[@class='BaseLabel']");
        Keyboard keyboard = new Keyboard(remoteRobot);
        for (int i = 0; i < 3; i++) {
            baseLabel.click();
            if (remoteRobot.isMac()) {
                keyboard.hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_META, KeyEvent.VK_UP);
            } else {
                keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT, KeyEvent.VK_UP);
            }
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
        return find(cls, xpath, Duration.ofSeconds(90));
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

    private static void trustClonedProject() {
        try {
            @Language("XPath") String xpath = "//div[@text='Trust Project']";
            waitFor(() -> hasAnyComponent(xpath) && find(xpath).isShowing());
            find(xpath).click();
        } catch(WaitForConditionTimeoutException ignored) {}
    }

    private static void setField(String fieldName, String value) {
        log("Setting field " + fieldName);
        @Language("XPath") String fieldXpath = String.format(FIELD_NAME, fieldName);
        waitFor(() -> hasAnyComponent(fieldXpath) && find(fieldXpath).isShowing());
        find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).setText(value);
    }

    protected static boolean hasAnyComponent(@Language("XPath") String xpath) {
        return UtilsKt.hasAnyComponent(remoteRobot, Locators.byXpath(xpath));
    }

    protected static void waitFor(Supplier<Boolean> condition) {
        try {
            RepeatUtilsKt.waitFor(waitDuration, condition::get);
        }catch(WaitForConditionTimeoutException e) {
            retries++;
            if(retries < 3){
                if(baseLabel != null && baseLabel.isShowing()) {
                    baseLabel.click();
                }
            } else{
                retries = 0;
                throw e;
            }
        }
    }

    private static void openCxToolWindow() {
        log("Opening Cx Tool Window");
        @Language("XPath") String xpath = "//div[@text='Checkmarx' and @class='StripeButton']";
        waitFor(() -> hasAnyComponent(xpath));
        if (!(hasAnyComponent(SETTINGS_ACTION) || hasAnyComponent(SETTINGS_BUTTON))) {
            find(xpath).click();
        }
    }

    private static void initializeElements() {
        baseLabel.click();
        scanIdTextBox = find(JTextFieldFixture.class, SCAN_FIELD);
        projectCombobox = findSelection("Project");
        branchCombobox = findSelection("Branch");
        scanCombobox = findSelection("Scan");
    }

    protected static void log(String msg) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.printf("%s | %s: %s%n", Instant.now().toString(), st[2], msg);
    }

    private static void openSettings() {
        waitFor(() -> {
            if (hasAnyComponent(SETTINGS_ACTION)) {
                click(SETTINGS_ACTION);
            } else if (hasAnyComponent(SETTINGS_BUTTON)) {
                click(SETTINGS_BUTTON);
            }
            return hasAnyComponent(String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY));
        });
    }

    protected static void testASTConnection(boolean validCredentials) {
        openSettings();

        setField(Constants.FIELD_NAME_API_KEY, validCredentials ? Environment.API_KEY : "invalidAPIKey");
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");

        click(VALIDATE_BUTTON);

        waitFor(() -> !hasAnyComponent("//div[@accessiblename='Validating...']"));

        if (validCredentials) {
            Assertions.assertTrue(hasAnyComponent("//div[@accessiblename='Successfully authenticated to AST server']"));
            click("//div[@text='OK']");
            baseLabel.click();
            // Ensure that start scan button and cancel scan button are hidden with invalid credentials
            waitFor(() -> hasAnyComponent("//div[contains(@myaction.key, 'START_SCAN_ACTION')]"));
            waitFor(() -> hasAnyComponent("//div[contains(@myaction.key, 'CANCEL_SCAN_ACTION')]"));
        } else {
            Assertions.assertFalse(hasAnyComponent("//div[@accessiblename='Successfully authenticated to AST server']"));
            click("//div[@text='OK']");
            baseLabel.click();
            // Ensure that start scan button and cancel scan button are hidden with invalid credentials
            waitFor(() -> !hasAnyComponent("//div[contains(@myaction.key, 'START_SCAN_ACTION')]"));
            waitFor(() -> !hasAnyComponent("//div[contains(@myaction.key, 'CANCEL_SCAN_ACTION')]"));
        }
    }

    protected static void testFileNavigation() {
        waitFor(() -> {
            baseLabel.click();
            findAll(LINK_LABEL).get(0).doubleClick();
            return hasAnyComponent(EDITOR);
        });
        Assertions.assertDoesNotThrow(() -> find(EditorFixture.class, EDITOR, waitDuration));
        //Confirming if editor is opened
        find(EditorFixture.class, EDITOR, waitDuration);
    }

    protected void getResults() {
        log(" =====> Get Results...");
        baseLabel.click();
        waitFor(() -> hasAnyComponent(SCAN_FIELD) && hasSelection("Project") && hasSelection("Branch") && hasSelection("Scan"));
        baseLabel.click();
        scanIdTextBox.setText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
        waitFor(() -> hasAnyComponent(String.format("//div[@class='Tree' and contains(@visible_text,'Scan %s')]", Environment.SCAN_ID)));
    }

    private static boolean hasSelection(String s) {
        return hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]",
                s));
    }

    protected void waitForScanIdSelection() {
        baseLabel.click();
        // check scan selection for the scan id
        waitFor(() -> hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']",
                Environment.SCAN_ID,
                Environment.SCAN_ID)));
    }

    protected void navigate(String prefix, int minExpectedSize) {
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

    @NotNull
    private static ActionButtonFixture findSelection(String s) {
        @Language("XPath") String xpath = String.format(
                "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]",
                s);
        waitFor(() -> hasAnyComponent(xpath));
        return find(ActionButtonFixture.class, xpath);
    }

    protected void testSelectionAction(ActionButtonFixture selection, String prefix, String value) {
        baseLabel.click();
        waitFor(() -> {
            System.out.println(selection.getTemplatePresentationText());
            return selection.isEnabled() && selection.getTemplatePresentationText().contains(prefix);
        });
        baseLabel.click();
        waitFor(() -> {
            selection.click();
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                    && findAll(JListFixture.class, "//div[@class='MyList']").get(0).findAllText().size() > 0;
        });
        enter(value);
    }

    protected void clearSelection() {
        waitFor(() -> {
            if (hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Project: none']")
                    && projectCombobox.isEnabled()
                    && hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Branch: none']")
                    && hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Scan: none']")
                    && !hasAnyComponent(TREE)
                    && StringUtils.isBlank(scanIdTextBox.getText())) {
                log("clear selection done");
                return true;
            }
            if (!scanCombobox.isShowing() || (scanCombobox.hasText("Scan: ..."))
                    || (!branchCombobox.isShowing() || branchCombobox.hasText("Branch: ..."))
                    || (!projectCombobox.isShowing() || projectCombobox.hasText("Project: ..."))) {
                log("clear selection still in progress");
                return false;
            }
            log("clicking refresh action button");
            click("//div[@myicon='refresh.svg']");
            return false;
        });
    }
}
