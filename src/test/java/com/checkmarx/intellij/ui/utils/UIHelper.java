package com.checkmarx.intellij.ui.utils;

import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.intellij.lang.annotations.Language;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static com.checkmarx.intellij.ui.BaseUITest.focusCxWindow;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.FIELD_NAME;
import static com.checkmarx.intellij.ui.utils.Xpath.VISIBLE_TEXT;

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
}
