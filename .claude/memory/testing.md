# Testing

- Tests run headless (no display). Keep scanner/layout logic UI-free so it's unit-testable without a Stage.
- Any JavaFX toolkit test needs headless config (e.g. Monocle: `-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw`) set in surefire `argLine`.
- Prefer testing `DiskScanner` (temp dirs), `FileNode.human`, `Cleaner`/`Collector`/`SearchIndex` over GUI interaction.
- Headless-tested modules: `analyzer-core` (32 tests) and `analyzer-cli` (8 tests, `Render` is pure strings). `analyzer-ui` has no tests; it is compile-verified only.
- Run: `bash ~/.claude/skills/mvn-test/test.sh <module> [Class]`; full suite `mvn -q test` from the repo root.
