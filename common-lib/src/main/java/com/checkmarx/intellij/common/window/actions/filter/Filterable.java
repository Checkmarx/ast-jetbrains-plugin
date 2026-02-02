package com.checkmarx.intellij.common.window.actions.filter;

import javax.swing.*;
import java.util.function.Supplier;

public interface Filterable {

    default Icon getIcon() {
        return null;
    }

    Supplier<String> tooltipSupplier();
    String getFilterValue();
}
