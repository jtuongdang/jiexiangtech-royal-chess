# Implementation status

November 26, 2026 · ChessMaster – Royal Edition · JIEXIANGTECH (杰祥科技)

The existing four-module architecture (`com.jiexiangtech:royal-chess`) and working domain,
SQLite persistence, authentication, and AI engine layers were preserved in place. The desktop
presentation layer has been redesigned to match the approved Splash/Login baseline and the
official Royal Luxury Gameplay visual reference.

| Area | State |
| --- | --- |
| Maven and module boundaries | Implemented as four modules. Reactor is `com.jiexiangtech:royal-chess` |
| Royal assets | 12 normalized 512×512 RGBA sprites, 4 marble board images, atmospheric `gameplay-background.png`, and horizontal `company-horizontal.png` brand lockup derived without altering raw originals |
| Corporate assets | Both wordmarks, company emblem, horizontal header lockup, supplied splash, login / dashboard / gameplay derivatives, seven PNG icon sizes, ICNS and ICO |
| Design system & graphics engine | `RoyalGlowEffect`, `RoyalAnimationEngine`, `RoyalPieceRenderer` (Retina-aware progressive bicubic sprite cache + alpha-bounds height ratios 94/90/84/82/76/67%), `RoyalBoardFrame` (metallic gold beveled frame + decorative corners + inner/outer shadows), `RoyalNavigationPanel` (glowing gold right edge + bottom knight ornament), `RoyalPlayerCard`, `RoyalGameClock`, `RoyalMoveHistory`, `RoyalCapturedTray`, `RoyalButton`, `RoyalIconButton`, `RoyalDialog`, `RoyalTextField`, `RoyalToggle`, `RoyalBackgroundPanel`, `RoyalIcons` |
| Splash and Login | Preserved approved visual baseline. Startup progress reflects actual stages; Login supports password reveal and English / Vietnamese |
| Gameplay screen | Matches the 3-column Royal Luxury visual reference: Left Navigation (~15% with gold icons, glowing active pill, bottom Knight ornament; auto-collapses smoothly below 1180px width), Center Stage (~55% with horizontal JIEXIANGTECH header + massive framed marble board + luminous selection/legal-move/check/hint/victory overlays), Right Panel (~30% with stacked Black/White Player Cards, circular royal avatars, Elo & AI/Local badges, active gold glow & crown, large tabular clocks, Move History with gold highlight bar, side-by-side Captured Pieces trays with real piece PNGs & counts, and 3-row golden control grid + Pause & Return) |
| Home, New Game, and secondary screens | Cohesive Royal Luxury redesign across Home, New Game, Puzzles, Puzzle Play, Game History, Replay, Leaderboard, Statistics, Achievements, Settings, Result, and About dialog |
| Draw adjudication | Common material and sealed-pawn dead positions verified. General mate reachability remains incomplete |
| Persistence | SQLite migration, PBKDF2, per-profile data, atomic snapshots, safe rollback and replay-based restore |
| AI | Four levels, time limits, cancellation, analysis and optional external Stockfish validated with Stockfish 19 |
| Progression | Local leaderboard, match statistics, achievements, four puzzles and a verified theme unlock |
| Desktop verification | Maven passed all 27 tests; `--smoke` and live macOS GUI `--visual-smoke --all` verified across normal (`1360×880`) and minimum (`1020×690`) layouts |
| GitHub | Private repository at `jtuongdang/jiexiangtech-royal-chess`. CI verifies the portable JAR |

## Remaining scope

- Online multiplayer / game server is intentionally deferred outside this stage.
- General dead-position and flag-fall adjudication still lack a complete reachability solver for unusual positions.
- The replay import supports one PGN main line per file and is session-local. The history view filters local results without pagination.
- Four verified puzzles currently cover mate in one. Multi-move puzzles are outside this release.
- Native release signing and notarization require developer signing resources. The checkout provides packaging scripts and local macOS app image support.
- Runtime assets and reviewed screenshots are tracked under `docs/screenshots/`.

[Test report](TEST_REPORT.md) · [Design system](DESIGN_SYSTEM.md) · [Asset manifest](ASSET_MANIFEST.md) · [Private repository](https://github.com/jtuongdang/jiexiangtech-royal-chess)
