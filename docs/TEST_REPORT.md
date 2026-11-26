# Test report

Verification on November 26, 2026 used Apple Silicon macOS 15.6, OpenJDK 17, and Maven.

## Maven verification (`mvn clean verify`)

| Module | Tests passed | Coverage |
| --- | ---: | --- |
| Domain | 14 | Initial and special-position perft, castling, pinned en passant, promotion, FEN/SAN/PGN, checkmate, stalemate, claims, automatic draws, sealed pawns and clocks |
| Application | 3 | Restore repetition and clocks, validate and clear password buffers, verify each puzzle solution |
| Infrastructure | 6 | Migrate and reopen SQLite, authenticate, force and recover a transaction rollback, restore and undo a game, award once, unlock theme and validate legal AI output |
| Desktop | 4 | Sprite and branding assets (including Focus Mode assets), native icon signatures, 2D & 2.5D perspective 64-square coordinate round-trips, offscreen 2D & Royal Focus 2.5D board painting, and English / Vietnamese resource bundle parity |
| **Total** | **27** | **0 failures, 0 errors, 0 skipped** |

## Perft

| Position | Depth | Expected and observed |
| --- | ---: | --- |
| Initial | 1 | 20 |
| Initial | 2 | 400 |
| Initial | 3 | 8,902 |
| Initial | 4 | 197,281 |
| Kiwipete | 3 | 97,862 |
| Endgame with en passant sequences | 4 | 43,238 |
| Castling and promotion position | 3 | 9,467 |

## Launchers and Pixel-Perfect Visual QA

- Executable shaded JAR (`chess-desktop/target/royal-chess-1.0.0-all.jar --smoke`) loaded all 59 classpath resources, migrated SQLite, verified the guest profile and puzzle catalog, and confirmed `perft(3)=8902`.
- **Royal Focus Mode 2.5D Visual Verification**:
  - Captured live Swing rendering at `16:9` (`royal-focus-actual.png`) and generated side-by-side comparison (`royal-focus-comparison.png`) against [royal-focus-reference.png](design/royal-focus-reference.png).
  - Verified geometry alignment across Left Sidebar (`~13%` with gold edge, active glow pill, bottom knight ornament), Top Branding (`JIEXIANGTECH 杰祥科技` + `CHESSMASTER ROYAL EDITION`), Left/Right Player Cards (`Black` & `White` with circular avatars, `AI`/`You` badges, live green turn dot, Elo, and clocks), 2.5D Perspective Chessboard (`taper = 0.625`, 64 warped marble squares, physical 3D front slab with golden crown & laurel sprigs, ornate corner filigree, foreshortened `A–H` / `1–8` coordinates, depth-sorted Staunton pieces, shadows, reflections, golden `e4` selection glow, green-gold `e5` legal target), and Bottom Horizontal Pill Toolbar.
  - Round 1 → Round 2 visual refinements fixed Player Card badge clearance (`AI` / `You` + green dot), calibrated 2.5D piece height (`1.18 × localTileWidth`) and base anchor (`0.74`) for clean Rank 1 / Rank 2 separation, and upgraded the front slab's sculpted golden laurel sprigs.
- Approved baseline `splash.png`, `login.png`, Classic 2D `gameplay.png`, `gameplay-minimum.png`, `dashboard.png`, and `settings.png` (showing Display Mode & Graphics Quality selectors) were all verified clean.
