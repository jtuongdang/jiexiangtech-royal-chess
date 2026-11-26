# Test report

Verification on November 26, 2026 used Apple Silicon macOS 15.6, OpenJDK 17, and Maven.

## Maven verification (`mvn -o -B -ntp clean verify`)

| Module | Tests passed | Coverage |
| --- | ---: | --- |
| Domain | 14 | Initial and special-position perft, castling, pinned en passant, promotion, FEN/SAN/PGN, checkmate, stalemate, claims, automatic draws, sealed pawns and clocks |
| Application | 3 | Restore repetition and clocks, validate and clear password buffers, verify each puzzle solution |
| Infrastructure | 6 | Migrate and reopen SQLite, authenticate, force and recover a transaction rollback, restore and undo a game, award once, unlock theme and validate legal AI output |
| Desktop | 6 | Sprite and bezel alpha, marble variants, Classic piece and bezel ratios, 2D and 2.5D coordinate round-trips, 2.5D click-to-move, offscreen rendering, icons and locale bundles |
| Server | 6 | Invitation authorization, seat isolation, clocks, replay and dedupe, plus HTTP/WebSocket with real MongoDB across restart |
| **Total** | **35** | **0 failures, 0 errors, 0 skipped** |

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

## Launchers and visual verification

- The rebuilt JAR passed `--smoke` for classpath assets, SQLite migrations, guest profile,
  puzzle catalog and `perft(3)=8902`.
- The macOS Swing launcher captured [Classic gameplay](screenshots/gameplay.png) at
  1360 × 847 content pixels and at the [minimum window size](screenshots/gameplay-minimum.png).
  The fixture reaches e4 with a legal white knight and paints the corresponding move targets.
- The [Classic comparison](screenshots/royal-classic-comparison.png) shows the measured
  Interface-2 source alongside the live board. The test suite verifies bezel and sprite ratios
  through multiple sizes with nearest-pixel tolerance.
- The refreshed [Royal Focus capture](screenshots/royal-focus-actual.png) at 1280 × 720 and
  [comparison](screenshots/royal-focus-comparison.png) show the new chess sprites and marble
  under the established 2.5D camera. Geometry round-trip and click-to-move tests pass.
- Native macOS packaging (`scripts/package-macos.sh`) succeeded with OpenJDK 17 `jpackage`,
  producing `dist/app/ChessMaster Royal.app` and `dist/ChessMaster Royal-1.0.0.dmg` (58 MB).
  The packaged native launcher (`ChessMaster Royal --smoke`) passed assets, SQLite migrations/profile,
  puzzles, and `perft(3)=8902` verification.

The online test run set `ROYAL_TEST_MONGO_URI` to a local Docker MongoDB 8.0 test database.

The `linux/amd64` container for commit `834ff84` used the verified Maven JAR and ran with a
512 MiB memory limit. Its health response reported the expected commit, and the container
passed an invite, join, legal-move, duplicate-command and resynchronization check against
MongoDB. The Docker Hub push and subsequent manifest lookup returned the same
[image digest](ONLINE_DEPLOYMENT.md#deploy-the-private-image).
