package com.checkmarx.intellij.devassist.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Represents a specific location within a file.
 * This class is primarily used for identifying specific ranges in code, such as
 * vulnerable code segments or other points of interest identified during a scan.
 * Instances of this class are used within scan result models such as {@code ScanIssue}.
 * <p>
 * Attributes:
 * - line: The line number in the file where the vulnerability is found.
 * - startIndex: The starting character index within the line for the vulnerability.
 * - endIndex: The ending character index within the line for the vulnerability.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    private int line;
    private int startIndex;
    private int endIndex;
}
