package com.checkmarx.intellij.ast.test.unit.components;

import com.checkmarx.intellij.components.PaneUtils;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaneUtilsTest {

    @Test
    void inScrollPane_CreatesScrollPaneWithNoBorders() {
        // Arrange
        JPanel testComponent = new JPanel();
        JScrollPane mockScrollPane = new JScrollPane(testComponent);
        
        try (MockedStatic<ScrollPaneFactory> factoryMock = mockStatic(ScrollPaneFactory.class)) {
            factoryMock.when(() -> ScrollPaneFactory.createScrollPane(testComponent, SideBorder.NONE))
                    .thenReturn(mockScrollPane);

            // Act
            JScrollPane result = PaneUtils.inScrollPane(testComponent);

            // Assert
            assertSame(mockScrollPane, result, "Should return the scroll pane from the factory");
            factoryMock.verify(() -> ScrollPaneFactory.createScrollPane(testComponent, SideBorder.NONE));
        }
    }

    @Test
    void inVerticalScrollPane_CreatesScrollPaneWithVerticalScrollingOnly() {
        // Arrange
        JPanel testComponent = new JPanel();
        JScrollPane mockScrollPane = spy(new JScrollPane(testComponent));
        
        try (MockedStatic<ScrollPaneFactory> factoryMock = mockStatic(ScrollPaneFactory.class)) {
            factoryMock.when(() -> ScrollPaneFactory.createScrollPane(testComponent, SideBorder.NONE))
                    .thenReturn(mockScrollPane);

            // Act
            JScrollPane result = PaneUtils.inVerticalScrollPane(testComponent);

            // Assert
            assertSame(mockScrollPane, result, "Should return the scroll pane from the factory");
            assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, 
                    result.getHorizontalScrollBarPolicy(), 
                    "Horizontal scrollbar should be disabled");
            verify(mockScrollPane).setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        }
    }

    @Test
    void inScrollPane_WithNullComponent_CreatesScrollPane() {
        // Arrange
        Component nullComponent = null;
        JScrollPane mockScrollPane = new JScrollPane();
        
        try (MockedStatic<ScrollPaneFactory> factoryMock = mockStatic(ScrollPaneFactory.class)) {
            factoryMock.when(() -> ScrollPaneFactory.createScrollPane(null, SideBorder.NONE))
                    .thenReturn(mockScrollPane);

            // Act
            JScrollPane result = PaneUtils.inScrollPane(nullComponent);

            // Assert
            assertSame(mockScrollPane, result, "Should return the scroll pane from the factory even with null component");
            factoryMock.verify(() -> ScrollPaneFactory.createScrollPane(null, SideBorder.NONE));
        }
    }

    @Test
    void inVerticalScrollPane_WithNullComponent_CreatesScrollPane() {
        // Arrange
        Component nullComponent = null;
        JScrollPane mockScrollPane = spy(new JScrollPane());
        
        try (MockedStatic<ScrollPaneFactory> factoryMock = mockStatic(ScrollPaneFactory.class)) {
            factoryMock.when(() -> ScrollPaneFactory.createScrollPane(null, SideBorder.NONE))
                    .thenReturn(mockScrollPane);

            // Act
            JScrollPane result = PaneUtils.inVerticalScrollPane(nullComponent);

            // Assert
            assertSame(mockScrollPane, result, "Should return the scroll pane from the factory even with null component");
            assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, 
                    result.getHorizontalScrollBarPolicy(), 
                    "Horizontal scrollbar should be disabled");
            verify(mockScrollPane).setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        }
    }
}