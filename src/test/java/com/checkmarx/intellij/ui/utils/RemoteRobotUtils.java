package com.checkmarx.intellij.ui.utils;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.search.locators.Locators;
import com.intellij.remoterobot.utils.UtilsKt;
import org.intellij.lang.annotations.Language;

import java.time.Duration;
import java.util.List;

public class RemoteRobotUtils {

    public static final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8580");

    public static boolean hasAnyComponent(@Language("XPath") String xpath) {
        System.out.println("Checking hasAnyComponent: " + xpath);
        return UtilsKt.hasAnyComponent(remoteRobot, Locators.byXpath(xpath));
    }

    public static void click(@Language("XPath") String xpath) {
        find(xpath).click();
    }

    public static ComponentFixture find(@Language("XPath") String xpath) {
        return find(ComponentFixture.class, xpath);
    }

    public static <T extends ComponentFixture> T find(Class<T> cls, @Language("XPath") String xpath) {
        return find(cls, xpath, Duration.ofSeconds(90));
    }

    public static <T extends ComponentFixture> T find(Class<T> cls, @Language("XPath") String xpath, Duration duration) {
        return remoteRobot.find(cls, Locators.byXpath(xpath), duration);
    }

    public static List<ComponentFixture> findAll(@Language("XPath") String xpath) {
        return findAll(ComponentFixture.class, xpath);
    }

    public static <T extends ComponentFixture> List<T> findAll(Class<T> cls, @Language("XPath") String xpath) {
        return remoteRobot.findAll(cls, Locators.byXpath(xpath));
    }

    public static List<ComponentFixture> getInspectedProblems() {
        // Navigate to the "Problems" tool window
        remoteRobot.find(ComponentFixture.class, Locators.byXpath("//div[@class='StripeButton' and @text='Problems']")).click();

        // Retrieve the list of problems
        return remoteRobot.findAll(ComponentFixture.class, Locators.byXpath("//div[@class='EditorNotificationPanel']"));
    }
}
