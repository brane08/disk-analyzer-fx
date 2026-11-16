package dev.diskanalyzer.core;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves a user-requested UI scale (e.g. "1.5" or "150%") for JavaFX on Linux, where fractional
 * desktop scaling is not picked up automatically. An explicit {@code glass.gtk.uiScale} always wins.
 * Percentages must be whole numbers; plain factors may have up to two decimals. Zero is rejected.
 */
public final class UiScale {
    private static final Pattern PERCENT = Pattern.compile("\\d{1,3}%");
    private static final Pattern FACTOR = Pattern.compile("\\d{1,2}(\\.\\d{1,2})?");

    private UiScale() { }

    /** The value to set as {@code glass.gtk.uiScale}, or empty to leave JavaFX's own detection alone. */
    public static Optional<String> override(String existingGlassProp, String sysProp, String envVar) {
        if (existingGlassProp != null && !existingGlassProp.isBlank()) return Optional.empty();
        Optional<String> fromProp = valid(sysProp);
        return fromProp.isPresent() ? fromProp : valid(envVar);
    }

    private static Optional<String> valid(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim();
        boolean ok = PERCENT.matcher(v).matches() || FACTOR.matcher(v).matches();
        if (!ok || Double.parseDouble(v.replace("%", "")) <= 0) return Optional.empty();
        return Optional.of(v);
    }
}
