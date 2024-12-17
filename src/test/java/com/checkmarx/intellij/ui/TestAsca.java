package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.time.Duration;

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
public void testGetVul() {
//oat-lessons

    // Attempt to find and click the project side tab button
    ComponentFixture projectSideTabButton = find(ComponentFixture.class, "//div[contains(@tooltiptext.key, 'title.project')]", waitDuration);
    try {
        // We assume that project side tab is open, trying to click the project view tree
        ComponentFixture projectViewTree = find(ComponentFixture.class, "//div[@class='JBViewport'][.//div[@class='ProjectViewTree']]", Duration.ofSeconds(1));
        Point webGoatRootDirectoryPoint = projectViewTree.findAllText().get(0).getPoint();
        projectViewTree.click(webGoatRootDirectoryPoint);
    } catch (Exception e) {
        // If the project side tab button is not open, click the project side tab button
        projectSideTabButton.click();
    }

    // Navigate through the project directory to the specific file path
    String[] path = {"webgoat-lessons", "challenge", "src", "main", "java", "challenge5", "Assignment5"};
    for (String step : path) {
        enter(step);
    }

    // Open the Problems view and search for a specific problem
    click("//div[contains(@text.key, 'toolwindow.stripe.Problems_View')]");
    click("//div[@class='BaseLabel' and @text='Problems:']");

    enter("Unsafe SQL Query Construction");

    // Wait for the problem to be detected and displayed
    waitFor(() -> hasAnyComponent("//div[contains(@mytext, 'Unsafe SQL Query Construction - Consider using prepared statements instead of concatenation when building SQL statement.')]"));

    openCxToolWindow();
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