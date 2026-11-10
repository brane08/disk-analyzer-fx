package dev.diskanalyzer.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "analyzer", mixinStandardHelpOptions = true, version = "analyzer 1.0",
        description = "Disk space analyzer.",
        subcommands = {ScanCommand.class, TopCommand.class, SearchCommand.class, CachesCommand.class})
public final class Main implements Runnable {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
