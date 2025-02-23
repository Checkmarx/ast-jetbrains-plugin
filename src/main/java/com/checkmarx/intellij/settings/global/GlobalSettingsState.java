package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.DynamicFilterActionGroup;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.tool.window.actions.filter.DynamicFilterActionGroup.NOT_EXPLOITABLE_LABEL;
import static com.checkmarx.intellij.tool.window.actions.filter.DynamicFilterActionGroup.PROPOSED_NOT_EXPLOITABLE_LABEL;

/**
 * State object for not sensitive global settings for the plugin.
 */
@Getter
@Setter
@EqualsAndHashCode
@State(name = Constants.GLOBAL_SETTINGS_STATE_NAME, storages = @Storage(Constants.GLOBAL_SETTINGS_STATE_FILE))
public class GlobalSettingsState implements PersistentStateComponent<GlobalSettingsState> {

    private static final Logger LOGGER = Utils.getLogger(GlobalSettingsState.class);

    public static GlobalSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsState.class);
    }

    @NotNull
    private String additionalParameters = "";

    private boolean asca = false;

    public @NotNull Set<Filterable> getFilters() {
        if (filters.isEmpty() || filters.stream().allMatch(Objects::isNull)) {
            filters = getDefaultFilters();
        }
        return filters;
    }

    @NotNull
    private Set<Filterable> filters = getDefaultFilters();

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

    public static Set<Filterable> getDefaultFilters() {
        Set<Filterable> set = new HashSet<>();

        set.addAll(Severity.DEFAULT_SEVERITIES);

        // Add all states except NOT_EXPLOITABLE and PROPOSED_NOT_EXPLOITABLE
        set.addAll(DynamicFilterActionGroup.STATES.stream()
                .filter(s -> !s.getLabel().equals(NOT_EXPLOITABLE_LABEL) && !s.getLabel().equals(PROPOSED_NOT_EXPLOITABLE_LABEL))
                .collect(Collectors.toSet()));

        return set;
    }
}

