package dev.diskanalyzer.ui;

import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.Os;
import dev.diskanalyzer.core.ScanHints;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Path;
import java.util.List;

/** Shown on every OS when folders could not be read: explains the short totals and lists the skipped folders. */
public final class UnreadableBanner extends HBox {
    private static final String MAC_SETTINGS_URL =
            "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles";
    private static final int LISTED_FOLDERS = 50;

    private final HostServices host;
    private final Os os = Os.detect();
    private final Label text = new Label();
    private final Button action = new Button();
    private FileNode root;

    public UnreadableBanner(HostServices host) {
        this.host = host;
        setSpacing(8);
        setPadding(new Insets(6, 10, 6, 10));
        setStyle("-fx-background-color: #5c4a00;");
        text.setStyle("-fx-text-fill: white;");
        text.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        action.setText(os == Os.MAC ? "Open Privacy Settings" : "Show folders");
        action.setOnAction(e -> act());
        getChildren().addAll(Icons.of("mdi2a-alert-outline"), text, spacer, action);
        update(null);
    }

    /** Shows the banner when {@code root} has folders that could not be read; hides it for a null root. */
    public void update(FileNode root) {
        this.root = root;
        long skipped = root == null ? 0 : root.skippedDirs();
        text.setText(ScanHints.unreadableMessage(skipped, os));
        boolean show = skipped > 0;
        setVisible(show);
        setManaged(show);
    }

    private void act() {
        if (os == Os.MAC) {
            host.showDocument(MAC_SETTINGS_URL);
        } else {
            showFolders();
        }
    }

    private void showFolders() {
        if (root == null) return;
        List<Path> folders = root.unreadableFolders(LISTED_FOLDERS);
        StringBuilder body = new StringBuilder();
        for (Path p : folders) body.append("• ").append(p).append('\n');
        if (root.skippedDirs() > folders.size()) {
            body.append("… and ").append(root.skippedDirs() - folders.size()).append(" more");
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, body.toString().stripTrailing(), ButtonType.OK);
        alert.setHeaderText("Folders that could not be read");
        alert.showAndWait();
    }
}
