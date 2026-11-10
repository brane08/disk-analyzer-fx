package dev.diskanalyzer.ui;

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** macOS hint shown when some folders could not be read. */
public final class FdaBanner extends HBox {
    private static final String SETTINGS_URL =
            "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles";

    private final Label text = new Label();

    public FdaBanner(HostServices host) {
        setSpacing(8);
        setPadding(new Insets(6, 10, 6, 10));
        setStyle("-fx-background-color: #5c4a00;");
        text.setStyle("-fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button open = new Button("Open Privacy Settings");
        open.setOnAction(e -> host.showDocument(SETTINGS_URL));
        getChildren().addAll(Icons.of("mdi2a-alert-outline"), text, spacer, open);
        update(0);
    }

    /** Shows the banner on macOS when {@code skippedDirs > 0}. */
    public void update(long skippedDirs) {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        boolean show = mac && skippedDirs > 0;
        text.setText(skippedDirs + " folder(s) could not be read, so totals are short. Grant Full Disk Access in "
                + "System Settings → Privacy & Security → Full Disk Access, then rescan.");
        setVisible(show);
        setManaged(show);
    }

    static boolean shouldShow(String osName, long skippedDirs) {
        return osName.toLowerCase().contains("mac") && skippedDirs > 0;
    }
}
