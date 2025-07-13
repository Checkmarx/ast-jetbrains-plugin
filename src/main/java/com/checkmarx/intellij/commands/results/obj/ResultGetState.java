package com.checkmarx.intellij.commands.results.obj;

import com.checkmarx.intellij.commands.results.Results;
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
    private com.checkmarx.ast.results.Results resultOutput = Results.emptyResults;
    private String message = null;

    public ResultGetState() {

    }
}
