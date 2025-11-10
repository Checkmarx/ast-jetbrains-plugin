package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import lombok.Getter;

import javax.swing.*;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Link severity with an icon.
 */
@Getter
public enum Severity implements Filterable {
    MALICIOUS(() -> CxIcons.getMaliciousIcon()),
    CRITICAL(() -> CxIcons.getCriticalIcon()),
    HIGH(() -> CxIcons.getHighIcon()),
    MEDIUM(() -> CxIcons.getMediumIcon()),
    LOW(() -> CxIcons.getLowIcon()),
    INFO(() -> CxIcons.getInfoIcon()) ;

    public static final Set<Filterable> DEFAULT_SEVERITIES = Set.of(MALICIOUS,CRITICAL, HIGH, MEDIUM);

    private final Supplier<Icon> iconSupplier;

    Severity(Supplier<Icon> iconSupplier) {
        this.iconSupplier = iconSupplier;
    }

    @Override
    public Icon getIcon() {
        return iconSupplier.get();
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
