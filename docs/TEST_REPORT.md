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

## Launchers and GUI Visual Verification Loop

- Executable shaded JAR (`chess-desktop/target/royal-chess-1.0.0-all.jar --smoke`) loaded all 51 classpath resources (including `gameplay-background.png` and `company-horizontal.png`), migrated SQLite, verified the guest profile and puzzle catalog, and confirmed `perft(3)=8902`.
- Multiple live macOS desktop GUI verification cycles (`--visual-smoke ... --all`) compared live screenshots directly against the approved Splash/Login baseline and the official Royal Luxury Gameplay reference:
  - Approved baseline screens: `splash.png`, `login.png` (verified visually unchanged).
  - Reference-matched Gameplay: `gameplay.png` (`1360×880`, full 3-column layout) and `gameplay-minimum.png` (`1020×690`, responsive auto-collapsed icon navigation rail), verifying the horizontal `JIEXIANGTECH 杰祥科技` header, ornate gold-framed marble board with decorative corners, Retina-cached visible-bounds pieces with contact shadows and dual-surface contrast, luminous selected square (`d2`) + sage-gold legal move dots (`d3`/`d4`), stacked Black/White player cards with circular royal avatars, Elo & AI/You badges, active turn green dot & gold crown glow, Move History gold highlight bar, side-by-side Captured Pieces trays with real piece PNGs, and the 3-row metallic gold control grid + Pause & Return.
  - Visual polish fixes applied between capture rounds: removed Elo comma formatting (`Elo: 1750` / `Elo: 1200`), switched player card title row to zero-clip `BoxLayout` so the live green turn indicator is always visible, and intensified the selected-square golden light bloom.
- Updated verified screenshots are committed under [screenshots](screenshots/).
