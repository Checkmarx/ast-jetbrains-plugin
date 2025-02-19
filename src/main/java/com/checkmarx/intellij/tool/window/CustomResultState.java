package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class CustomResultState implements Filterable {
    private final String label;

    public CustomResultState(String label) {
        this.label = label;
    }

    @Override
    public Supplier<String> tooltipSupplier() {
        return this::getLabel;
    }

}
