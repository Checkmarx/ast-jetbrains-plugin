package com.checkmarx.intellij.ui.utils;


import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JListFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.intellij.lang.annotations.Language;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import java.util.function.Supplier;

import static com.checkmarx.intellij.ui.BaseUITest.*;
import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class UIHelper {

    private static final Duration waitDuration = Duration.ofSeconds(Integer.getInteger("uiWaitDuration"));
    private static final boolean initialized = false;
    private static int retries = 0;
    private static final int DEFAULT_SLEEP_MS = 1500;

    /**
     * Waits for the given condition to become true, using the default wait duration.
     * Retries up to 3 times if the condition times out, refocusing the Cx window if needed.
     *
     * @param condition The condition to wait for (returns true when satisfied)
     */
    public static void waitFor(Supplier<Boolean> condition) {
        try {
            RepeatUtilsKt.waitFor(waitDuration, condition::get);
        } catch (WaitForConditionTimeoutException e) {
            retries++;
            if (retries < 3) {
                focusCxWindow();
            } else {
                retries = 0;
                throw e;
            }
        }
    }

    /**
     * Logs a message with a timestamp and caller information.
     *
     * @param msg The message to log
     */
    public static void log(String msg) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.printf("%s | %s: %s%n", Instant.now().toString(), st[2], msg);
    }

    /**
     * Waits for an element matching the given XPath to appear, then clicks it.
     *
     * @param xpath XPath of the element to click
     */
    public static void locateAndClickOnButton(String xpath) {
        //Close welcome window
        waitFor(() -> hasAnyComponent(xpath));
        find(xpath).click();
    }

    /**
     * Sets the value of a text field identified by its field name.
     *
     * @param fieldName The name of the field
     * @param value     The value to set
     */
    public static void setField(String fieldName, String value) {
        log("Setting field " + fieldName);
        @Language("XPath") String fieldXpath = String.format(FIELD_NAME, fieldName);
        waitFor(() -> hasAnyComponent(fieldXpath) && find(fieldXpath).isShowing());
        find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).setText(value);
    }

    /**
     * Enters the given value into the currently focused field, simulating keyboard input.
     *
     * @param value The value to enter
     */
    public static void enter(String value) {
        Keyboard keyboard = new Keyboard(remoteRobot);
        waitFor(() -> {
            for (int i = 0; i < value.length(); i++) {
                keyboard.backspace();
            }
            keyboard.enterText(value);
            return hasAnyComponent(String.format(VISIBLE_TEXT, value));
        });
        keyboard.enter();
    }

    /**
    * Opens a file in the IDE by its path using keyboard shortcuts.
    * @param filePath The full relative or absolute path of the file to open
    */
    public static void openFileByPath(String filePath) {
        //Implementation to open a file by its path using keyboard shortcuts
        Keyboard keyboard = new Keyboard(remoteRobot);
        // Open "Navigate → File"
        keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);

        // Type full relative or absolute path
        keyboard.enterText(filePath);

        // Press Enter to open
        keyboard.key(KeyEvent.VK_ENTER);
    }

    /**
     * Edits the currently opened file by adding and removing a space character.
     */
    public static void editFile() {
        //Implementation to edit the currently opened file by adding and removing a space character
        Keyboard keyboard = new Keyboard(remoteRobot);
        keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_END);
        keyboard.key(KeyEvent.VK_SPACE);   // add single space
        keyboard.key(KeyEvent.VK_BACK_SPACE); // remove space
    }

    /**
     * Selects a radio button by its visible text.
     *
     * @param radioText The text of the radio button to select
     */
    public static void selectRadioButton(String radioText) {
        log("Selecting radio button " + radioText);
        waitFor(() -> hasAnyComponent(radioText));
        find(radioText).click();
    }

    /**
     * Checks if a checkbox identified by its text is selected.
     *
     * @param checkboxText The text of the checkbox
     * @return true if selected, false otherwise
     */
    public static boolean isCheckboxSelected(String checkboxText) {
        waitFor(() -> hasAnyComponent(checkboxText));
        Boolean result = find(checkboxText).callJs(
                "component.isSelected ? component.isSelected() : component.getModel().isSelected()"
        );
        return result != null && result;
    }

    /**
     * Opens the Cx Tool Window in the UI, clicking the notification group if needed.
     */
    public static void openCxToolWindow() {
        log("Opening Cx Tool Window");
        waitFor(() -> hasAnyComponent("//div[@tooltiptext.key='NOTIFICATION_GROUP_NAME']"));
        if (!(hasAnyComponent(SETTINGS_ACTION) || hasAnyComponent(SETTINGS_BUTTON))) {
            find("//div[@tooltiptext.key='NOTIFICATION_GROUP_NAME']").click();
        }
    }

    /**
     * Generic polling utility. Polls the given condition until it returns true or timeout is reached.
     *
     * @param condition          The condition to check
     * @param timeoutMillis      Max time to wait in milliseconds
     * @param pollIntervalMillis Interval between checks in milliseconds
     * @return true if the condition is met before timeout, false otherwise
     */
    private static boolean pollUntil(Supplier<Boolean> condition, long timeoutMillis, int pollIntervalMillis) {
        long elapsed = 0;
        while (elapsed < timeoutMillis) {
            try {
                if (condition.get()) {
                    return true;
                }
                Thread.sleep(pollIntervalMillis);
                elapsed += pollIntervalMillis;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Ignore and keep polling
            }
        }
        return false;
    }

    /**
     * Polls for the presence or absence of an element (by XPath or text) up to the system default timeout.
     *
     * @param xpathOrText    XPath or text to search for
     * @param waitForVisible true to wait for visibility, false to wait for invisibility
     * @return true if the element is found (or not found, depending on waitForVisible) before timeout, false otherwise
     */
    public static boolean pollingWaitForElement(@Language("XPath") String xpathOrText, boolean waitForVisible) {
        int pollIntervalMillis = 5000; // 5 seconds
        long timeoutMillis = 240_000; // 4 minutes
        boolean value = pollUntil(
                () -> {
                    boolean isVisible = hasAnyComponent(xpathOrText);
                    return (waitForVisible && isVisible) || (!waitForVisible && !isVisible);
                },
                timeoutMillis,
                pollIntervalMillis
        );
        return value;
    }

    /**
     * Waits until the element found by the given XPath becomes enabled, or times out after 4 minutes.
     * Polls every 5 seconds.
     *
     * @param xpath XPath of the element
     * @return true if the element becomes enabled before timeout, false otherwise
     */
    public static boolean waitForElementEnabled(String xpath) {
        int pollIntervalMillis = 5000; // 5 seconds
        long timeoutMillis = 240_000; // 4 minutes
        boolean value = pollUntil(
                () -> {
                    if (hasAnyComponent(xpath)) {
                        var fixture = find(xpath);
                        Object enabledObj = fixture.callJs("component.isEnabled ? component.isEnabled() : (component.getModel ? component.getModel().isEnabled() : false)");
                        boolean enabled = enabledObj instanceof Boolean ? (Boolean) enabledObj : false;
                        return enabled;
                    }
                    return false;
                },
                timeoutMillis,
                pollIntervalMillis
        );
        return value;
    }

    /**
     * Checks if the element found by the given XPath is clickable (enabled and showing).
     *
     * @param xpath XPath of the element
     * @return true if the element is clickable, false otherwise
     */
    public static boolean isElementClickable(String xpath) {
        try {
            if (!hasAnyComponent(xpath)) {
                return false;
            }
            var fixture = find(xpath);
            boolean showing = fixture.isShowing();
            Object enabledObj = fixture.callJs("component.isEnabled ? component.isEnabled() : (component.getModel ? component.getModel().isEnabled() : false)");
            boolean enabled = enabledObj instanceof Boolean ? (Boolean) enabledObj : false;
            return showing && enabled;
        } catch (Exception e) {
            System.err.println("Failed to check clickability for xpath: " + xpath + ", reason: " + e.getMessage());
            return false;
        }
    }

    /**
     * Hides all tool windows using the keyboard shortcut Ctrl+Shift+F12.
     */
    public static void hideToolWindows() {
        Keyboard keyboard = new Keyboard(remoteRobot);
        keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_F12);
    }

    public static void clickSafe(String locator) {
        repeatUntilSuccess(3, () -> {
            waitFor(() -> hasAnyComponent(locator));
            find(locator).click();
        });
    }

    private static void repeatUntilSuccess(int attempts, Runnable action) {
        for (int i = 1; i <= attempts; i++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                if (i == attempts) throw e;
                sleep(DEFAULT_SLEEP_MS);
            }
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interruption flag
        }
    }

    /**
     * Reads the raw popup items from a JListFixture, returning a list of [text, selected] pairs.
     *
     * @param list The JListFixture to read from
     * @return List of [text, selected] pairs - This is the core method
     */
    @SuppressWarnings("unchecked")
    private static List<List<Object>> readPopupItemsRaw() {
        JListFixture list = find(JListFixture.class, MY_LIST);

        return list.callJs(
                "var data = new java.util.ArrayList();"
                        + "var model = component.getModel();"

                        + "for (var i = 0; i < model.getSize(); i++) {"
                        + "  var item = model.getElementAt(i);"
                        + "  var cls = item.getClass();"

                        + "  var textField = cls.getDeclaredField('myText');"
                        + "  textField.setAccessible(true);"
                        + "  var text = textField.get(item);"

                        + "  var selField = cls.getDeclaredField('mySelectedIcon');"
                        + "  selField.setAccessible(true);"
                        + "  var selectedIcon = selField.get(item);"

                        + "  var selected = (selectedIcon != null);"

                        + "  data.add(java.util.Arrays.asList(text, selected));"
                        + "}"

                        + "data;"
        );
    }


    public static Map<String, Boolean> getMenuOptionsWithState() {
        List<List<Object>> raw = readPopupItemsRaw();

        Map<String, Boolean> map = new LinkedHashMap<>();
        for (List<Object> row : raw) {
            map.put((String) row.get(0), (Boolean) row.get(1));
        }
        log("Complete Final Menu Options with State: " + map);
        return map;
    }

    /**
     * Retrieves the list of menu option texts from the popup menu.
     *
     * @return List of menu option texts
     */
    public static List<String> getMenuTextItems() {
        return new ArrayList<>(getMenuOptionsWithState().keySet());
    }

    /**
     * Gets the selected status of a menu option by its text.
     *
     * @param optionText The text of the menu option
     * @return true if selected, false otherwise
     * @throws IllegalArgumentException if the menu option is not found
     */
    public static boolean getMenuSelectedStatus(String optionText) {
        List<List<Object>> raw = readPopupItemsRaw();

        for (List<Object> row : raw) {
            String text = (String) row.get(0);
            Boolean selected = (Boolean) row.get(1);

            if (optionText.equals(text)) {
                return selected;
            }
        }

        throw new IllegalArgumentException("Menu option not found: " + optionText);
    }

    /**
     * Clicks a menu option by its text.
     *
     * @param optionText The text of the menu option to click
     */
    public static void clickMenuOption(String optionText) {
        JListFixture list = find(JListFixture.class, MY_LIST);

        list.callJs(
                "var model = component.getModel();"
                        + "for (var i = 0; i < model.getSize(); i++) {"
                        + "  var item = model.getElementAt(i);"
                        + "  var cls = item.getClass();"
                        + "  var textField = cls.getDeclaredField('myText');"
                        + "  textField.setAccessible(true);"
                        + "  var text = textField.get(item);"

                        + "  if (text != null && text.equals('" + optionText + "')) {"
                        + "    component.setSelectedIndex(i);"
                        + "    component.ensureIndexIsVisible(i);"
                        + "    component.dispatchEvent(new java.awt.event.MouseEvent("
                        + "      component, java.awt.event.MouseEvent.MOUSE_PRESSED,"
                        + "      java.lang.System.currentTimeMillis(), 0, 10, 10, 1, false));"
                        + "    component.dispatchEvent(new java.awt.event.MouseEvent("
                        + "      component, java.awt.event.MouseEvent.MOUSE_RELEASED,"
                        + "      java.lang.System.currentTimeMillis(), 0, 10, 10, 1, false));"
                        + "    break;"
                        + "  }"
                        + "}"
        );
    }
}
