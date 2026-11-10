# Open review findings (recorded, not fixed)

Status: two fable+haiku review rounds done on branch `feature/opendisk-style`. No fix pass authorized yet.
Deletion-safety items first when a fix pass is approved (TDD: failing test first).

## Critical
- C1 Windows junctions traversed by delete and scan (`Cleaner.java:89-103`, `DiskScanner.java:119-130`): treat `isOther()` dirs as leaves.
- C2 `CleanerTest.protectedPathsAreRefused` (`:20,35-47`) runs PERMANENT on real `/`, `/System`, `/Users/tester`; only code under test stops it. Passes via "not found" on Linux/Windows; asserts only `failed.size()==4`. Fix: `@TempDir` + explicit `Os`, assert reason "protected path" and files still exist.

## Important
- I1 Real-pathed home not in `ProtectedPaths.exact`; Linux `/var`, `/run` trees swallow `/var/home/me`, `/run/media/*`.
- I2 Unprotected containers: macOS `/private`, `/Volumes`, `~/Library/{Application Support,Keychains,Mail,...}`; Windows `AppData\Roaming`, `AppData\Local\Microsoft`; Linux `/mnt/*`, `/media/*`, `/srv`, `/snap`, `/nix`.
- I3 Permanent delete aborts on first failure (read-only files); reports "Freed 0 B".
- I4 Trash is a no-op on Linux (no `MOVE_TO_TRASH`): use `gio trash` or disable button.
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
