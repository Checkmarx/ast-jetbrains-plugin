package com.checkmarx.intellij.unit.tool.window.actions.filter;

import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.FilterBaseAction;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FilterBaseActionTest {

    @Mock
    private Application mockApplication;

    @Mock
    private MessageBus mockMessageBus;

    @Mock
    private GlobalSettingsState mockSettingsState;

    @Mock
    private AnActionEvent mockEvent;

    private Set<Filterable> filters;
    private MockedStatic<ApplicationManager> appManagerMock;
    private MockedStatic<GlobalSettingsState> settingsStateMock;

    @BeforeEach
    void setUp() {
        filters = new HashSet<>();
        when(mockSettingsState.getFilters()).thenReturn(filters);
        when(mockApplication.getMessageBus()).thenReturn(mockMessageBus);
    }

    @AfterEach
    void tearDown() {
        if (appManagerMock != null) {
            appManagerMock.close();
        }
        if (settingsStateMock != null) {
            settingsStateMock.close();
        }
    }

    private void initStaticMocks() {
        if (appManagerMock != null) {
            appManagerMock.close();
        }
        if (settingsStateMock != null) {
            settingsStateMock.close();
        }
        appManagerMock = mockStatic(ApplicationManager.class);
        settingsStateMock = mockStatic(GlobalSettingsState.class);
        appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
        settingsStateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSettingsState);
    }

    @Test
    void isSelected_WhenFilterIsInGlobalSettings_ReturnsTrue() {
        initStaticMocks();
        FilterBaseAction.CriticalFilter filter = new FilterBaseAction.CriticalFilter();
        filters.add(Severity.CRITICAL);

        // Act
        boolean result = filter.isSelected(mockEvent);

        // Assert
        assertTrue(result);
    }

    @Test
    void isSelected_WhenFilterIsNotInGlobalSettings_ReturnsFalse() {
        initStaticMocks();
        FilterBaseAction.CriticalFilter filter = new FilterBaseAction.CriticalFilter();

        // Act
        boolean result = filter.isSelected(mockEvent);

        // Assert
        assertFalse(result);
    }

    @Test
    void constructor_SetsCorrectPresentationFromFilterable() {
        initStaticMocks();
        Icon testIcon = new ImageIcon();
        String testTooltip = "Test Tooltip";
        Filterable testFilterable = new Filterable() {
            @Override
            public Icon getIcon() {
                return testIcon;
            }

            @Override
            public Supplier<String> tooltipSupplier() {
                return () -> testTooltip;
            }
        };

        FilterBaseAction testFilter = new FilterBaseAction() {
            @Override
            protected Filterable getFilterable() {
                return testFilterable;
            }
        };

        // Assert
        assertEquals(testTooltip, testFilter.getTemplatePresentation().getText());
        assertEquals(testIcon, testFilter.getTemplatePresentation().getIcon());
    }
} 