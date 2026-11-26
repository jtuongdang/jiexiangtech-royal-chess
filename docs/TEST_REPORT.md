# Test report

Verification on November 26, 2026 used Apple Silicon macOS 15.6, Homebrew OpenJDK 17.0.17
and Maven 3.10.0.

## Maven verification

| Module | Tests passed | Coverage |
| --- | ---: | --- |
| Domain | 14 | Initial and special-position perft, castling, pinned en passant, promotion, FEN/SAN/PGN, checkmate, stalemate, claims, automatic draws, sealed pawns and clocks |
| Application | 3 | Restore repetition and clocks, validate and clear password buffers, verify each puzzle solution |
| Infrastructure | 6 | Migrate and reopen SQLite, authenticate, force and recover a transaction rollback, restore and undo a game, award once, unlock theme and validate legal AI output |
| Desktop | 4 | Sprite and branding assets, native icon signatures, coordinate mapping, offscreen board painting and resource bundle parity |
| **Total** | **27** | **0 failures, 0 errors, 0 skipped** |

The test suite exposed an invalid castling fixture, an extra repetition loop written
after an already terminal position, the inline Mockito agent requiring unsupported attach
permissions, and a tiny interpolation fringe in the smallest icons. The fixtures, test
configuration and icon preprocessing were corrected while keeping all assertions.

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

## Launchers and graphics

- The executable JAR loaded all required classpath assets and migrated a clean SQLite database.
- The native ARM64 app image launched using its embedded Java 17 runtime. Its smoke test verified the assets, database, guest profile, puzzle catalog and move generator.
- The same native launcher was opened in a graphical macOS session. Splash, login, dashboard, live game and the minimum-size game layout were visually inspected. The test database contained the six legal plies shown in the capture.
- A standalone upstream Stockfish 19 binary, verified against the release SHA-256, completed the UCI handshake through the native launcher and returned a legal move. The binary was used from a temporary directory.
- Screenshots in [screenshots](screenshots/) came from the visible application. GUI verification covered those screens and sizes. Unit and repository tests cover underlying workflows.

The tests above are independent of remote CI. The GitHub Actions run associated with each revision records its Linux, Windows and macOS
Maven and portable JAR checks. Additional native packaging has been deferred for local user testing.
