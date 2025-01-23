package com.checkmarx.intellij.unit.settings.global;

import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.ResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsStateTest {

    @Mock
    private Application mockApplication;

    private GlobalSettingsState globalSettingsState;

    @BeforeEach
    void setUp() {
        globalSettingsState = new GlobalSettingsState();
    }

    @Test
    void getState_ReturnsSelf() {
        // Act
        GlobalSettingsState result = globalSettingsState.getState();

        // Assert
        assertSame(globalSettingsState, result);
    }

    @Test
    void loadState_CopiesStateCorrectly() {
        // Arrange
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);
        Set<Filterable> newFilters = new HashSet<>();
        newFilters.add(Severity.HIGH);
        newState.setFilters(newFilters);

        // Act
        globalSettingsState.loadState(newState);

        // Assert
        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
        assertEquals(newFilters, globalSettingsState.getFilters());
    }

    @Test
    void apply_LoadsStateCorrectly() {
        // Arrange
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);

        // Act
        globalSettingsState.apply(newState);

        // Assert
        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
    }


    @Test
    void constructor_InitializesDefaultValues() {
        // Assert
        assertEquals("", globalSettingsState.getAdditionalParameters());
        assertFalse(globalSettingsState.isAsca());
        assertEquals(GlobalSettingsState.getDefaultFilters(), globalSettingsState.getFilters());
    }
} 