package dev.diskanalyzer.ui;

import atlantafx.base.theme.PrimerDark;
import dev.diskanalyzer.core.CacheCatalog;
import dev.diskanalyzer.core.Cleaner;
import dev.diskanalyzer.core.Collector;
import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.ProtectedPaths;
import dev.diskanalyzer.core.ScanProgress;
import dev.diskanalyzer.core.SearchIndex;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.controlsfx.control.BreadCrumbBar;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class App extends Application {
    private Stage stage;
    private final ScanController scan = new ScanController();
    private final Collector collector = new Collector();
    private final TreeTableView<FileNode> table = new TreeTableView<>();
    private final SunburstView sunburst = new SunburstView();
    private final BreadCrumbBar<FileNode> crumbs = new BreadCrumbBar<>();
    private final Label status = new Label("Choose a folder to analyze.");
    private final ProgressBar progress = new ProgressBar();
    private final Button scanBtn = new Button("Scan folder…", Icons.of("mdi2f-folder-search-outline"));
    private final Button cancelBtn = new Button("Cancel", Icons.of("mdi2c-close-circle-outline"));
    private final SearchBox search = new SearchBox();
    private FdaBanner banner;
    private CollectorPane collectorPane;
    private FileNode view;
    private boolean syncing;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        Path home = Path.of(System.getProperty("user.home"));
        collectorPane = new CollectorPane(collector, new Cleaner(new ProtectedPaths(home)), this::rescanLast);
        CachesPane cachesPane = new CachesPane(new CacheCatalog(home), collectorPane::add);
        banner = new FdaBanner(getHostServices());

        progress.setPrefWidth(120);
        progress.setVisible(false);
        cancelBtn.setDisable(true);
        search.setPrefWidth(260);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, scanBtn, cancelBtn, spacer, progress, search);
        bar.setPadding(new Insets(8));

        crumbs.setAutoNavigationEnabled(false);
        crumbs.setOnCrumbAction(e -> {
            FileNode n = e.getSelectedCrumb().getValue();
            if (n != null) select(n);
        });
        crumbs.setPadding(new Insets(0, 8, 4, 8));

        configureTable();

        Tab collectorTab = new Tab("Collector", collectorPane);
        Tab cachesTab = new Tab("Caches", cachesPane);
        collectorTab.setClosable(false);
        cachesTab.setClosable(false);
        TabPane side = new TabPane(collectorTab, cachesTab);
        side.setPrefWidth(280);

        SplitPane split = new SplitPane(table, sunburst, side);
        split.setDividerPositions(0.36, 0.78);

        VBox top = new VBox(bar, crumbs, banner);
        status.setPadding(new Insets(4, 8, 4, 8));
        BorderPane root = new BorderPane(split);
        root.setTop(top);
        root.setBottom(status);

        scanBtn.setOnAction(e -> chooseAndScan());
        cancelBtn.setOnAction(e -> {
            scan.cancel();
            status.setText("Scan cancelled.");
        });

        Consumer<FileNode> hover = n -> {
            if (n != null) {
                status.setText(n.path() + "  —  " + FileNode.human(n.size()) + "  —  " + n.fileCount() + " files");
            }
        };
        sunburst.setOnDrill(this::select);
        sunburst.setOnHover(hover);
        search.setOnPick(this::select);

        scan.runningProperty().addListener((o, was, now) -> {
            scanBtn.setDisable(now);
            cancelBtn.setDisable(!now);
            progress.setVisible(now);
            progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        });
        scan.progressProperty().addListener((o, old, p) -> {
            if (p != null) status.setText("Scanned " + p.items() + " items — " + FileNode.human(p.bytes()) + " — " + p.current());
        });
        scan.errorProperty().addListener((o, old, msg) -> {
            if (msg != null) status.setText("Scan failed: " + msg);
        });
        scan.rootProperty().addListener((o, old, root2) -> onRoot(root2));

        stage.setTitle("Disk Analyzer");
        stage.setScene(new Scene(root, 1280, 760));
        stage.show();
    }

    private Path lastPath;

    private void chooseAndScan() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select folder to analyze");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            lastPath = dir.toPath();
            scan.start(lastPath);
        }
    }

    private void rescanLast() {
        if (lastPath != null) scan.start(lastPath);
    }

    private void onRoot(FileNode root) {
        if (root == null) {
            view = null;
            table.setRoot(null);
            sunburst.setCurrent(null);
            crumbs.setSelectedCrumb(null);
            search.setIndex(null);
            banner.update(0);
            return;
        }
        boolean scanning = scan.runningProperty().get();
        if (scanning && view != null && view.parent() != null) {
            return; // user drilled down mid-scan: don't yank the view back to the root on every partial
        }
        FileTreeItem rootItem = new FileTreeItem(root);
        rootItem.setExpanded(true);
        table.setRoot(rootItem);
        show(root);
        banner.update(root.skippedDirs());
        if (!scan.runningProperty().get()) {
            search.setIndex(SearchIndex.of(root));
            status.setText(root.fileCount() + " files, " + FileNode.human(root.size()) + " in " + root.path());
        }
    }

    private void configureTable() {
        TreeTableColumn<FileNode, String> name = new TreeTableColumn<>("Name");
        name.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getValue().name()));
        name.setPrefWidth(220);

        TreeTableColumn<FileNode, String> size = new TreeTableColumn<>("Size");
        size.setCellValueFactory(c -> new ReadOnlyStringWrapper(FileNode.human(c.getValue().getValue().size())));
        size.setPrefWidth(90);
        size.setStyle("-fx-alignment: CENTER-RIGHT;");

        TreeTableColumn<FileNode, String> pct = new TreeTableColumn<>("%");
        pct.setCellValueFactory(c -> {
            FileNode n = c.getValue().getValue();
            FileNode p = n.parent();
            String s = (p == null || p.size() == 0) ? "100%" : String.format("%.1f%%", 100.0 * n.size() / p.size());
            return new ReadOnlyStringWrapper(s);
        });
        pct.setPrefWidth(60);
        pct.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.getColumns().addAll(List.of(name, size, pct));
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (syncing || item == null) return;
            FileNode n = item.getValue();
            show(n.isDir() ? n : n.parent());
        });

        MenuItem add = new MenuItem("Add to Collector");
        add.setOnAction(e -> {
            TreeItem<FileNode> sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue().parent() != null) collectorPane.add(sel.getValue());
        });
        table.setContextMenu(new ContextMenu(add));

        table.setOnDragDetected(e -> {
            javafx.scene.Node hit = e.getPickResult().getIntersectedNode();
            while (hit != null && !(hit instanceof javafx.scene.control.TreeTableRow)) hit = hit.getParent();
            if (!(hit instanceof javafx.scene.control.TreeTableRow<?> row) || row.isEmpty()
                    || !(row.getItem() instanceof FileNode dragged) || dragged.parent() == null) return;
            DragState.node = dragged;
            ClipboardContent cc = new ClipboardContent();
            cc.putString(dragged.path().toString());
            Dragboard db = table.startDragAndDrop(TransferMode.COPY);
            db.setContent(cc);
            e.consume();
        });
        table.setOnDragDone(e -> DragState.node = null);
    }

    private void show(FileNode node) {
        view = node;
        sunburst.setCurrent(node);
        List<FileNode> chain = new ArrayList<>();
        for (FileNode n = node; n != null; n = n.parent()) chain.add(n);
        Collections.reverse(chain);
        TreeItem<FileNode> crumb = null;
        for (FileNode n : chain) {
            TreeItem<FileNode> next = new TreeItem<>(n);
            if (crumb != null) crumb.getChildren().add(next);
            crumb = next;
        }
        crumbs.setSelectedCrumb(crumb);
    }

    /** Select node in the table (expanding ancestors) and show its folder in the sunburst. */
    private void select(FileNode node) {
        if (table.getRoot() == null) return;
        List<FileNode> chain = new ArrayList<>();
        for (FileNode n = node; n != null; n = n.parent()) chain.add(n);
        Collections.reverse(chain);

        TreeItem<FileNode> item = table.getRoot();
        for (int i = 1; i < chain.size(); i++) {
            item.setExpanded(true);
            TreeItem<FileNode> next = null;
            for (TreeItem<FileNode> c : item.getChildren()) {
                if (c.getValue() == chain.get(i)) {
                    next = c;
                    break;
                }
            }
            if (next == null) return;
            item = next;
        }
        syncing = true;
        try {
            table.getSelectionModel().select(item);
            table.scrollTo(table.getRow(item));
        } finally {
            syncing = false;
        }
        show(node.isDir() ? node : node.parent());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
