# Open review findings (recorded, not fixed)

Status: deletion-safety fix pass done on `fix/deletion-safety` (C1, C2, I1–I4). Remaining items below still open.

## Critical
- C1 ~~Windows junctions traversed by delete and scan~~ **fixed**: `DiskScanner.isTraversableDirectory` treats `isSymbolicLink|isOther` as leaves; `Cleaner.deleteRecursively` skips those subtrees.
- C2 ~~`CleanerTest.protectedPathsAreRefused` hits real `/`/`/System`~~ **fixed**: `@TempDir` + `Os.MAC` for PERMANENT home/Library; TRASH-only for absolute system paths with boom trasher.

## Important
- I1 ~~Real-pathed home / `/run` swallow~~ **fixed**: home `toRealPath()` registered; `/run` is exact-only so `/run/media/...` stays deletable.
- I2 ~~Unprotected containers~~ **fixed**: mac `/private`,`/Volumes`, sensitive `~/Library/*`; Linux `/srv`,`/snap`,`/nix`; Windows `AppData/*/Microsoft`. `/mnt`/`/media` remain exact (not trees).
- I3 ~~Permanent delete aborts on first failure~~ **fixed**: continue-on-error walk; partial `freedBytes`; root stays out of `removed` if remnants remain.
- I4 ~~Trash no-op on Linux~~ **fixed**: `Cleaner.trashAvailable()` + `gio trash` fallback; UI disables Trash when unavailable.
- I5 Windows `Temp` offered as wholesale-deletable cache.
- I6 Core tests fail on Windows (Mac-path tests, symlink and POSIX-perm tests unguarded); no Windows-branch tests; needs CI matrix. `emptyHomeHasNoCaches` uses host OS.
- I7 Whole-disk scan sizes wrong (`/proc/kcore`, firmlinks, logical sizes); delete runs on FX thread.
- I8 `DiskScanner.java:100-105` deep-copies whole tree per top-level child under lock (GC/OOM on big homes).
- I9 Symlinked scan root treated as a file (`DiskScanner.java:48-50`, `BaseCommand.java:40`): `toRealPath()` root once.
- I10 `App.java:166-173` partial updates reset table selection/expansion/scroll every 250 ms; drilled view freezes then jumps to root.
- I11 `CollectorPane.java:104-116` Trash has no confirm; Collector keeps stale FileNodes/sizes across rescans.
- I12 `CacheCatalog` offers whole `~/.m2/repository` (local installs not re-downloadable), `~/.npm`, `~/.cargo/registry`; "Add all" + Trash = 2 clicks.

## Plan gaps that matter
- Sunburst arcs not a drag source / no "Add to Collector".
- `top --files` missing (only `--dirs`).

## Minor
- `Os.parse("darwin")` -> WINDOWS ("win" check first).
- Negative `-n`/`--top`/search limit throws; `caches --home` on missing path exits 0.
- `█░` shows `?` on non-UTF-8 Windows consoles.
- No runnable UI artifact (no Launcher main; needs `mvn install` first).
- No unreadable-folder hint on Linux/Windows; `FdaBanner.shouldShow` dead code.
- `Cleaner` freed bytes use stale sizes, count Trash as freed; child skipped under failed ancestor is unreported (`:58,:74`).
- Cache entries stay listed after clean; `SearchIndex.of` recursive on FX thread at scan end.
- `FileNode.human`/`extension` use default locale.
- `ProtectedPaths` case-sensitive on macOS (`:73-83`).
- Every clean rescans `lastPath`; fails if it was deleted (`CollectorPane.java:132`, `App.java:152`).
- After cancel partial tree shown as complete, no search index; unreadable root leaves status "Scanned…" (`ScanController.java:89-106`).
- Worker exception lets full scan finish before error surfaces (`DiskScanner.java:76-77`).
- `Collector.add(null)` stores null; `SearchIndex.search` throws on negative limit; `CachesPane` null `getException()`, no cancel for cache scan.
- Test weaknesses: `partialSnapshotsGrowMonotonically` asserts exactly 3 callbacks; `noColorHasNoEscapeCodes` negative-only; `Cleaner(ProtectedPaths)` ctor, `FileNode.copy`, `human`, `Main.main` untested.

## Verified fine
- `SunburstView` `break` on sorted children and `arcAt` need no guard; empty/zero-size/single-file roots render.
- No network code; only `FdaBanner` opens a settings URL. `TreemapView` deleted.
- Not yet done: GUI and CLI jar never launched; Windows/Linux paths unverified; no push/PR.
