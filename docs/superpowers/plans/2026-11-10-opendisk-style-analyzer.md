# OpenDisk-style Disk Analyzer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the JavaFX analyzer into a multi-module Maven project and grow it to the OpenDisk feature set (sunburst + list, live scan, collector with Trash/permanent delete, filename search, developer-cache grouping, Full Disk Access hint).

**Architecture:** `analyzer-core` is plain Java (no JavaFX): scanning, search, collector/cleanup, cache catalog; fully unit-testable headless. `analyzer-ui` is the JavaFX app depending on core; it adapts core callbacks onto the FX thread. Sunburst stays a custom Canvas (no mainstream JavaFX lib offers one); everything else uses libraries.

**Tech Stack:** Java 25, Maven multi-module, JavaFX 25.0.1, AtlantaFX 2.0.1 (theme), ControlsFX 11.2.2 (BreadCrumbBar), Ikonli 12.3.1 (icons, `ikonli-javafx` + `ikonli-materialdesign2-pack`), JUnit 5.11.4.

**Spec:** user message "something like https://opendisk.app/en-gb ... any mainstream well maintained javafx library ... ui app should be a module, multi module project" + OpenDisk feature list (sunburst beside Finder-style list; hover sizes, click drills down, live scan results; Collector with reclaimable-space preview, Trash or permanent delete, protected system/home/Library folders; filename search as-you-type; developer caches grouped; Full Disk Access prompt; no telemetry/network).

## Global Constraints

- Modules: parent `disk-analyzer-fx` (packaging pom), `analyzer-core`, `analyzer-ui`. Package root `dev.diskanalyzer` (`.core`, `.ui`).
- `analyzer-core` MUST NOT depend on any `org.openjfx` artifact.
- Tests run headless: core tests need no toolkit; no UI toolkit tests in this plan.
- Never follow symlinks while scanning; never delete outside the user-confirmed collector set.
- No network access, no telemetry.
- Paths in commands: run Maven from repo root with `-pl <module> -am`.

## Review Focus

- Symlink cycle / symlink to a directory: not followed, counted as a file of link size, scan terminates.
- Unreadable directory (permission denied): scan continues, `skippedDirs` incremented, no exception.
- File vanishes mid-scan: silently skipped.
- Scan root is a regular file or nonexistent: result is a single-file node / `IllegalArgumentException`, no crash.
- Cancel mid-scan: returns promptly, `scan` yields `Optional.empty()`.
- Collector holding a parent and its own child: reclaimable size counts the bytes once; delete does not fail on the already-removed child.
- Deleting a protected path (`/`, `/System`, home dir, `~/Library`): refused with a result entry, nothing touched.
- Search with regex metacharacters, empty string, and unicode: literal match, empty returns empty list.
- Name with `.` only at start (`.gitignore`) or no extension: `extension()` returns `""`.

---

### Task 1: Multi-module restructure with UI-free core

**Files:**
- Create: `analyzer-core/pom.xml`, `analyzer-ui/pom.xml`
- Modify: `pom.xml` (becomes parent: `<packaging>pom</packaging>`, `<modules>`, `dependencyManagement` for JavaFX/AtlantaFX/ControlsFX/Ikonli/JUnit, shared compiler `release` 25)
- Move (`git mv`): `FileNode.java` → `analyzer-core/src/main/java/dev/diskanalyzer/core/`; `DiskScanner.java` → same dir (rewritten); `App.java`, `SunburstView.java`, `TreemapView.java`, `FileTreeItem.java` → `analyzer-ui/src/main/java/dev/diskanalyzer/ui/`
- Test: `analyzer-core/src/test/java/dev/diskanalyzer/core/DiskScannerTest.java`

**Interfaces:**
- Produces (core):
  - `record ScanProgress(long items, long bytes, long skippedDirs, Path current)`
  - `interface ScanListener { default void onProgress(ScanProgress p) {} default void onPartial(FileNode partialRoot) {} }`
  - `final class DiskScanner { DiskScanner(Path root, ScanListener listener); Optional<FileNode> scan(); void cancel(); }`
  - `FileNode` unchanged API plus `long skippedDirs()` on the root result.
- Consumes: none.

- [ ] **Step 1: Write failing tests in `DiskScannerTest`** (`@TempDir`): `sumsFileSizesAndCounts` (dirs `a/` with 10-byte file, `b/` with 20-byte file → root size 30, fileCount 2, children sorted desc: b then a); `doesNotFollowSymlinkLoop` (symlink `a/loop` → `..`, scan terminates, `loop` is a non-dir node); `skipsUnreadableDirectory` (chmod 000 subdir, root still returned, `skippedDirs()==1`; skip assertion if running as root); `cancelReturnsEmpty` (call `cancel()` from a listener on the first `onProgress`, expect `Optional.empty()`); `fileRootGivesSingleNode`; `missingRootThrows` (`IllegalArgumentException`).
- [ ] **Step 2: Run** `mvn -q test -pl analyzer-core -am` → expect FAIL (classes not found).
- [ ] **Step 3: Create the three poms and move files** with `git mv`; strip `javafx.concurrent.Task` from the scanner: cancellation via `volatile boolean`, progress via `ScanListener` every 500 items. UI `App` wraps `DiskScanner` in a `javafx.concurrent.Task` and forwards listener calls with `Platform.runLater`. `analyzer-ui` pom uses `javafx-maven-plugin` `mainClass=dev.diskanalyzer.ui.App`.
- [ ] **Step 4: Run** `mvn -q test -pl analyzer-core -am` → PASS; `mvn -q compile` (root) → PASS.
- [ ] **Step 5: Commit** `refactor: split into analyzer-core and analyzer-ui modules`.

### Task 2: Progressive, parallel scan

**Files:**
- Modify: `analyzer-core/.../DiskScanner.java`
- Test: `DiskScannerTest.java`

**Interfaces:**
- Consumes: `ScanListener.onPartial(FileNode)` from Task 1.
- Produces: `onPartial` fires after each top-level child of the root completes, with a detached snapshot root (own `FileNode` copies, children sorted, safe to read from another thread). Directory scanning of top-level children runs on virtual threads limited by a `Semaphore(8)`.

- [ ] **Step 1: Test** `partialSnapshotsGrowMonotonically` (3 top-level dirs; collected `onPartial` roots have non-decreasing `size()`, last equals final result size) and `parallelScanMatchesSequentialTotals` (500 generated files, totals equal expected sum).
- [ ] **Step 2: Run** → FAIL. 
- [ ] **Step 3: Implement** parallel top-level fan-out plus snapshot copy (`FileNode.copy()` deep, detached `parent` rewired). Subtree scanning remains single-threaded per top-level child; counters use `AtomicLong`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(core): parallel scan with partial snapshots`.

### Task 3: Filename search

**Files:**
- Create: `analyzer-core/.../SearchIndex.java`
- Test: `SearchIndexTest.java`

**Interfaces:**
- Produces: `final class SearchIndex { static SearchIndex of(FileNode root); List<FileNode> search(String query, int limit); }` — case-insensitive literal substring on `name()`, results ordered by `size()` desc, empty/blank query → empty list.

- [ ] **Step 1: Tests** `matchesCaseInsensitively`, `ordersBySizeDescending`, `respectsLimit`, `emptyQueryReturnsEmpty`, `treatsRegexCharsLiterally` (query `a.c` does not match `abc`), `matchesUnicode` (`é`).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** flat `List<FileNode>` + lowercased name array. **Step 4: Run** → PASS. 
- [ ] **Step 5: Commit** `feat(core): filename search index`.

### Task 4: Collector and guarded cleanup

**Files:**
- Create: `analyzer-core/.../Collector.java`, `ProtectedPaths.java`, `Cleaner.java`
- Test: `CollectorTest.java`, `CleanerTest.java`

**Interfaces:**
- Produces:
  - `final class Collector { void add(FileNode n); void remove(FileNode n); List<FileNode> items(); long reclaimableBytes(); void clear(); }` — adding a descendant of an existing item is ignored; adding an ancestor replaces its descendants; `reclaimableBytes()` never double-counts.
  - `final class ProtectedPaths { ProtectedPaths(Path home); boolean isProtected(Path p); }` — protects `/`, `/System`, `/usr`, `/bin`, `/sbin`, `/etc`, `/Applications`, `/Library`, home dir itself, `home/Library`, and any ancestor of those.
  - `enum Mode { TRASH, PERMANENT }`; `record CleanResult(List<Path> removed, Map<Path,String> failed, long freedBytes)`
  - `final class Cleaner { Cleaner(ProtectedPaths pp, Trasher trasher); CleanResult clean(Collection<FileNode> items, Mode mode); }` with `interface Trasher { boolean moveToTrash(Path p) throws IOException; }` (default impl wraps `java.awt.Desktop.moveToTrash`, injected so tests stay headless).

- [ ] **Step 1: Tests** `ignoresDescendantOfCollectedAncestor`, `ancestorReplacesDescendants`, `reclaimableCountsOnce`, `protectedPathsRefused` (`/`, `$HOME`, `$HOME/Library` → in `failed`, files untouched), `permanentDeletesRecursively` (`@TempDir`), `trashUsesInjectedTrasher` (fake records path, file not deleted), `missingFileReportedAsFailedNotThrown`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** Permanent delete = `Files.walkFileTree` post-order, no symlink following. `freedBytes` sums sizes of removed items only.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(core): collector and guarded cleanup`.

### Task 5: Developer cache catalog

**Files:**
- Create: `analyzer-core/.../CacheCatalog.java`
- Test: `CacheCatalogTest.java`

**Interfaces:**
- Produces: `record CacheLocation(String label, Path path)`; `final class CacheCatalog { CacheCatalog(Path home); List<CacheLocation> existing(); }` returning only paths that exist, from: Xcode DerivedData (`Library/Developer/Xcode/DerivedData`), Xcode Archives-less `Library/Developer/CoreSimulator/Caches`, npm (`.npm`), Homebrew (`Library/Caches/Homebrew`), Gradle (`.gradle/caches`), Maven (`.m2/repository`), pip (`Library/Caches/pip`), Cargo (`.cargo/registry`).

- [ ] **Step 1: Test** `returnsOnlyExistingLocations` (temp home with `.npm` and `.m2/repository` created → exactly those two labels `npm`, `Maven`). **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS. **Step 5: Commit** `feat(core): developer cache catalog`.

### Task 6: UI shell — theme, layout, breadcrumbs, list + sunburst

**Files:**
- Modify: `analyzer-ui/.../App.java` (+ pom deps), `SunburstView.java`
- Delete: `TreemapView.java` (OpenDisk has no treemap; YAGNI)
- Create: `ScanController.java` (runs `DiskScanner` in a `Task`, marshals `onProgress`/`onPartial` with `Platform.runLater`, throttled to ≤ 4 UI updates/sec)

**Interfaces:**
- Consumes: Task 1–2 scanner API.
- Produces: `ScanController { void start(Path root); void cancel(); ReadOnlyObjectProperty<FileNode> rootProperty(); ReadOnlyObjectProperty<ScanProgress> progressProperty(); ReadOnlyBooleanProperty runningProperty(); }`

- [ ] **Step 1: Apply** `Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet())`; layout `BorderPane`: top toolbar (Ikonli icons: scan, cancel, search field), `BreadCrumbBar<FileNode>` under it, center `SplitPane` (list left, sunburst right), right collapsible Collector pane (Task 7), bottom status.
- [ ] **Step 2: Wire** sunburst to the root property so partial snapshots redraw live; hover sets status text with size (existing hover callback).
- [ ] **Step 3: Verify** `mvn -q compile` PASS, then manual run `mvn -pl analyzer-ui javafx:run`: scan `~/Downloads`, chart fills in progressively, breadcrumb navigation matches drill-down.
- [ ] **Step 4: Commit** `feat(ui): atlantafx theme, breadcrumbs, live sunburst`.

### Task 7: UI — search, collector, caches, FDA banner

**Files:**
- Create: `analyzer-ui/.../CollectorPane.java`, `SearchBox.java`, `CachesPane.java`, `FdaBanner.java`

**Interfaces:**
- Consumes: `SearchIndex`, `Collector`, `Cleaner`, `CacheCatalog`, `ScanProgress.skippedDirs`.
- Produces: `CollectorPane(Collector, Cleaner)` — drop target for list rows and sunburst arcs (drag source `FileNode.path()`; context menu "Add to Collector" as non-drag fallback); shows count + `FileNode.human(reclaimableBytes())`; buttons "Move to Trash" and "Delete Permanently", the latter behind a confirm `Alert` listing the item count and size; refusal entries from `CleanResult.failed` shown in a result dialog. `SearchBox` shows results in a popup `ListView` (limit 50) as the user types (150 ms debounce); selecting a result drills the views to its parent. `CachesPane` lists `CacheCatalog.existing()` with sizes (each scanned via `DiskScanner`) and an "Add all to Collector" button. `FdaBanner` shows when `skippedDirs > 0` on macOS with text pointing to System Settings → Privacy & Security → Full Disk Access.

- [ ] **Step 1: Implement** the four components and mount them from `App`.
- [ ] **Step 2: Verify** manually: add a temp folder to the collector → reclaimable size matches; "Move to Trash" removes it and it appears in `~/.Trash`; attempt to add `~/Library` and delete → refused dialog; search `pom` finds files; scanning `/` shows the FDA banner without Full Disk Access.
- [ ] **Step 3: Commit** `feat(ui): search, collector, caches, FDA banner`.

### Task 8: Housekeeping

**Files:**
- Create: `.gitignore` (`target/`, `.idea/`, `*.iml`)
- Modify: `.claude/memory/testing.md` (note core is the headless-tested module)

- [ ] **Step 1: Verify** `mvn -q test` (root) → PASS across modules. 
- [ ] **Step 2: Commit** `chore: gitignore, notes`.

---

## Self-Review

- **Spec coverage:** sunburst+list (T6), hover/drill/live (T2, T6), collector/Trash/permanent/protected (T4, T7), search (T3, T7), dev caches (T5, T7), FDA (T1 skippedDirs, T7), multi-module + UI module (T1), library use (T6: AtlantaFX/ControlsFX/Ikonli). Incremental rescans from OpenDisk are intentionally out of scope (needs FSEvents; not feasible portably in JavaFX).
- **Out of scope:** auto-update, Sparkle, `getattrlistbulk` native scanning, jpackage distribution.
- **Types:** `ScanListener`, `ScanProgress`, `Collector`, `Cleaner`, `Mode`, `CleanResult` names consistent across tasks.
