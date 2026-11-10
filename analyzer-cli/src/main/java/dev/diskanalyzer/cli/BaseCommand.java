package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.DiskScanner;
import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.ScanListener;
import dev.diskanalyzer.core.ScanProgress;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

abstract class BaseCommand implements Callable<Integer> {
    static final int EXIT_BAD_PATH = 2;

    @Spec
    CommandSpec spec;

    @Option(names = "--no-color", description = "Disable ANSI colors.")
    boolean noColor;

    Ansi ansi() {
        return noColor ? Ansi.OFF : Ansi.AUTO;
    }

    PrintWriter out() {
        return spec.commandLine().getOut();
    }

    PrintWriter err() {
        return spec.commandLine().getErr();
    }

    /** Scans {@code path}; prints an error and returns null when it does not exist. */
    FileNode scan(Path path) {
        if (!Files.exists(path)) {
            err().println("analyzer: path not found: " + path);
            return null;
        }
        boolean live = System.console() != null;
        FileNode root = new DiskScanner(path, new ScanListener() {
            @Override
            public void onProgress(ScanProgress p) {
                if (live) System.err.print("\rScanning… " + p.items() + " items");
            }
        }).scan().orElse(null);
        if (live) System.err.print("\r\033[K");
        return root;
    }
}
