package com.checkmarx.intellij.devassist.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    private int line;
    private int startIndex;
    private int endIndex;
}
