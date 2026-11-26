# Test report

Verification on November 26, 2026 used Apple Silicon macOS 15.6, OpenJDK 17, and Maven.

## Maven verification (`mvn clean verify`)

| Module | Tests passed | Coverage |
| --- | ---: | --- |
| Domain | 14 | Initial and special-position perft, castling, pinned en passant, promotion, FEN/SAN/PGN, checkmate, stalemate, claims, automatic draws, sealed pawns and clocks |
| Application | 3 | Restore repetition and clocks, validate and clear password buffers, verify each puzzle solution |
| Infrastructure | 6 | Migrate and reopen SQLite, authenticate, force and recover a transaction rollback, restore and undo a game, award once, unlock theme and validate legal AI output |
| Desktop | 4 | Sprite and branding assets, native icon signatures, coordinate mapping, offscreen board painting and English / Vietnamese resource bundle parity |
| **Total** | **27** | **0 failures, 0 errors, 0 skipped** |

## Perft

| Position | Depth | Expected and observed |
| --- | ---: | ---: |
| Initial | 1 | 20 |
| Initial | 2 | 400 |
| Initial | 3 | 8,902 |
| Initial | 4 | 197,281 |
| Kiwipete | 3 | 97,862 |
| Endgame with en passant sequences | 4 | 43,238 |
| Castling and promotion position | 3 | 9,467 |

## Launchers and GUI visual QA

- Executable shaded JAR (`chess-desktop/target/royal-chess-1.0.0-all.jar --smoke`) loaded all classpath assets, migrated SQLite, verified the guest profile and puzzle catalog, and confirmed `perft(3)=8902`.
- Two full rounds of graphical visual QA (`--visual-smoke ... --all`) were executed in a live macOS desktop session and reviewed screenshot by screenshot across:
  - Approved baseline screens: `splash.png`, `login.png` (verified visually preserved).
  - Live gameplay: `gameplay.png` (`1360×880`) and `gameplay-minimum.png` (`1020×690`), verifying auto-collapsed navigation rail, square board dominance, visible-bounds piece scaling, base alignment, contact shadows, dark-square black-piece contrast, player cards/clocks, custom move history, icon-based captured tray, and hierarchical action buttons.
  - Redesigned lobby & secondary screens: `dashboard.png`, `new-game.png`, `puzzles.png`, `puzzle-play.png`, `history.png`, `leaderboard.png`, `statistics.png`, `achievements.png`, `settings.png`, `result.png`, and `about.png`.
- Visual QA Round 1 identified and fixed: sidebar `Sign out` label truncation, Dashboard empty-state CTA vertical stretch, Result hero vertical alignment, About dialog row spacing, Settings slider gold theming and percentage pill height, New Game board theme card sizing, Statistics breakdown bar height, and dark-piece contrast in player avatars and captured trays. Round 2 verified all fixes cleanly.
- Updated screenshots are committed under [screenshots](screenshots/).
