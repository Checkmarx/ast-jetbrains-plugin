package com.checkmarx.intellij.tool.window;

import com.intellij.icons.AllIcons;
import lombok.Getter;

import javax.swing.*;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Link severity with an icon.
 */
@Getter
public enum Severity {
    HIGH(AllIcons.General.Error),
    MEDIUM(AllIcons.General.Warning),
    LOW(AllIcons.General.Information),
    INFO(AllIcons.General.Note),
    ;

    public static final Set<Severity> DEFAULT_SEVERITIES = Set.of(HIGH, MEDIUM);

    private final Icon icon;

    Severity(Icon icon) {
        this.icon = icon;
    }

    public Supplier<String> tooltipSupplier() {
        return this::toString;
    }

    public static Severity fromID(String id) {
        if (id.startsWith("Checkmarx.")) {
            return Severity.valueOf(id.substring(id.indexOf('.') + 1).toUpperCase());
        }
        throw new IllegalArgumentException("Invalid ID for severity");
    }
}
