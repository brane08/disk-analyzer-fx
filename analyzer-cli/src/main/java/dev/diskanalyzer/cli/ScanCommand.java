package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.FileNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(name = "scan", description = "Show a colored size tree for a folder.")
public final class ScanCommand extends BaseCommand {
    @Parameters(index = "0", description = "Folder to analyze.")
    Path path;

    @Option(names = "--depth", defaultValue = "2", description = "Levels to show (default: ${DEFAULT-VALUE}).")
    int depth;

    @Option(names = "--top", defaultValue = "10", description = "Entries per folder (default: ${DEFAULT-VALUE}).")
    int top;

    @Override
    public Integer call() {
        FileNode root = scan(path);
        if (root == null) return EXIT_BAD_PATH;
        out().print(Render.tree(root, depth, top, ansi()));
        out().flush();
        return 0;
    }
}
