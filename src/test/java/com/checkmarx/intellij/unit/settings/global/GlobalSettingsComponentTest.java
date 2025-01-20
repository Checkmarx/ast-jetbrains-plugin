package com.checkmarx.intellij.unit.settings.global;

import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.SettingsListener;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsComponentTest {

    @Mock
    private Application mockApplication;

    @Mock
    private MessageBus mockMessageBus;

    @Mock
    private MessageBusConnection mockConnection;

    @Mock
    private GlobalSettingsState mockSettingsState;

    @Mock
    private GlobalSettingsSensitiveState mockSensitiveState;

    @Mock
    private SettingsListener mockSettingsListener;

    @Mock
    private EditorColorsManager mockEditorColorsManager;

    @Mock
    private EditorColorsScheme mockEditorColorsScheme;

    @Mock
    private PasswordSafe mockPasswordSafe;

    private GlobalSettingsComponent component;

    private MockedStatic<ApplicationManager> appManagerMock;
    private MockedStatic<GlobalSettingsState> settingsStateMock;
    private MockedStatic<GlobalSettingsSensitiveState> sensitiveMock;
    private MockedStatic<EditorColorsManager> editorColorsMock;
    private MockedStatic<PasswordSafe> passwordSafeMock;

    @BeforeEach
    void setUp() {
        appManagerMock = mockStatic(ApplicationManager.class);
        settingsStateMock = mockStatic(GlobalSettingsState.class);
        sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class);
        editorColorsMock = mockStatic(EditorColorsManager.class);
        passwordSafeMock = mockStatic(PasswordSafe.class);

        appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
        when(mockApplication.getMessageBus()).thenReturn(mockMessageBus);

        settingsStateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSettingsState);
        sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitiveState);
        passwordSafeMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);

        // Mock EditorColorsManager and font
        editorColorsMock.when(EditorColorsManager::getInstance).thenReturn(mockEditorColorsManager);
        when(mockEditorColorsManager.getGlobalScheme()).thenReturn(mockEditorColorsScheme);
        Font mockFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        when(mockEditorColorsScheme.getFont(any(EditorFontType.class))).thenReturn(mockFont);

        component = new GlobalSettingsComponent();
    }

    @AfterEach
    void tearDown() {
        appManagerMock.close();
        settingsStateMock.close();
        sensitiveMock.close();
        editorColorsMock.close();
        passwordSafeMock.close();
    }

    @Test
    void isModified_WithModifiedAdditionalParameters_ReturnsTrue() {
        // Arrange
        component.reset();

        // Set text to additionalParametersField
        component.getAdditionalParametersField().setText("modified");
        // Act
        boolean result = component.isModified();

        // Assert
        assertTrue(result);
    }

    @Test
    void reset_ResetsAllFields() {
        // Arrange
        String additionalParams = "--test-params";
        boolean ascaEnabled = true;
        String apiKey = "test-api-key";

        when(mockSettingsState.getAdditionalParameters()).thenReturn(additionalParams);
        when(mockSettingsState.isAsca()).thenReturn(ascaEnabled);
        when(mockSensitiveState.getApiKey()).thenReturn(apiKey);

        // Act
        component.reset();

        JBPasswordField apiKeyField = component.getApiKeyField();
        ExpandableTextField additionalParamsField = component.getAdditionalParametersField();
        JBCheckBox ascaCheckbox = component.getAscaCheckBox();

        assertEquals(additionalParams, additionalParamsField.getText());
        assertEquals(ascaEnabled, ascaCheckbox.isSelected());
        assertEquals(apiKey, new String(apiKeyField.getPassword()));
    }
} 