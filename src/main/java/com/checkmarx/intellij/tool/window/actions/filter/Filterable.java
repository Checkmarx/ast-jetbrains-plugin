package com.checkmarx.intellij.tool.window.actions.filter;

import javax.swing.*;
import java.util.function.Supplier;

public interface Filterable {

    Icon getIcon();
    Supplier<String> tooltipSupplier();
}
