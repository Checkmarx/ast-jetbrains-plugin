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
import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.openSettings;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class UIHelper {

    private static final Duration waitDuration = Duration.ofSeconds(Integer.getInteger("uiWaitDuration"));
    private static boolean initialized = false;
    private static int retries = 0;

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
        Keyboard keyboard = new Keyboard(remoteRobot);
        // Open "Navigate → File"
        keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);

        // Type full relative or absolute path
        keyboard.enterText(filePath);

        // Press Enter to open
        keyboard.key(KeyEvent.VK_ENTER);
    }

    public static void editFile() {
        Keyboard keyboard = new Keyboard(remoteRobot);
        keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_END);
        keyboard.key(KeyEvent.VK_SPACE);   // add single space
        keyboard.key(KeyEvent.VK_BACK_SPACE); // remove space
    }

    public static void isVulnerableFilePresentInCxAssistTree(String fileName) {
        waitFor(() -> {
            List<JTreeFixture> trees = findAll(JTreeFixture.class, FINDINGS_TREE_XPATH);

            if (trees.isEmpty()) return false;

            // Check if exact filename exists as a node
            return trees.get(0).findAllText().stream().map(RemoteText::getText).anyMatch(token -> token.equals(fileName));
        });
    }

    public static void enableRealTimeScanIfDisabled(String realTimeScanCheckboxXpath) {
        log("Ensuring Real-Time Scan is enabled");
        //open settings page
        openSettings();
        //Navigate to OSS Settings tab
        clickSafe(GO_TO_CHECKMARXONE_ASSIST);
        //Ensure OSS Real-Time Scan is enabled
        if (!isCheckBoxChecked(realTimeScanCheckboxXpath))
            clickSafe(realTimeScanCheckboxXpath);
        //Close settings page
        clickSafe(OK_BTN);
    }
}
