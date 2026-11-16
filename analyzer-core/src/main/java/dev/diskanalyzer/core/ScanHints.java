package dev.diskanalyzer.core;

/** User-facing hints about a scan, shared by the GUI and the CLI. */
public final class ScanHints {
    private ScanHints() { }

    /** Explains short totals caused by folders the current account could not read, with the fix for each OS. */
    public static String unreadableMessage(long skippedFolders, Os os) {
        String base = skippedFolders + " folder(s) could not be read, so totals are short. ";
        return base + switch (os) {
            case MAC -> "Grant Full Disk Access in System Settings → Privacy & Security → Full Disk Access, then rescan.";
            case WINDOWS -> "Scan a folder your account can open, or start the app as an administrator, then rescan.";
            case LINUX -> "Scan a folder your account can read, or fix those folders' permissions, then rescan.";
        };
    }
}
