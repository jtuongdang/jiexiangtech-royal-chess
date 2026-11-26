# Implementation status

November 26, 2026 · ChessMaster – Royal Edition · JIEXIANGTECH (杰祥科技)

The existing project was kept in place with its four-module architecture (`com.jiexiangtech:royal-chess`)
and working domain, persistence, authentication, and AI layers preserved. The presentation layer has been
upgraded to match the approved Splash and Login Royal Luxury design baseline across all application screens.

| Area | State |
| --- | --- |
| Maven and module boundaries | Implemented as four modules. Reactor is com.jiexiangtech:royal-chess |
| Royal assets | 12 normalized 512×512 sprites and 4 board images. Runtime rendering uses visible alpha-bounds detection (`alpha > 16`) for proportional full-square piece scaling |
| Corporate assets | Both wordmarks, company emblem, supplied splash, login / dashboard derivatives, seven PNG icon sizes, ICNS and ICO |
| Design system | Reusable component library (`RoyalButton`, `RoyalIconButton`, `RoyalPanel`, `RoyalCard`, `RoyalPlayerCard`, `RoyalGameClock`, `RoyalMoveHistory`, `RoyalCapturedTray`, `RoyalDialog`, `RoyalTextField`, `RoyalToggle`, `RoyalNavigationPanel`, `RoyalBackgroundPanel`, `RoyalIcons`) |
| Splash and Login | Preserved approved visual baseline. Startup progress reflects actual stages; Login supports password reveal and English / Vietnamese |
| Gameplay screen | Redesigned with auto-collapsed navigation rail, larger framed board with subtle gold glow, content-trimmed piece rendering with contact shadows and dark-square readability lift, player cards with tabular clocks, custom move history, icon-based captured tray, and hierarchical action buttons |
| Home, New Game, and secondary screens | Redesigned to match the Royal Luxury theme: cinematic Home hero & stat cards, 2-column New Game lobby with presets & summary, card-based Puzzles, Game History, Leaderboard, Statistics with outcome breakdown bar, Achievements showcase, sectioned Settings with gold toggles & slider, branded About dialog, and outcome-themed Result screen |
| Draw adjudication | Common material and sealed-pawn dead positions verified. General mate reachability remains incomplete |
| Persistence | SQLite migration, PBKDF2, per-profile data, atomic snapshots, safe rollback and replay-based restore |
| AI | Four levels, time limits, cancellation, analysis and optional external Stockfish validated with Stockfish 19 |
| Progression | Local leaderboard, match statistics, achievements, four puzzles and a verified theme unlock |
| Desktop verification | Visual QA performed across Splash, Login, Dashboard, Gameplay (normal & `1020×690` minimum), New Game, Puzzles, Puzzle Play, Game History, Leaderboard, Statistics, Achievements, Settings, Result, and About. Maven passed all 27 tests |
| Packaging | Portable shaded JAR verified with `--smoke` and `--visual-smoke`. Native packaging scripts preserved for release tagging |
| GitHub | Private repository at `jtuongdang/jiexiangtech-royal-chess`. CI verifies the portable JAR |

## Remaining scope

- General dead-position and flag-fall adjudication still lack a complete reachability solver for unusual positions.
- The replay import supports one PGN main line per file and is session-local. The history view filters local results without pagination.
- Four verified puzzles currently cover mate in one. Multi-move puzzles are outside this release.
- Native release signing and notarization require developer signing resources. The checkout provides packaging scripts and local macOS app image support.
- Runtime assets and reviewed screenshots are tracked under `docs/screenshots/`.

[Test report](TEST_REPORT.md) · [Design system](DESIGN_SYSTEM.md) · [Asset manifest](ASSET_MANIFEST.md) · [Private repository](https://github.com/jtuongdang/jiexiangtech-royal-chess)
