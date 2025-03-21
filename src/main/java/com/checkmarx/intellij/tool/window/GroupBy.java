package com.checkmarx.intellij.tool.window;

import com.checkmarx.ast.results.result.Result;

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
    STATE,
    FILE,
    VULNERABILITY_TYPE_NAME,
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
            return (result) -> result.getData().getQueryName();
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
            return Comparator.comparing(Severity::valueOf);
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
