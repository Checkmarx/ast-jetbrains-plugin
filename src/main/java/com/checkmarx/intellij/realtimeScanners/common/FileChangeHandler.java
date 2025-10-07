package com.checkmarx.intellij.realtimeScanners.common;

import com.checkmarx.intellij.realtimeScanners.common.debouncer.Debouncer;
import org.jetbrains.annotations.NotNull;

public class FileChangeHandler {
    private final Debouncer debouncer;
    private final int debounceTimeInMilli;

    public FileChangeHandler(Debouncer debouncer, int debounceTimeInMilli ) {
        this.debouncer = debouncer;
        this.debounceTimeInMilli = debounceTimeInMilli;
    }

    public void onTextChanged(@NotNull String fileUri, @NotNull Runnable scanAction) {
        debouncer.debounce(fileUri, scanAction, debounceTimeInMilli);
    }

    public void dispose() {
        debouncer.dispose();
    }
}
