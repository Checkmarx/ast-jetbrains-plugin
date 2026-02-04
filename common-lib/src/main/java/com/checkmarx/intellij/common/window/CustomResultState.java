package com.checkmarx.intellij.common.window;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import lombok.Getter;

import java.util.Set;
import java.util.function.Supplier;

@Getter
public class CustomResultState implements Filterable, Comparable<CustomResultState> {
    private final String label;
    private final String name;

    public CustomResultState(String label) {
        this.label = label;
        this.name = label;
    }

    public CustomResultState(String label, String name) {
        this.label = label;
        this.name = name;
    }

    @Override
    public Supplier<String> tooltipSupplier() {
        return this::getLabel;
    }

    @Override
    public String getFilterValue() {
        return tooltipSupplier().get();
    }

    public static CustomResultState valueOf(String label) {
        Set<Filterable> filters = GlobalSettingsState.getInstance().getFilters();
        return (CustomResultState) filters.stream()
                .filter(filterable -> filterable instanceof CustomResultState)
                .filter(filterable -> ((CustomResultState) filterable).getLabel().equals(label))
                .findFirst()
                .orElse(new CustomResultState(label));
    }


    @Override
    public int compareTo(CustomResultState other) {
        return this.label.compareTo(other.label);
    }
}
