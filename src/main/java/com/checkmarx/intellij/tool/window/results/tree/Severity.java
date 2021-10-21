package com.checkmarx.intellij.tool.window.results.tree;

import com.intellij.icons.AllIcons;
import lombok.Getter;

import javax.swing.*;

/**
 * Link severity with an icon.
 */
public enum Severity {
    HIGH(AllIcons.General.Error),
    MEDIUM(AllIcons.General.Warning),
    LOW(AllIcons.General.Information),
    INFO(AllIcons.General.Note),
    ;

    @Getter
    private final Icon icon;

    Severity(Icon icon) {
        this.icon = icon;
    }
}
