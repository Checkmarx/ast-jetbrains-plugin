package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.Keyboard;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.time.Duration;
import java.util.List;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;
import static com.intellij.remoterobot.search.locators.Locators.byXpath;

public class TestAsca extends BaseUITest {

    public void clickAscaCheckbox() {
        openSettings();
        waitFor(() -> hasAnyComponent(ASCA_CHECKBOX));
        if(!isCheckBoxChecked(ASCA_CHECKBOX))
            click(ASCA_CHECKBOX);
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }

    private boolean isCheckBoxChecked(String ascaCheckbox) {
        ComponentFixture checkbox = remoteRobot.find(
                ComponentFixture.class,
                byXpath(ascaCheckbox),
                Duration.ofSeconds(5)
        );
        boolean checked = (boolean) checkbox.callJs("component.isSelected()");
        return checked;
    }
    public void validateAscaRunning() {
        openSettings();
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }

    @Test
    @Video
    public void testClickAscaCheckbox() {
        clickAscaCheckbox();
        click(OK_BTN);
    }

    @Test
    @Video
    public void clickAscaCheckbox_ExitSetting_OpenSetting_ValidateAscaRunning_Success() {
        clickAscaCheckbox();
        click(OK_BTN);
        validateAscaRunning();
        click(OK_BTN);
        if (hasAnyComponent(OK_BTN)) {
            click(OK_BTN);
        }
    }

    @Test
    @Video
    public void AscaCheckboxEnabled_EnteringFileWithVulnerabilities_AscaVulnerabilityExist() {
        openCxToolWindow();
        clickAscaCheckbox();
        click(OK_BTN);
        hideToolWindows();
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
        find(EXPAND_ALL_FOLDER).click();
        clickExactFileInProjectTree("Assignment5");


        // Open the Problems view and search for a specific problem
        click("//div[contains(@text.key, 'toolwindow.stripe.Problems_View')]");
        click("//div[@class='BaseLabel' and @text='Problems:']");

        ComponentFixture problems = find(ComponentFixture.class, "//div[@class='Tree']", waitDuration);

        waitFor(() -> {
            List<RemoteText> textList = problems.findAllText();
            return textList.stream().anyMatch(t -> t.getText().contains("ASCA"));
        });

        Assertions.assertTrue(problems.findAllText().stream().anyMatch(t -> t.getText().contains("ASCA")));
    }

    protected static void enter(String value) {
        Keyboard keyboard = new Keyboard(remoteRobot);

        keyboard.enterText(value);
        var comp = remoteRobot.find(ComponentFixture.class, byXpath("//ProjectViewTree"));
        System.out.println(comp.getData());

        waitFor(() -> {
            String xpath = String.format("//*[@AccessibleName='%s']", value);

            return hasAnyComponent(xpath);
        });

        keyboard.enter();
    }

    protected static void clickExactFileInProjectTree(String fileName) {
        Keyboard keyboard = new Keyboard(remoteRobot);

        keyboard.enterText(fileName);
        ComponentFixture projectViewTree = find(ComponentFixture.class, "//div[@class='ProjectViewTree']", waitDuration);

        RemoteText fileNode = projectViewTree.findAllText().stream()
                .filter(t -> t.getText().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + fileName));

        projectViewTree.doubleClick(fileNode.getPoint());
    }


}