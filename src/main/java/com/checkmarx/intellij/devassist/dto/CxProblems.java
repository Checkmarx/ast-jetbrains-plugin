package com.checkmarx.intellij.devassist.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
public class CxProblems {

    private int column;
    private String severity;      // e.g. "Critical", "High", "Medium", etc.
    private String title;         // Rule name or Package name
    private String description;   // Human-readable description of the issue
    private String remediationAdvise; // Fix suggestion, if available
    private String packageVersion; // May be null for rule-based issues.
    private String cve;            // If a single CVE (or null, if not applicable)
    private String scannerType;

    // One or multiple vulnerable code ranges
    private List<Location> locations = new ArrayList<>();

    public static class Location {
        private int line;
        private int start;
        private int end;

        public Location(int line, int start, int end) {
            this.line = line;
            this.start = start;
            this.end = end;
        }

        public int getLine() { return line; }
        public int getColumnStart() { return start; }
        public int getColumnEnd() { return end; }
    }

    public void addLocation(int line, int start, int end) {
        locations.add(new Location(line, start, end));
    }
}
