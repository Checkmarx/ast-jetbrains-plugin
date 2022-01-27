package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.CxIcons;
import lombok.Getter;

import javax.swing.*;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Link severity with an icon.
 */
@Getter
public enum State {
    CONFIRMED,
    TO_VERIFY,
    NOT_EXPLOITABLE,
    URGENT,
    PROPOSED_NOT_EXPLOITABLE
    ;

    public static final Set<State> DEFAULT_STATE = Set.of(CONFIRMED, TO_VERIFY, URGENT);

    public Supplier<String> tooltipSupplier() {
        return this::toString;
    }

    public static State fromID(String id) {
        if (id.startsWith("Checkmarx.")) {
            return State.valueOf(id.substring(id.indexOf('.') + 1).toUpperCase());
        }
        throw new IllegalArgumentException("Invalid ID for state");
    }
}
