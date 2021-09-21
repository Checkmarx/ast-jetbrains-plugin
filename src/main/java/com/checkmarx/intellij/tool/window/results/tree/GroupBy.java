package com.checkmarx.intellij.tool.window.results.tree;

import com.checkmarx.ast.results.structure.CxResult;

import java.util.function.Function;

/**
 * Enum for filters, currently only supports by severity.
 * This corresponds to the parent nodes in the tree for each result.
 */
public enum GroupBy {
    SEVERITY,
    QUERY_NAME;

    public static final GroupBy DEFAULT_GROUP_BY = SEVERITY;

    /**
     * @return function to apply to a result for getting the parent, that matches the filter
     */
    public Function<CxResult, String> getFunction() {
        if (this == SEVERITY) {
            return CxResult::getSeverity;
        }
        if (this == QUERY_NAME) {
            return (result) -> result.getData().getQueryName() != null
                               ? result.getData().getQueryName()
                               : result.getId();
        }
        throw new RuntimeException("Invalid filter");
    }
}
