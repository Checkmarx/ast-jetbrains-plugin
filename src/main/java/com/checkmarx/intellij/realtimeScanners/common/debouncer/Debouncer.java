package com.checkmarx.intellij.realtimeScanners.common.debouncer;

public interface Debouncer {
    void debounce(String uri,Runnable task,int delay);
    void cancel(String uri);
    void dispose();
}
