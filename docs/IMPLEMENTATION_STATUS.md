# Implementation status

November 26, 2026 · ChessMaster – Royal Edition · JIEXIANGTECH (杰祥科技)

The existing four-module architecture (`com.jiexiangtech:royal-chess`) and working domain,
SQLite persistence, authentication, and AI engine layers were preserved in place. The desktop
presentation layer now includes both **Classic 2D** and **Royal Focus Mode 2.5D**, matching the
approved Splash/Login baseline and both official visual references.

| Area | State |
| --- | --- |
| Maven and module boundaries | Implemented as four modules. Reactor is `com.jiexiangtech:royal-chess`. Domain has zero Swing/JDBC dependencies |
| Royal Focus Mode 2.5D | Implemented with exact `3×3` projective homography (`PerspectiveProjection`, `PerspectiveCoordinateMapper`), cached inverse-warped 64-square marble board (`PerspectiveTextureRenderer`), physical 3D front slab with metallic gold rails & sculpted crown/laurel emblem, ornate beveled gold frame & corner filigree, depth-sorted 2.5D billboard pieces (`PieceDepthSorter`, `RoyalPieceRenderer`), shadows & clipped reflections (`ShadowRenderer`, `ReflectionRenderer`), cinematic lighting (`CinematicLightingEngine`, `SpotlightRenderer`, `VignetteRenderer`), auto-dimming Left Sidebar & `RoyalBottomToolbar`, collapsible Move History/Captured drawer, and live 2D ↔ 2.5D switching (`FocusModeController`) |
| Classic 2D Gameplay | Preserved 3-column Royal Luxury layout with live toggle into Royal Focus 2.5D |
| Preferences & SQLite | `Preferences.ViewMode` (`CLASSIC_2D`, `ROYAL_FOCUS_25D`) and `Preferences.GraphicsQuality` (`LOW`, `MEDIUM`, `HIGH`, `ULTRA`) persisted cleanly in SQLite without breaking existing schema migrations |
| Royal assets | 12 normalized 512×512 RGBA sprites, 4 marble tiles/board images, `gameplay-background.png`, `royal-focus-background.png`, `company-horizontal.png`, and `focus-mode/` lighting, shadow, frame, reflection, and texture assets |
| Splash and Login | Preserved approved visual baseline intact |
| Home, New Game, and secondary screens | Cohesive Royal Luxury redesign across Home, New Game, Puzzles, Puzzle Play, Game History, Replay, Leaderboard, Statistics, Achievements, Settings (with Display Mode & Graphics Quality), Result, and About dialog |
| Draw adjudication | Common material and sealed-pawn dead positions verified. General mate reachability remains incomplete |
| Persistence | SQLite migration, PBKDF2, per-profile data, atomic snapshots, safe rollback and replay-based restore |
| AI | Four levels, time limits, cancellation, analysis and optional external Stockfish validated with Stockfish 19 |
| Desktop verification | Maven passed all 27 tests (including 64-square 2.5D perspective round-trip & offscreen paint checks); live macOS GUI verification captured `royal-focus-actual.png` and `royal-focus-comparison.png` alongside Classic 2D and secondary screens |
| GitHub | Private repository at `jtuongdang/jiexiangtech-royal-chess` |

## Remaining scope

- Online multiplayer / game server is intentionally deferred outside this stage.
- General dead-position and flag-fall adjudication still lack a complete reachability solver for unusual positions.
- The replay import supports one PGN main line per file and is session-local. The history view filters local results without pagination.
- Four verified puzzles currently cover mate in one. Multi-move puzzles are outside this release.
- Native release signing and notarization require developer signing resources. The checkout provides packaging scripts and local macOS app image support.
- Reference design spec and verified comparison screenshots are tracked in `docs/design/` and `docs/screenshots/`.

[Royal Focus Mode Spec](design/ROYAL_FOCUS_MODE.md) · [Architecture](ARCHITECTURE.md) · [Test report](TEST_REPORT.md) · [Design system](DESIGN_SYSTEM.md) · [Private repository](https://github.com/jtuongdang/jiexiangtech-royal-chess)
