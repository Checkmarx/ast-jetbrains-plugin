package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.EditorFixture;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.search.locators.Locator;
import com.intellij.remoterobot.search.locators.Locators;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.intellij.remoterobot.search.locators.Locators.byXpath;

public class TestAsca extends BaseUITest {

    public void clickAscaCheckbox(){
        openSettings();
        waitFor(() -> hasAnyComponent(ASCA_CHECKBOX));
        click(ASCA_CHECKBOX);
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }

    public void validateAscaRunning(){
        openSettings();
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }

    @Test
    @Video
    public void testClickAscaCheckbox() {
        clickAscaCheckbox();
        click(ASCA_CHECKBOX);
        click(OK_BTN);
    }

    @Test
    @Video
    public void clickAscaCheckbox_ExitSetting_OpenSetting_ValidateAscaRunning_Success() {
        clickAscaCheckbox();
        click(OK_BTN);
        validateAscaRunning();
        click(OK_BTN);
    }

@Test
@Video
public void testGetVul() {
    clickAscaCheckbox();
    click(OK_BTN);

    ComponentFixture stripeButton = find(ComponentFixture.class, "//div[contains(@tooltiptext.key, 'title.project')]", waitDuration);
    try {
        ComponentFixture stripeButton1 = find(ComponentFixture.class, "//div[@class='JBViewport'][.//div[@class='ProjectViewTree']]", Duration.ofSeconds(1));
        stripeButton1.click(stripeButton1.findAllText().get(0).getPoint());
    } catch (Exception e) {
        stripeButton.click();
    }

    String[] path = {"webgoat-lessons", "challenge", "src", "main", "java", "challenge5", "Assignment5"};
    for (String step : path) {
        enter(step);
    }

    click("//div[@text.key='toolwindow.stripe.Problems_View']");
    click("//div[@class='BaseLabel' and @text='Problems:']");
    enter("Unsafe SQL Query Construction");

    waitFor(() -> hasAnyComponent("//div[contains(@mytext, 'Unsafe SQL Query Construction - Consider using prepared statements instead of concatenation when building SQL statement.')]"));
}

    protected static void enter(String value) {
        Keyboard keyboard = new Keyboard(remoteRobot);
        waitFor(() -> {
            keyboard.enterText(value);
            return hasAnyComponent(String.format(VISIBLE_TEXT, value));
        });
        keyboard.enter();
    }
}