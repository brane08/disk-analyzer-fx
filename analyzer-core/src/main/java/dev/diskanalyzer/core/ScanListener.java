package dev.diskanalyzer.core;

public interface ScanListener {
    default void onProgress(ScanProgress progress) { }

    default void onPartial(FileNode partialRoot) { }
}
