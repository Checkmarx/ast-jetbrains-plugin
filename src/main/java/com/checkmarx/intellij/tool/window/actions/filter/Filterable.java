package com.checkmarx.intellij.tool.window.actions.filter;

import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.util.function.Supplier;

public interface Filterable {

    default Icon getIcon() {
        return EmptyIcon.ICON_0;
    }

    Supplier<String> tooltipSupplier();
}
