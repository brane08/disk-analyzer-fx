# Deletion-Safety Fix Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the critical and important deletion-safety review findings (C1, C2, I1–I4) so scan/delete never traverse junctions, protected-path tests cannot touch the real filesystem, OS protect lists cover dangerous containers, permanent delete continues past per-file failures, and Linux trash either works or is disabled.

**Architecture:** Keep all safety logic in `analyzer-core` (headless, unit-tested). `DiskScanner` and `Cleaner` share the same leaf rule for non-followable directory-like entries (`isSymbolicLink` / `isOther`). `ProtectedPaths` gains real-path home registration and expanded exact/tree lists per `Os`. `Cleaner` gains continue-on-error permanent delete and a detectable trash capability; `CollectorPane` disables Trash when unavailable. No new modules.

**Tech Stack:** Java 25, JUnit 5.11.4, existing `analyzer-core` / `analyzer-ui` (no new deps). Linux trash probe: `gio trash` via `ProcessBuilder` when `Desktop.Action.MOVE_TO_TRASH` is unsupported.

**Spec:** `.claude/memory/review-findings.md` (Critical C1–C2, Important I1–I4). Prior product plan: `docs/superpowers/plans/2026-11-10-opendisk-style-analyzer.md`.

## Global Constraints

- `analyzer-core` MUST NOT depend on any `org.openjfx` artifact.
- Never follow symlinks or Windows junctions while scanning or deleting.
- Never delete a path `ProtectedPaths.isProtected` returns true for.
- Tests stay headless; no real `/`, `/System`, or host home as delete targets.
- Paths in commands: run Maven from repo root with `-pl <module> -am`.
- TDD: failing test first for every behavior change.

## Review Focus

- Junction under a scanned tree: treated as a non-dir leaf (link/other size), never entered; permanent delete removes the junction node only, not the target tree.
- Protected refuse reason is literally `"protected path"` and refused temp files still exist after `Mode.PERMANENT`.
- Linux home under `/var/home/me` (real path) is protected as home; `/run/media/user/disk` content remains deletable (not swallowed by a `/run` tree).
- One read-only file inside a permanent-delete tree: siblings still deleted, failure reported, `freedBytes` reflects only successfully removed nodes.
- Host without `MOVE_TO_TRASH`: `Cleaner.trashAvailable()` is false; UI Trash button stays disabled even when collector is non-empty.

---

### Task 1: C2 — Safe protected-path Cleaner test

**Files:**
- Modify: `analyzer-core/src/test/java/dev/diskanalyzer/core/CleanerTest.java`
- (No production change required if existing `ProtectedPaths(Path, Os)` already refuses the temp fixtures.)

**Interfaces:**
- Consumes: `ProtectedPaths(Path home, Os os)`, `Cleaner.clean`, `Mode.PERMANENT`
- Produces: replacement for `protectedPathsAreRefused` that never targets real `/` or `/System`

- [ ] **Step 1: Rewrite failing/stronger test** `protectedPathsAreRefused(@TempDir Path tmp)`

Build a fake Mac-style layout under `tmp`:
- `home = tmp.resolve("Users/tester")` (create dirs)
- `library = home.resolve("Library")` (create)
- `system = tmp.resolve("System")` with a file `system.resolve("keep")` of 1 byte
- `rootMarker = tmp.resolve("rootish")` with a file inside (stand-in only if needed)

Construct `ProtectedPaths pp = new ProtectedPaths(home, Os.MAC)` and `Cleaner` with a recording trasher.

Clean `Mode.PERMANENT` on nodes for: `home`, `library`, and a path that `pp` treats as a system tree if expressible under tmp **or** assert the four conceptual cases using paths that `isProtected` returns true for under this fixture: `home`, `home.resolve("Library")`, and two paths added via existing Mac rules that resolve under the default FS only when `assumeTrue` POSIX — prefer asserting against `home`, `library`, plus `Path.of("/")` and `Path.of("/System")` **only with `Mode.TRASH` and a trasher that throws if called** (never `PERMANENT` on real system paths).

Required assertions:
- `r.failed().get(path)` equals `"protected path"` for each refused item
- trasher list empty
- `system/keep` still exists if any temp system fixture was used
- `r.removed()` empty, `freedBytes == 0`

Remove the old test body that ran `PERMANENT`-capable cleaner logic against real `/`/`/System` without asserting reason strings beyond `failed.size()==4` under TRASH — keep TRASH-only for absolute system paths, PERMANENT only for temp home/Library fixtures.

- [ ] **Step 2: Run** `mvn test -pl analyzer-core -am -Dtest=CleanerTest#protectedPathsAreRefused`

Expected: FAIL or already PASS if production already refuses; if PASS immediately, the test is still valuable as a regression pin — do not weaken assertions. If FAIL because Mac `ProtectedPaths` does not protect temp `home/Library` correctly, that feeds Task 3.

- [ ] **Step 3: Only if Step 2 failed for a production reason, minimal fix in `ProtectedPaths` / `Cleaner`** so reason is `"protected path"` and temp home/Library refuse works. Otherwise no code change.

- [ ] **Step 4: Re-run** same command → PASS; also `mvn test -pl analyzer-core -am -Dtest=CleanerTest` → PASS.

- [ ] **Step 5: Commit** `test(core): pin protected-path refuse without touching real system roots`

---

### Task 2: C1 — Treat junctions / `isOther` dirs as leaves

**Files:**
- Modify: `analyzer-core/src/main/java/dev/diskanalyzer/core/DiskScanner.java`
- Modify: `analyzer-core/src/main/java/dev/diskanalyzer/core/Cleaner.java`
- Modify: `analyzer-core/src/test/java/dev/diskanalyzer/core/DiskScannerTest.java`
- Modify: `analyzer-core/src/test/java/dev/diskanalyzer/core/CleanerTest.java`

**Interfaces:**
- Consumes: `BasicFileAttributes` with `LinkOption.NOFOLLOW_LINKS`
- Produces: shared rule — if `attrs.isSymbolicLink() || attrs.isOther()`, emit/delete as a non-directory leaf (do not `newDirectoryStream` / do not walk into)

- [ ] **Step 1: Failing tests**

`DiskScannerTest.otherDirectoryLikeEntryIsLeafNotTraversed(@TempDir Path tmp)`:
- Create `target/file` (4 bytes) and an entry the platform can make that attributes report as non-ordinary directory without being a normal dir follow — on Linux use a symlink to `target` (already covered) **plus** a test helper that, when `Files.isSymbolicLink` is the only portable stand-in, asserts the same leaf rule via a package-visible or duplicated condition.
- Portable approach: add `DiskScannerTest.symlinkDirectoryIsLeaf` if missing strengthening, and `CleanerTest.doesNotWalkIntoSymlinkDirectory(@TempDir Path tmp)`: directory symlink `link -> target` with files under target; permanent-delete the link node; assert target files still exist; link gone.

Also add `CleanerTest.continuesPastVisitFailures` only in Task 4 — not here.

For true Windows junctions: guard with `Assumptions.assumeTrue` on a junction create attempt (`Files.createSymbolicLink` may create directory junction on Windows); if unsupported, skip. Assert scan of parent lists junction as `!isDir()` and target file size not double-counted.

- [ ] **Step 2: Run** `mvn test -pl analyzer-core -am -Dtest=DiskScannerTest,CleanerTest` → expect FAIL on new leaf-traversal assertions if `walkFileTree` still follows dir symlinks / scanner still enters dir symlinks that `isDirectory()` reports true without NOFOLLOW nuance.

Note: current scanner already uses `NOFOLLOW_LINKS` and treats non-`isDirectory()` as files; dir symlinks often have `isDirectory()==false` with NOFOLLOW. Focus production fix on:
1. `attrs.isOther()` → leaf in `DiskScanner.scan`
2. `Cleaner.deleteRecursively` must use `EnumSet.noneOf(FileVisitOption.class)` (no FOLLOW_LINKS) and in `preVisitDirectory` / `visitFile`, skip descending into `isSymbolicLink|isOther` by deleting the entry as a file and `SKIP_SUBTREE` where needed

- [ ] **Step 3: Implement** leaf rule in `DiskScanner` after reading attrs; rewrite `deleteRecursively` to never follow links and to delete symlink/other dir entries with `Files.delete` without walking targets.

- [ ] **Step 4: Run** same tests → PASS.

- [ ] **Step 5: Commit** `fix(core): never traverse junctions or symlink dirs on scan/delete`

---

### Task 3: I1–I2 — Protected path coverage

**Files:**
- Modify: `analyzer-core/src/main/java/dev/diskanalyzer/core/ProtectedPaths.java`
- Modify: `analyzer-core/src/test/java/dev/diskanalyzer/core/ProtectedPathsTest.java`

**Interfaces:**
- Consumes: `ProtectedPaths(Path home, Os os)`
- Produces: same `boolean isProtected(Path)`; expanded exact/trees; home also registered via `toRealPath()` when resolvable

**Policy (builder call):**
- Register `home.toAbsolutePath().normalize()` and, if `toRealPath()` succeeds, that path + its ancestors in `exact`.
- **MAC exact adds:** `/Volumes`, `/private` (exact — contents under `/private/tmp` stay free unless other rules hit); home `Library/Application Support`, `Library/Keychains`, `Library/Mail`, `Library/Messages`, `Library/Containers`.
- **LINUX:** keep `/mnt`, `/media` as **exact only** (not trees). Change `/run` from tree to exact (`/run` itself protected, `/run/media/...` content not). Keep `/var` as tree **except** do not let it block a home whose real path is under `/var/home/...` — home real path in `exact` already protects home itself; user content under `/var/home/me/Downloads` must remain unprotected. Add exact `/srv`, `/snap`, `/nix`.
- **WINDOWS exact adds:** `AppData/Local/Microsoft`, `AppData/Roaming/Microsoft` (home-relative).

- [ ] **Step 1: Failing tests** in `ProtectedPathsTest`:
  - `realHomePathIsProtected` — `@TempDir`, create `linkHome -> realHome`, `new ProtectedPaths(linkHome, Os.LINUX)`, assert `isProtected(realHome)`.
  - `linuxRunMediaContentIsNotProtected` — `assertFalse` on `/run/media/user/disk/file` with `Os.LINUX`.
  - `macSensitiveLibraryFoldersAreProtected` — Application Support / Keychains / Mail under home.
  - `linuxSrvSnapNixAreProtected` — exact paths.
  - `windowsMicrosoftAppDataIsProtected` — under fake home with `Os.WINDOWS`.

- [ ] **Step 2: Run** `mvn test -pl analyzer-core -am -Dtest=ProtectedPathsTest` → FAIL.

- [ ] **Step 3: Implement** list changes in `ProtectedPaths` constructor; adjust existing `linuxSystemTreesAreProtected` expectations if `/run` / `/var` semantics change (update that test in the same task).

- [ ] **Step 4: Run** ProtectedPathsTest + CleanerTest → PASS.

- [ ] **Step 5: Commit** `fix(core): broaden protected paths and real-path home`

---

### Task 4: I3 — Permanent delete continues after per-entry failures

**Files:**
- Modify: `analyzer-core/src/main/java/dev/diskanalyzer/core/Cleaner.java`
- Modify: `analyzer-core/src/test/java/dev/diskanalyzer/core/CleanerTest.java`

**Interfaces:**
- Consumes: `clean(..., Mode.PERMANENT)`
- Produces: `CleanResult` where partial success is allowed; failed paths map to reason; `freedBytes` sums sizes of fully removed top-level candidates only when the top-level path is gone, or sum of successfully deleted file sizes if you track them — **pin:** top-level candidate that fully disappears counts `n.size()`; top-level that remains (partial) counts `0` for that candidate and lists failed child paths.

- [ ] **Step 1: Failing test** `permanentDeleteContinuesAfterReadOnlyFile(@TempDir Path tmp)`

Layout: `victim/ok` (writable 5 bytes), `victim/ro` (5 bytes then POSIX `r--r--r--` or Windows dos read-only). Scan `victim`. `Mode.PERMANENT`.

Assert:
- `victim/ok` deleted
- `victim/ro` still exists OR parent `victim` may remain if non-empty
- `r.failed()` non-empty
- `r.freedBytes() > 0` (at least the deleted sibling)
- Assume-skip if OS ignores read-only bit for the test user

- [ ] **Step 2: Run** → FAIL (today walk aborts on first `IOException`).

- [ ] **Step 3: Implement** `deleteRecursively` collecting failures: `visitFileFailed` / catch delete errors, `CONTINUE`; `postVisitDirectory` tries delete and records failure without aborting the whole clean of other top-level candidates. If top-level path still exists after walk, put it in `failed` and do not add its `n.size()` to `freed`.

- [ ] **Step 4: Run** CleanerTest → PASS.

- [ ] **Step 5: Commit** `fix(core): continue permanent delete after per-file failures`

---

### Task 5: I4 — Linux trash availability

**Files:**
- Modify: `analyzer-core/src/main/java/dev/diskanalyzer/core/Cleaner.java`
- Modify: `analyzer-core/src/test/java/dev/diskanalyzer/core/CleanerTest.java`
- Modify: `analyzer-ui/src/main/java/dev/diskanalyzer/ui/CollectorPane.java`

**Interfaces:**
- Produces:
  - `static boolean trashAvailable()` — true if `Desktop` supports `MOVE_TO_TRASH`, else true if `gio` executable exists on `PATH` (probe once, cached).
  - Default trasher: try Desktop; if unsupported, run `gio trash -- <path>` (exit 0 = success); else return false.
  - `CollectorPane.refresh`: `trashBtn.setDisable(empty || !Cleaner.trashAvailable())`; when disabled for availability, tooltip or summary note `"Trash unavailable on this system"` is enough (no new i18n framework).

- [ ] **Step 1: Failing tests**
  - `CleanerTest.trashAvailableReflectsCapability` — assert `trashAvailable()` is boolean stable across two calls (cache); cannot assert true/false portably — assert idempotence and that when a custom trasher is injected, clean TRASH still uses injection (existing tests).
  - Add package-visible or test-facing `Cleaner.gioTrasherForTest(ProcessRunner)` only if needed; prefer testing default trasher by verifying `Mode.TRASH` with default constructor on a temp file **assumes** `trashAvailable()` — if true, file leaves the original path; if false, expect `failed` reason `trash unavailable`.

  UI: no automated test (plan constraint); manual verify step.

- [ ] **Step 2: Run** core tests → FAIL until `trashAvailable` exists.

- [ ] **Step 3: Implement** `trashAvailable`, gio fallback in `desktopTrash` (rename to `defaultTrash`), wire `CollectorPane`.

- [ ] **Step 4: Run** `mvn test -pl analyzer-core -am` and `mvn -q compile -pl analyzer-ui -am` → PASS.

- [ ] **Step 5: Commit** `fix: gio trash fallback and disable Trash when unavailable`

---

### Task 6: Verify suite

**Files:** none new

- [ ] **Step 1: Run** `mvn test` from repo root → PASS (core + cli).
- [ ] **Step 2: Run** `mvn -q compile` → PASS.
- [ ] **Step 3: Commit** only if Task 6 produced doc/memory updates; otherwise skip empty commit.
- [ ] **Step 4: Update** `.claude/memory/review-findings.md` — mark C1, C2, I1–I4 as fixed with one-line notes.

---

## Self-Review

- **Spec coverage:** C1→T2, C2→T1, I1+I2→T3, I3→T4, I4→T5; T6 verification.
- **Step scan:** each step is one test, one run, one implement, one verify, or one commit.
- **Types:** `CleanResult`, `Mode`, `ProtectedPaths`, `Os` unchanged externally except additive `Cleaner.trashAvailable()`.
- **Review Focus:** each line mapped to a named test in T2–T5.
- **Out of scope:** I5–I12, sunburst drag, `top --files`, packaging, CI matrix.
