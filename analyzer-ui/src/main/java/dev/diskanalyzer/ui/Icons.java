package dev.diskanalyzer.ui;

import javafx.scene.text.Font;
import org.kordamp.ikonli.javafx.FontIcon;

final class Icons {
    private Icons() { }

    /** Never throws: an unknown icon literal yields an empty icon instead of breaking the UI. */
    static FontIcon of(String literal) {
        FontIcon icon;
        try {
            icon = new FontIcon(literal);
        } catch (RuntimeException e) {
            icon = new FontIcon();
        }
        icon.setIconSize(sizeFor(Font.getDefault().getSize()));
        return icon;
    }

    /** Large dialog icon that scales with the system font; null (keep the stock icon) if the literal is unknown. */
    static FontIcon dialog(String literal) {
        try {
            FontIcon icon = new FontIcon(literal);
            if (icon.getIconCode() == null) return null;
            icon.setIconSize(sizeFor(Font.getDefault().getSize()) * 3);
            return icon;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Icons track the system font size (16px at the usual 12px font) so they scale with text. */
    static int sizeFor(double defaultFontSize) {
        return Math.max(12, (int) Math.round(defaultFontSize * 4.0 / 3.0));
    }
}
