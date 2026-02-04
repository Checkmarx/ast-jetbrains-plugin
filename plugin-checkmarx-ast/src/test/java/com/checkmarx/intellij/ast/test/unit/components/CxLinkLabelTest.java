package com.checkmarx.intellij.ast.test.unit.components;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.intellij.ui.HyperlinkLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CxLinkLabelTest {

    private static final String TEST_TEXT = "Test Link";
    private static final String TEST_URL = "https://test.checkmarx.com";
    private static final Resource TEST_RESOURCE = Resource.LEARN_MORE;

    @Test
    void constructor_WithText_CreatesLabelWithCorrectText() {
        // Arrange
        AtomicBoolean clicked = new AtomicBoolean(false);

        // Act
        CxLinkLabel label = new CxLinkLabel(TEST_TEXT, e -> clicked.set(true));

        // Assert
        assertEquals(TEST_TEXT, label.getText());
        assertTrue(label instanceof HyperlinkLabel);
    }

    @Test
    void constructor_WithResource_CreatesLabelWithCorrectText() {
        // Arrange
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(TEST_RESOURCE)).thenReturn(TEST_TEXT);
            AtomicBoolean clicked = new AtomicBoolean(false);

            // Act
            CxLinkLabel label = new CxLinkLabel(TEST_RESOURCE, e -> clicked.set(true));

            // Assert
            assertEquals(TEST_TEXT, label.getText());
            assertTrue(label instanceof HyperlinkLabel);
        }
    }

    @Test
    void mouseClick_OnLabel_InvokesCallback() {
        // Arrange
        AtomicBoolean clicked = new AtomicBoolean(false);
        CxLinkLabel label = new CxLinkLabel(TEST_TEXT, e -> clicked.set(true));
        MouseEvent mockEvent = mock(MouseEvent.class);

        // Act
        label.getMouseListeners()[0].mouseClicked(mockEvent);

        // Assert
        assertTrue(clicked.get(), "Click callback should have been invoked");
    }

    @Test
    void buildDocLinkLabel_WithResourceAndLink_CreatesCorrectComponent() {
        // Arrange
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<Desktop> desktopMock = mockStatic(Desktop.class)) {
            
            bundleMock.when(() -> Bundle.message(TEST_RESOURCE)).thenReturn(TEST_TEXT);
            Desktop mockDesktop = mock(Desktop.class);
            desktopMock.when(Desktop::getDesktop).thenReturn(mockDesktop);

            // Act
            JComponent component = CxLinkLabel.buildDocLinkLabel(TEST_URL, TEST_RESOURCE);

            // Assert
            assertTrue(component instanceof CxLinkLabel);
            assertEquals(TEST_TEXT, ((CxLinkLabel) component).getText());
        }
    }

    @Test
    void buildDocLinkLabel_WithLabelAndLink_CreatesCorrectComponent() {
        // Arrange
        try (MockedStatic<Desktop> desktopMock = mockStatic(Desktop.class)) {
            Desktop mockDesktop = mock(Desktop.class);
            desktopMock.when(Desktop::getDesktop).thenReturn(mockDesktop);

            // Act
            JComponent component = CxLinkLabel.buildDocLinkLabel(TEST_URL, TEST_TEXT);

            // Assert
            assertTrue(component instanceof CxLinkLabel);
            assertEquals(TEST_TEXT, ((CxLinkLabel) component).getText());
        }
    }

    @Test
    void buildDocLinkLabel_WhenClicked_OpensURL() throws Exception {
        // Arrange
        try (MockedStatic<Desktop> desktopMock = mockStatic(Desktop.class)) {
            Desktop mockDesktop = mock(Desktop.class);
            desktopMock.when(Desktop::getDesktop).thenReturn(mockDesktop);

            JComponent component = CxLinkLabel.buildDocLinkLabel(TEST_URL, TEST_TEXT);
            MouseEvent mockEvent = mock(MouseEvent.class);

            // Act
            ((CxLinkLabel) component).getMouseListeners()[0].mouseClicked(mockEvent);

            // Assert
            verify(mockDesktop).browse(new URI(TEST_URL));
        }
    }
}