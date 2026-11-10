# Testing

- Tests run headless (no display). Keep scanner/layout logic UI-free so it's unit-testable without a Stage.
- Any JavaFX toolkit test needs headless config (e.g. Monocle: `-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw`) set in surefire `argLine`.
- Prefer testing `DiskScanner` (temp dirs), `FileNode.human`, and treemap squarify math over GUI interaction.
