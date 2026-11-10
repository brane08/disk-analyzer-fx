package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.FileNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(name = "top", description = "List the largest files (or folders) under a path.")
public final class TopCommand extends BaseCommand {
    @Parameters(index = "0", description = "Folder to analyze.")
    Path path;

    @Option(names = "-n", defaultValue = "10", description = "How many entries (default: ${DEFAULT-VALUE}).")
    int limit;

    @Option(names = "--dirs", description = "List folders instead of files.")
    boolean dirs;

    @Override
    public Integer call() {
        FileNode root = scan(path);
        if (root == null) return EXIT_BAD_PATH;
        out().print(Render.top(root, !dirs, limit, ansi()));
        out().flush();
        return 0;
    }
}
