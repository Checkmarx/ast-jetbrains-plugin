package com.checkmarx.intellij.tool.window;


import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class FileNode {

    private String fileName;
    private int line;
    private int column;
}
