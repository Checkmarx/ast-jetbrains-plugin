package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.util.ui.EmptyIcon;
import lombok.Getter;

import javax.swing.*;
import java.util.Set;
import java.util.function.Supplier;

/**
 * States.
 */
@Getter
public enum ResultState implements Filterable {
    CONFIRMED("Confirmed"),
    TO_VERIFY("To Verify"),
    URGENT("Urgent"),
    NOT_EXPLOITABLE("Not Exploitable"),
    PROPOSED_NOT_EXPLOITABLE("Proposed Not Exploitable"),
    ;

    public static final Set<Filterable> DEFAULT_STATES = Set.of(CONFIRMED, TO_VERIFY, URGENT);

    private final String label;

    ResultState(String label) {
        this.label = label;
    }

    @Override
    public Supplier<String> tooltipSupplier() {
        return this::getLabel;
    }

    public static ResultState fromID(String id) {
        if (id.startsWith("Checkmarx.")) {
            return ResultState.valueOf(id.substring(id.indexOf('.') + 1).toUpperCase());
        }
        throw new IllegalArgumentException("Invalid ID for result state");
    }
}
