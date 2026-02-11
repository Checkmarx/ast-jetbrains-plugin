package com.checkmarx.intellij.ast.window.actions.group.by;

import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Enum for filters.
 * This corresponds to the parent nodes in the tree for each result.
 */
public enum GroupBy {
    SEVERITY,
    VULNERABILITY_TYPE_NAME,
    STATE,
    FILE,
    PACKAGE,
    DIRECT_DEPENDENCY,
    SCA_TYPE;

    public static final List<GroupBy> DEFAULT_GROUP_BY = Arrays.asList(SEVERITY, VULNERABILITY_TYPE_NAME,PACKAGE);
    public static final List<GroupBy> HIDDEN_GROUPS = List.of(SCA_TYPE,PACKAGE);

    /**
     * @return function to apply to a result for getting the parent, that matches the filter
     */
    public Function<Result, String> getFunction() {
        if (this == SEVERITY) {
            return Result::getSeverity;
        }
        if (this == VULNERABILITY_TYPE_NAME) {
            return (result) -> {
                // For SCS (secret detection), group by the result id
                if (Constants.SCAN_TYPE_SCS.equals(result.getType())) {
                    return result.getId();
                }
                // For all other engines, keep the existing behavior (queryName)
                return result.getData().getQueryName();
            };
        }
        if (this == FILE) {
            return (result) -> result.getData().getFileName();
        }
        if (this == STATE) {
            return Result::getState;
        }
        if (this == PACKAGE) {
            return (result) -> result.getData().getPackageIdentifier();
        }
        if (this == DIRECT_DEPENDENCY) {
            return (result) -> {
                String r = "";
                if(result.getData().getScaPackageData()!=null){
                    r = result.getData().getScaPackageData().getTypeOfDependency() ;
                }
                return r;
            };
        }
        if (this == SCA_TYPE) {
            return (result) -> result.getScaType();
        }
        throw new RuntimeException("Invalid filter");
    }

    /**
     * @return comparator to sort children of a given group
     */
    public Comparator<String> getComparator() {
        if (this == SEVERITY) {
            return Comparator.comparing(SeverityFilter::valueOf);
        }
        if (this == STATE) {
            return Comparator.comparing(CustomResultState::valueOf);
        }
        if (this == VULNERABILITY_TYPE_NAME) {
            return String::compareTo;
        }
        if (this == FILE) {
            return String::compareTo;
        }
        if (this == PACKAGE) {
            return String::compareTo;
        }
        if (this == DIRECT_DEPENDENCY) {
            return String::compareTo;
        }
        if (this == SCA_TYPE) {
            return String::compareTo;
        }
        return null;
    }
}
