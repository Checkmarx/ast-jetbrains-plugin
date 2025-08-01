package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.service.StateService;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * State object for not sensitive global settings for the plugin.
 */
@Getter
@Setter
@EqualsAndHashCode
@State(name = Constants.GLOBAL_SETTINGS_STATE_NAME, storages = @Storage(Constants.GLOBAL_SETTINGS_STATE_FILE))
public class GlobalSettingsState implements PersistentStateComponent<GlobalSettingsState> {

    private StateService stateService = StateService.getInstance();

    public static GlobalSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsState.class);
    }

    private String validationMessage = "";

    @NotNull
    private String additionalParameters = "";

    private boolean asca = false;
    private boolean isApiKeyEnabled = false;

    @Attribute("authenticated")
    private boolean authenticated = false;

    @Attribute("lastValidationSuccess")
    private boolean lastValidationSuccess = true;

    private String refreshTokenExpiry;

    private String validationExpiry;

    public @NotNull Set<Filterable> getFilters() {
        if (filters.isEmpty() || filters.stream().allMatch(Objects::isNull)) {
            filters = stateService.getDefaultFilters();
        }
        return filters;
    }

    @NotNull
    private Set<Filterable> filters = stateService.getDefaultFilters();

    private String baseUrl = "";
    private String tenant = "";

    @Attribute("validationInProgress")
    private boolean validationInProgress = false;

    @Override
    public @Nullable GlobalSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GlobalSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void apply(@NotNull GlobalSettingsState state) {
        loadState(state);
    }
}

