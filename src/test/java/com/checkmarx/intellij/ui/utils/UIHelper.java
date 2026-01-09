package com.checkmarx.intellij.ui.utils;

import com.intellij.remoterobot.fixtures.ComponentFixture;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.checkmarx.intellij.ui.BaseUITest.*;
import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;
import static com.checkmarx.intellij.ui.PageMethods.CxOneAssistPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class UIHelper {

    private static final Duration waitDuration = Duration.ofSeconds(Integer.getInteger("uiWaitDuration"));
    private static boolean initialized = false;
    private static int retries = 0;
    private static final int DEFAULT_SLEEP_MS = 1500;
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

    public static <T> T waitAndGet(Supplier<T> supplier) {
        AtomicReference<T> ref = new AtomicReference<>();

        waitFor(() -> {
            T value = supplier.get();
            if (value == null) {
                return false;
            }
            ref.set(value);
            return true;
        });

        return ref.get();
    }


    public static void log(String msg) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.printf("%s | %s: %s%n", Instant.now().toString(), st[2], msg);
    }

    public static void locateAndClickOnButton(String xpath) {
        //Close welcome window
        waitFor(() -> hasAnyComponent(xpath));
        find(xpath).click();
    }

    public static void setField(String fieldName, String value) {
        log("Setting field " + fieldName);
        @Language("XPath") String fieldXpath = String.format(FIELD_NAME, fieldName);
        waitFor(() -> hasAnyComponent(fieldXpath) && find(fieldXpath).isShowing());
        find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).setText(value);
    }

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

    public static void editFile() {
        //Implementation to edit the currently opened file by adding and removing a space character
        Keyboard keyboard = new Keyboard(remoteRobot);
        keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_END);
        keyboard.key(KeyEvent.VK_SPACE);   // add single space
        keyboard.key(KeyEvent.VK_BACK_SPACE); // remove space
    }

    public static void enableRealTimeScanIfDisabled(String realTimeScanCheckboxXpath) {
        //Implementation to enable Real-Time Scan if it is disabled
        log("Ensuring Real-Time Scan is enabled");
        //open settings page
        openSettings();
        logoutIfUserIsAlreadyLoggedIn();
        performLoginUsingApiKey(true);
        validateWelcomePageLoadedSuccessfully(true);
        locateAndClickOnButton(WELCOME_CLOSE_BUTTON);
        //Navigate to OSS Settings tab
        navigateToCxOneAssistPage();
        //Ensure OSS Real-Time Scan is enabled
        if (!isCheckboxSelected(realTimeScanCheckboxXpath))
            clickSafe(realTimeScanCheckboxXpath);
        //Close settings page
        clickSafe(OK_BTN);
    }

    public static void selectRadioButton(String radioText) {
        log("Selecting radio button " + radioText);
        waitFor(() -> hasAnyComponent(radioText));
        find(radioText).click();
    }

    public static boolean isCheckboxSelected(String checkboxText) {
        waitFor(() -> hasAnyComponent(checkboxText));
        Boolean result = (Boolean) find(checkboxText).callJs(
                "component.isSelected ? component.isSelected() : component.getModel().isSelected()"
        );
        if (result != null && result) {
            return true;
        } else {
            return false;
        }
    }

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
}
