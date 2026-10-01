package com.besome.sketch.tools;

public class CompileErrorItem {
    public final String filePath;
    public final int lineNumber;
    public final String errorType;
    public final String message;
    public final String rawBlock;

    public CompileErrorItem(String filePath, int lineNumber, String errorType, String message, String rawBlock) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.errorType = errorType;
        this.message = message;
        this.rawBlock = rawBlock;
    }
}
