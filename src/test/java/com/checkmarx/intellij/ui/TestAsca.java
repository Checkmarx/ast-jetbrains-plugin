package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.search.locators.Locators;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

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
    public void testGetVul() throws InterruptedException {
        clickAscaCheckbox();
        click(OK_BTN);
        click("//div[contains(@tooltiptext.key, 'title.project')]");

        enter("webgoat");
        enter("webgoat-lessons");
        enter("challenge");
        enter("src");
        enter("main");
        enter("java");
        enter("challenge5");
        enter("Assignment5");

        click("//div[@text.key='toolwindow.stripe.Problems_View']");
        click("//div[@class='BaseLabel' and @text='Problems:']");
        enter("ASCA");
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