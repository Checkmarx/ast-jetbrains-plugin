package com.checkmarx.intellij.commands.results;

import com.checkmarx.ast.results.structure.CxResultOutput;
import lombok.Data;

/**
 * State resulting from getting results.
 * Has scan id of the results, the results, a tree built from the results and a component for displaying them
 */
@Data
public class ResultGetState {
    private String scanId;
    private String scanIdFieldValue;
    private boolean latest;
    private CxResultOutput resultOutput = Results.emptyResults;
    private String message = null;

    public ResultGetState() {

    }
}
