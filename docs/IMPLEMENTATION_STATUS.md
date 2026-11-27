# Implementation status

November 27, 2026 · ChessMaster – Royal Edition · JIEXIANGTECH (杰祥科技)

The existing desktop architecture (`com.jiexiangtech:royal-chess`) and working domain,
SQLite persistence, authentication, and AI engine layers were preserved in place. The desktop
presentation layer now includes both **Classic 2D** and **Royal Focus Mode 2.5D**, matching the
approved Splash/Login baseline and both official visual references.

| Area | State |
| --- | --- |
| Maven and module boundaries | The desktop and online modules share the `com.jiexiangtech:royal-chess` reactor. Domain has zero Swing/JDBC dependencies |
| Royal Focus Mode 2.5D | Implemented with exact `3×3` projective homography (`PerspectiveProjection`, `PerspectiveCoordinateMapper`), cached inverse-warped 64-square marble board (`PerspectiveTextureRenderer`), physical 3D front slab with metallic gold rails & sculpted crown/laurel emblem, ornate beveled gold frame & corner filigree, depth-sorted 2.5D billboard pieces (`PieceDepthSorter`, `RoyalPieceRenderer`), shadows & clipped reflections (`ShadowRenderer`, `ReflectionRenderer`), cinematic lighting (`CinematicLightingEngine`, `SpotlightRenderer`, `VignetteRenderer`), auto-dimming Left Sidebar & `RoyalBottomToolbar`, collapsible Move History/Captured drawer, and live 2D ↔ 2.5D switching (`FocusModeController`) |
| Classic 2D Gameplay | Classical board rebuilt with the measured Interface-2 bezel, 64 square cells, eight marble samples and 12 redrawn pieces. Live toggle into Royal Focus 2.5D |
| Preferences & SQLite | `Preferences.ViewMode` (`CLASSIC_2D`, `ROYAL_FOCUS_25D`) and `Preferences.GraphicsQuality` (`LOW`, `MEDIUM`, `HIGH`, `ULTRA`) persisted cleanly in SQLite without breaking existing schema migrations |
| Royal assets | 12 redrawn 512×512 RGBA sprites, eight marble samples, a Classic frame texture, two legacy board images, `gameplay-background.png`, `royal-focus-background.png`, `company-horizontal.png`, and `focus-mode/` lighting, shadow, frame, reflection, and texture assets |
| Splash and Login | Preserved approved visual baseline intact |
| Home, New Game, and secondary screens | Cohesive Royal Luxury redesign across Home, New Game, Puzzles, Puzzle Play, Game History, Replay, Leaderboard, Statistics, Achievements, Settings (with Display Mode & Graphics Quality), Result, and About dialog |
| Draw adjudication | Common material and sealed-pawn dead positions verified. General mate reachability remains incomplete |
| Persistence | SQLite migration, PBKDF2, per-profile data, atomic snapshots, safe rollback and replay-based restore |
| AI | Four levels, time limits, cancellation, analysis and optional external Stockfish validated with Stockfish 19 |
| Piece motion | Classic and Focus share time-based easing, drag settling and capture/castling/promotion transitions. The Ultra setting requests an 8 ms repaint interval during moves |
| Royal Arena | Castle backdrop, player portraits and clocks around a VS header, revised perspective, 12 ivory and onyx sprites, and eight marble variants for Classic and Focus. Both modes still use the live match position |
| Avatars | Settings provides six portraits and the Royal emblem. SQLite stores the selection per profile. The updated online server stores the portrait preset ID on each seat and returns it to clients that support portraits |
| Desktop verification | Maven passed all 39 tests, including the MongoDB integration suite. Live macOS Swing captures cover Classic, Royal Arena and the avatar picker |
| Online beta | Live on Render Free in Singapore with MongoDB Atlas Free. Two live matches verified over HTTPS/WebSocket. Desktop v1.2.0 upgrades the Online UX with tabbed Create/Join lobby, collapsible server settings, cold-start feedback, prominent waiting invite banner, visible player names & low-time clock warning, move/result audio cues, and a match-outcome dialog with Back-to-Lobby |
| GitHub | Public repository at `jtuongdang/jiexiangtech-royal-chess` |

## Remaining scope

- Battle Map campaign progression, unlocks and rewards are a separate feature phase.
- The live beta uses a private Docker Hub image. Its Render pull credential expires on February 24, 2027. Automated GitHub Actions publishing still requires a separate write credential. Cloud accounts and rated matchmaking are later phases.
- Atlas project cleanup remains. The earlier unused `chessmaster_render` database user and the IP entry created by Atlas Auto Setup remain in the project. The service uses `royal_beta_render` and the Render Singapore outbound range.
- General dead-position and flag-fall adjudication still lack a complete reachability solver for unusual positions.
- The replay import supports one PGN main line per file and is session-local. The history view filters local results without pagination.
- Four verified puzzles currently cover mate in one. Multi-move puzzles are outside this release.
- Native release signing and notarization require developer signing resources. The checkout provides packaging scripts and local macOS app image support.
- Reference design spec and verified comparison screenshots are tracked in `docs/design/` and `docs/screenshots/`.

[Royal Focus Mode Spec](design/ROYAL_FOCUS_MODE.md) · [Architecture](ARCHITECTURE.md) · [Test report](TEST_REPORT.md) · [Design system](DESIGN_SYSTEM.md) · [GitHub repository](https://github.com/jtuongdang/jiexiangtech-royal-chess)

[Classic board geometry and comparison](design/ROYAL_CLASSIC_BOARD.md)

[Online deployment and local test guide](ONLINE_DEPLOYMENT.md)
