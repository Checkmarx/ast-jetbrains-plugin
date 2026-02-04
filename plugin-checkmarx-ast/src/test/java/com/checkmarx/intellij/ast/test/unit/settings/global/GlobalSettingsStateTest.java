package com.checkmarx.intellij.ast.test.unit.settings.global;

import com.checkmarx.intellij.service.StateService;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.application.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;

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
        StateService stateService = StateService.getInstance();
        assertEquals("", globalSettingsState.getAdditionalParameters());
        assertFalse(globalSettingsState.isAsca());
        assertEquals(stateService.getDefaultFilters(), globalSettingsState.getFilters());
    }
} 