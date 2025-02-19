package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class CustomResultState implements Filterable {
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
}
