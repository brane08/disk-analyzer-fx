package dev.diskanalyzer.ui;

import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.SearchIndex;
import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.function.Consumer;

/** Search-as-you-type field with a results popup. */
public final class SearchBox extends TextField {
    private static final int LIMIT = 50;

    private final ListView<FileNode> results = new ListView<>();
    private final Popup popup = new Popup();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(150));
    private SearchIndex index;
    private Consumer<FileNode> onPick = n -> { };

    public SearchBox() {
        setPromptText("Search by name");
        results.setPrefSize(460, 300);
        results.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FileNode n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null
                        : n.name() + "  —  " + FileNode.human(n.size()) + "\n" + n.path());
            }
        });
        popup.setAutoHide(true);
        popup.getContent().add(results);

        debounce.setOnFinished(e -> runSearch());
        textProperty().addListener((obs, old, text) -> debounce.playFromStart());
        results.setOnMouseClicked(e -> pick());
        results.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) pick();
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) popup.hide();
            if (e.getCode() == KeyCode.DOWN && popup.isShowing()) results.requestFocus();
        });
    }

    public void setIndex(SearchIndex index) {
        this.index = index;
        popup.hide();
        runSearch();
    }

    public void setOnPick(Consumer<FileNode> onPick) {
        this.onPick = onPick;
    }

    private void runSearch() {
        if (index == null || getText().isBlank() || getScene() == null) {
            popup.hide();
            return;
        }
        var hits = index.search(getText(), LIMIT);
        results.getItems().setAll(hits);
        if (hits.isEmpty()) {
            popup.hide();
            return;
        }
        if (!popup.isShowing()) {
            Bounds b = localToScreen(getBoundsInLocal());
            popup.show(getScene().getWindow(), b.getMinX(), b.getMaxY());
        }
    }

    private void pick() {
        FileNode n = results.getSelectionModel().getSelectedItem();
        if (n != null) {
            popup.hide();
            onPick.accept(n);
        }
    }
}
