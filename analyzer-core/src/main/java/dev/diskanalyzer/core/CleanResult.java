package dev.diskanalyzer.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CleanResult(List<Path> removed, Map<Path, String> failed, long freedBytes) { }
