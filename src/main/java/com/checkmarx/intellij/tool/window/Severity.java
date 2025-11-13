package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.icons.AllIcons;
import lombok.Getter;

import javax.swing.*;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Link severity with an icon.
 */
@Getter
public enum Severity implements Filterable {
    MALICIOUS(CxIcons.Medium.MALICIOUS),
    CRITICAL(CxIcons.Medium.CRITICAL),
    HIGH(CxIcons.Medium.HIGH),
    MEDIUM(CxIcons.Medium.MEDIUM),
    LOW(CxIcons.Medium.LOW),
    ;

    public static final Set<Filterable> DEFAULT_SEVERITIES = Set.of(CRITICAL, HIGH, MEDIUM);

    private final Icon icon;

    Severity(Icon icon) {
        this.icon = icon;
    }

    public Supplier<String> tooltipSupplier() {
        return this::toString;
    }

    @Override
    public String getFilterValue() {
        return this.name();
    }

    public static Severity fromID(String id) {
        if (id.startsWith("Checkmarx.")) {
            return Severity.valueOf(id.substring(id.indexOf('.') + 1).toUpperCase());
        }
        throw new IllegalArgumentException("Invalid ID for severity");
    }
}