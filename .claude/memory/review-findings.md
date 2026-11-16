# Review findings

Status: fix pass on branch `fix/review-critical` (local, unpushed). FIXED items are tagged below; the rest are still open.

## Critical
- FIXED C1 (helper tested with a symlink only, not a real junction) Windows junctions traversed by delete and scan (`Cleaner.java:89-103`, `DiskScanner.java:119-130`): treat `isOther()` dirs as leaves.
- FIXED C2 `CleanerTest.protectedPathsAreRefused` (`:20,35-47`) runs PERMANENT on real `/`, `/System`, `/Users/tester`; only code under test stops it. Passes via "not found" on Linux/Windows; asserts only `failed.size()==4`. Fix: `@TempDir` + explicit `Os`, assert reason "protected path" and files still exist.

## Important
- I1 (home itself is protected now; real-pathed and /var/home cases not rechecked) Real-pathed home not in `ProtectedPaths.exact`; Linux `/var`, `/run` trees swallow `/var/home/me`, `/run/media/*`.
- FIXED I2 (mac /private /Volumes /Users + ~/Library subdirs incl. Keychains, Mail; linux /srv /snap /nix + existing /mnt and /media mount points; Windows AppData\Local\Microsoft, LocalLow) Unprotected containers: macOS `/private`, `/Volumes`, `~/Library/{Application Support,Keychains,Mail,...}`; Windows `AppData\Roaming`, `AppData\Local\Microsoft`; Linux `/mnt/*`, `/media/*`, `/srv`, `/snap`, `/nix`.
- FIXED I3 Permanent delete aborts on first failure (read-only files); reports "Freed 0 B".
- FIXED I4 (freedesktop fallback in Cleaner.xdgTrash) Trash is a no-op on Linux (no `MOVE_TO_TRASH`): use `gio trash` or disable button.
- FIXED I5 Windows `Temp` offered as wholesale-deletable cache.
- PARTIAL I6 (Windows-guarded tests + `.github/workflows/ci.yml` matrix added; never run on Windows/macOS, first CI run is the real check) Core tests fail on Windows (Mac-path tests, symlink and POSIX-perm tests unguarded); no Windows-branch tests; needs CI matrix. `emptyHomeHasNoCaches` uses host OS.
- FIXED I7 (delete off the FX thread; /proc,/sys,/dev,/run and macOS /System/Volumes skipped via ScanExclusions; logical sizes of sparse files still over-count) Whole-disk scan sizes wrong (`/proc/kcore`, firmlinks, logical sizes); delete runs on FX thread.
- FIXED I8 (snapshots throttled to 250 ms, last child always emits) `DiskScanner.java:100-105` deep-copies whole tree per top-level child under lock (GC/OOM on big homes).
- FIXED I9 Symlinked scan root treated as a file (`DiskScanner.java:48-50`, `BaseCommand.java:40`): `toRealPath()` root once.
- PARTIAL I10 (partials skipped while a row is selected mid-scan; untested, expansion/scroll reset otherwise still possible) `App.java:166-173` partial updates reset table selection/expansion/scroll every 250 ms; drilled view freezes then jumps to root.
- FIXED I11 (Trash confirm; collector retargeted to fresh nodes after each completed scan; UI part not run in the GUI) `CollectorPane.java:104-116` Trash has no confirm; Collector keeps stale FileNodes/sizes across rescans.
- FIXED I12 (npm -> .npm/_cacache, cargo -> registry/cache; Maven stays whole repo, relabelled) `CacheCatalog` offered whole `~/.m2/repository` (local installs not re-downloadable), `~/.npm`, `~/.cargo/registry`; "Add all" + Trash = 2 clicks.

## Plan gaps that matter
- FIXED (compiles, never exercised in the GUI) sunburst arcs are a drag source and have a right-click "Add to Collector".
- FIXED `top --files` (flag added; rejects `--files` with `--dirs`).

## Minor
- FIXED `Os.parse("darwin")` -> WINDOWS ("win" check first).
- FIXED negative `top -n` (exit 2) and search limit (empty result); still open: `caches --home` on missing path exits 0.
- `█░` shows `?` on non-UTF-8 Windows consoles.
- No runnable UI artifact (no Launcher main; needs `mvn install` first).
- No unreadable-folder hint on Linux/Windows; `FdaBanner.shouldShow` dead code.
- `Cleaner` freed bytes use stale sizes, count Trash as freed; child skipped under failed ancestor is unreported (`:58,:74`).
- Cache entries stay listed after clean; `SearchIndex.of` recursive on FX thread at scan end.
- STALE `FileNode.human`/`extension` already use `Locale.ROOT`.
- FIXED `ProtectedPaths` case-insensitive on macOS/Windows, case-sensitive on Linux.
- Every clean rescans `lastPath`; fails if it was deleted (`CollectorPane.java:132`, `App.java:152`).
- After cancel partial tree shown as complete, no search index; unreadable root leaves status "Scanned…" (`ScanController.java:89-106`).
- Worker exception lets full scan finish before error surfaces (`DiskScanner.java:76-77`).
- FIXED `Collector.add(null)` and `SearchIndex.search` negative limit; open: `CachesPane` null `getException()`, no cancel for cache scan.
- Test weaknesses: `partialSnapshotsGrowMonotonically` asserts exactly 3 callbacks; `noColorHasNoEscapeCodes` negative-only; `Cleaner(ProtectedPaths)` ctor, `FileNode.copy`, `human`, `Main.main` untested.

## Verified fine
- `SunburstView` `break` on sorted children and `arcAt` need no guard; empty/zero-size/single-file roots render.
- No network code; only `FdaBanner` opens a settings URL. `TreemapView` deleted.
- Not yet done: GUI and CLI jar never launched; Windows/Linux paths unverified; no push/PR.
