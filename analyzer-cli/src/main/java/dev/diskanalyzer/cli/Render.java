package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.FileNode;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure string rendering of scan results; ANSI codes only when {@code ansi.enabled()}. */
public final class Render {
    private static final int BAR_WIDTH = 20;
    private static final int NAME_WIDTH = 40;

    private Render() { }

    static String paint(Ansi ansi, String text, Style... styles) {
        if (!ansi.enabled()) return text;
        return Style.on(styles) + text + Style.off(styles) + Style.reset.on();
    }

    static String bar(double fraction, int width) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "█".repeat(filled) + "░".repeat(width - filled);
    }

    public static String tree(FileNode root, int depth, int top, Ansi ansi) {
        StringBuilder sb = new StringBuilder();
        append(sb, root, root.path().toString(), root.size(), 0, depth, top, ansi);
        return sb.toString();
    }

    private static void append(StringBuilder sb, FileNode n, String label, long parentSize,
                               int level, int depth, int top, Ansi ansi) {
        double frac = parentSize <= 0 ? 1.0 : (double) n.size() / parentSize;
        Style color = frac > 0.5 ? Style.fg_red : frac > 0.2 ? Style.fg_yellow : Style.fg_green;
        String left = "  ".repeat(level) + label + (n.isDir() ? "/" : "");
        String name = String.format("%-" + NAME_WIDTH + "s", left);
        sb.append(n.isDir() ? paint(ansi, name, Style.bold) : name)
                .append(' ')
                .append(paint(ansi, bar(frac, BAR_WIDTH), color))
                .append(String.format(" %10s %5.1f%%%n", FileNode.human(n.size()), frac * 100));

        if (!n.isDir() || level >= depth) return;
        List<FileNode> kids = n.children();
        int shown = Math.min(top, kids.size());
        for (int i = 0; i < shown; i++) {
            FileNode c = kids.get(i);
            append(sb, c, c.name(), n.size(), level + 1, depth, top, ansi);
        }
        if (kids.size() > shown) {
            sb.append(paint(ansi, "  ".repeat(level + 1) + "… and " + (kids.size() - shown) + " more",
                    Style.faint)).append(System.lineSeparator());
        }
    }

    /** Largest descendants of {@code root}, files or directories, with paths relative to it. */
    public static String top(FileNode root, boolean files, int limit, Ansi ansi) {
        List<FileNode> all = new ArrayList<>();
        collect(root, files, all);
        all.sort(Comparator.comparingLong(FileNode::size).reversed());
        StringBuilder sb = new StringBuilder();
        for (FileNode n : all.subList(0, Math.min(limit, all.size()))) {
            sb.append(line(root, n, ansi));
        }
        return sb.toString();
    }

    public static String hits(FileNode root, List<FileNode> hits, Ansi ansi) {
        StringBuilder sb = new StringBuilder();
        for (FileNode n : hits) {
            sb.append(line(root, n, ansi));
        }
        return sb.toString();
    }

    private static String line(FileNode root, FileNode n, Ansi ansi) {
        String rel = root.path().relativize(n.path()).toString();
        return paint(ansi, String.format("%10s", FileNode.human(n.size())), Style.fg_yellow)
                + "  " + rel + (n.isDir() ? "/" : "") + System.lineSeparator();
    }

    private static void collect(FileNode n, boolean files, List<FileNode> out) {
        for (FileNode c : n.children()) {
            if (c.isDir() != files) out.add(c);
            collect(c, files, out);
        }
    }

    public static String cacheRow(String label, long size, String path, Ansi ansi) {
        return String.format("%-24s ", label) + paint(ansi, String.format("%10s", FileNode.human(size)), Style.bold)
                + "  " + paint(ansi, path, Style.faint) + System.lineSeparator();
    }
}
