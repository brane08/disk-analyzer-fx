package dev.diskanalyzer.core;

import java.nio.file.Path;

public record ScanProgress(long items, long bytes, long skippedDirs, Path current) { }
