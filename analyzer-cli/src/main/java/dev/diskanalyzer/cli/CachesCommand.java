package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.CacheCatalog;
import dev.diskanalyzer.core.CacheLocation;
import dev.diskanalyzer.core.FileNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "caches", description = "Show developer caches (Xcode, npm, Homebrew, ...) and their sizes.")
public final class CachesCommand extends BaseCommand {
    @Option(names = "--home", description = "Home directory to inspect (default: user.home).")
    Path home = Path.of(System.getProperty("user.home"));

    @Override
    public Integer call() {
        long total = 0;
        for (CacheLocation c : new CacheCatalog(home).existing()) {
            FileNode node = scan(c.path());
            if (node == null) continue;
            total += node.size();
            out().print(Render.cacheRow(c.label(), node.size(), c.path().toString(), ansi()));
        }
        out().print(Render.cacheRow("Total", total, "", ansi()));
        out().flush();
        return 0;
    }
}
