package com.checkmarx.intellij.devassist.ignore;

import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IgnoreEntry {
    public List<FileReference> files = new ArrayList<>();
    public ScanEngine type; // or enum
    public String similarityId;
    public String packageManager;
    public String packageName;
    public String packageVersion;
    public Integer ruleId;
    public String imageName;
    public String imageTag;
    public String severity;
    public String description;
    public String dateAdded;
    public String title;
    public String secretValue;

    public IgnoreEntry() {
    }

    @Getter
    @Setter
    public static final class FileReference {
        public String path;
        public boolean active;
        public Integer line;

        public FileReference() {}

        public FileReference(String relativePath, boolean b, int line) {
            this.path = relativePath;
            this.active = b;
            this.line = line;
        }
    }
}

