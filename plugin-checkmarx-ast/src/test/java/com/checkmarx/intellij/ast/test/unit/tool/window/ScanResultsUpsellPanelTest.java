package com.checkmarx.intellij.ast.test.unit.tool.window;

import com.checkmarx.intellij.ast.window.ScanResultsUpsellPanel;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.intellij.ide.BrowserUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScanResultsUpsellPanel.
 */
@ExtendWith(MockitoExtension.class)
class ScanResultsUpsellPanelTest {

    private ScanResultsUpsellPanel createPanel() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(Resource.UPSELL_SCAN_RESULTS_TITLE)).thenReturn("Upgrade Title");
            mockedBundle.when(() -> Bundle.message(Resource.UPSELL_SCAN_RESULTS_DESCRIPTION))
                    .thenReturn("Upgrade your plan. Get more features.");
            mockedBundle.when(() -> Bundle.message(Resource.UPSELL_SCAN_RESULTS_BUTTON)).thenReturn("Learn More");
            return new ScanResultsUpsellPanel();
        }
    }

    @Test
    void constructor_CreatesPanel_WithMigLayout() {
        ScanResultsUpsellPanel panel = createPanel();
        assertNotNull(panel.getLayout());
        assertTrue(panel.getLayout() instanceof net.miginfocom.swing.MigLayout);
    }

    @Test
    void constructor_AddsThreeComponents_TitleDescriptionButton() {
        ScanResultsUpsellPanel panel = createPanel();
        assertEquals(3, panel.getComponentCount());
    }

    @Test
    void constructor_TitleLabel_HasBoldFont() {
        ScanResultsUpsellPanel panel = createPanel();
        // First component is the title label
        Component titleComponent = panel.getComponent(0);
        assertTrue(titleComponent instanceof JLabel);
        JLabel titleLabel = (JLabel) titleComponent;
        assertEquals(Font.BOLD, titleLabel.getFont().getStyle());
    }

    @Test
    void constructor_DescriptionLabel_ContainsHtmlWrapping() {
        ScanResultsUpsellPanel panel = createPanel();
        Component descComponent = panel.getComponent(1);
        assertTrue(descComponent instanceof JLabel);
        JLabel descLabel = (JLabel) descComponent;
        assertTrue(descLabel.getText().contains("<html>"));
    }

    @Test
    void constructor_Button_HasWhiteForeground() {
        ScanResultsUpsellPanel panel = createPanel();
        Component btnComponent = panel.getComponent(2);
        assertTrue(btnComponent instanceof JButton);
        JButton btn = (JButton) btnComponent;
        // White foreground: RGB 255,255,255
        assertEquals(255, btn.getForeground().getRed());
        assertEquals(255, btn.getForeground().getGreen());
        assertEquals(255, btn.getForeground().getBlue());
    }

    @Test
    void constructor_Button_HasHandCursor() {
        ScanResultsUpsellPanel panel = createPanel();
        JButton btn = (JButton) panel.getComponent(2);
        assertEquals(Cursor.HAND_CURSOR, btn.getCursor().getType());
    }

    @Test
    void createButton_ActionListener_BrowsesToCorrectUrl() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class);
             MockedStatic<BrowserUtil> mockedBrowser = mockStatic(BrowserUtil.class)) {

            mockedBundle.when(() -> Bundle.message(Resource.UPSELL_SCAN_RESULTS_TITLE)).thenReturn("Title");
            mockedBundle.when(() -> Bundle.message(Resource.UPSELL_SCAN_RESULTS_DESCRIPTION)).thenReturn("Desc. More.");
            mockedBundle.when(() -> Bundle.message(Resource.UPSELL_SCAN_RESULTS_BUTTON)).thenReturn("Learn More");

            ScanResultsUpsellPanel panel = new ScanResultsUpsellPanel();
            JButton btn = (JButton) panel.getComponent(2);

            // Trigger the action listener
            for (var listener : btn.getActionListeners()) {
                listener.actionPerformed(new ActionEvent(btn, ActionEvent.ACTION_PERFORMED, ""));
            }

            mockedBrowser.verify(() -> BrowserUtil.browse(
                    "https://docs.checkmarx.com/en/34965-68736-using-the-checkmarx-one-jetbrains-plugin.html"));
        }
    }

    @Test
    void constructor_TitleLabel_HasCenterAlignment() {
        ScanResultsUpsellPanel panel = createPanel();
        JLabel titleLabel = (JLabel) panel.getComponent(0);
        assertEquals(SwingConstants.CENTER, titleLabel.getHorizontalAlignment());
    }

    @Test
    void constructor_DescriptionLabel_HasCenterAlignment() {
        ScanResultsUpsellPanel panel = createPanel();
        JLabel descLabel = (JLabel) panel.getComponent(1);
        assertEquals(SwingConstants.CENTER, descLabel.getHorizontalAlignment());
    }
}

