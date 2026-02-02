package com.checkmarx.intellij.common.devassist.common;

import com.checkmarx.intellij.common.devassist.model.ScanIssue;

import java.util.List;

/**
 * Interface for a scan result.
 *
 * @param <T>
 */
public interface ScanResult<T> {

    /**
     * Retrieves the results of a scan operation.
     * This will be used to get the actual results from the original scan engine scan
     *
     * @return the results of the scan as an object of type T
     */
    T getResults();

    /**
     * Get issues from a scan result. Default implementation returns empty list.
     * This method wraps an actual scan result and provides a meaningful scan issues list with required details.
     *
     * @return list of issues
     */
    List<ScanIssue> getIssues();
}
