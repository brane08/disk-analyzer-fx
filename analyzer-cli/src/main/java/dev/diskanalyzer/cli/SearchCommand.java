package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.SearchIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(name = "search", description = "Find files and folders by name (case-insensitive, literal).")
public final class SearchCommand extends BaseCommand {
    @Parameters(index = "0", description = "Folder to search.")
    Path path;

    @Parameters(index = "1", description = "Text to look for in names.")
    String query;

    @Option(names = "-n", defaultValue = "20", description = "Max results (default: ${DEFAULT-VALUE}).")
    int limit;

    @Override
    public Integer call() {
        FileNode root = scan(path);
        if (root == null) return EXIT_BAD_PATH;
        out().print(Render.hits(root, SearchIndex.of(root).search(query, limit), ansi()));
        out().flush();
        return 0;
    }
}
