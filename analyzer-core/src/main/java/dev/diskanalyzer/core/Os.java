package dev.diskanalyzer.core;

import java.util.Locale;

public enum Os {
    MAC, LINUX, WINDOWS;

    public static Os detect() {
        return parse(System.getProperty("os.name", ""));
    }

    static Os parse(String osName) {
        String n = osName.toLowerCase(Locale.ROOT);
        if (n.contains("win")) return WINDOWS;
        if (n.contains("mac") || n.contains("darwin")) return MAC;
        return LINUX;
    }
}
