package dev.diskanalyzer.ui;

import org.kordamp.ikonli.javafx.FontIcon;

final class Icons {
    private Icons() { }

    /** Never throws: an unknown icon literal yields an empty icon instead of breaking the UI. */
    static FontIcon of(String literal) {
        try {
            return new FontIcon(literal);
        } catch (RuntimeException e) {
            return new FontIcon();
        }
    }
}
