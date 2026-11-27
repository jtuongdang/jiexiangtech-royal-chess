# Test report

## Royal Arena · November 27, 2026

Apple Silicon macOS, OpenJDK 17 and local MongoDB 8.2.0. The seven-module Maven reactor
passed `mvn -o -B -ntp clean verify` with `ROYAL_TEST_MONGO_URI` set to an isolated test database.

| Module | Passed | Arena coverage |
| --- | ---: | --- |
| Domain | 14 | Chess rules and perft |
| Application | 3 | Restore, authentication and puzzles |
| Infrastructure | 7 | Per-profile portrait selection survives closing and reopening SQLite |
| Desktop | 9 | Packaged Arena scene and portraits, sprite alpha, geometry, hit testing and animation |
| Server | 6 | Both avatar IDs survive join, moves, reconnect and MongoDB-backed server restart. Legacy snapshots retain their original fields |
| **Total** | **39** | **0 failures, 0 errors, 0 skipped** |

The desktop Swing visual smoke captured [Royal Arena](screenshots/royal-focus-actual.png),
[Classic](screenshots/gameplay.png), [the minimum window](screenshots/gameplay-minimum.png),
and [the avatar picker](screenshots/royal-arena-avatars.png). The [live comparison](screenshots/royal-focus-comparison.png)
pairs the actual Focus capture with the supplied design.
The built desktop JAR passed its launcher smoke check, including `perft(3)=8902`.

[GitHub Actions](https://github.com/jtuongdang/jiexiangtech-royal-chess/actions/runs/45929018725)
passed Linux, macOS and Windows verification plus the MongoDB and reconnect job for
`6f54d79`. The `linux/amd64` image ran with a 512 MiB memory limit and passed its
revision, legacy snapshot, two-seat avatar and legal move checks. Its manifest digest
was verified after the private Docker Hub push.

### Desktop v1.3.0

The release commit `1123d7a` passed all 39 tests locally with MongoDB. [Main CI](https://github.com/jtuongdang/jiexiangtech-royal-chess/actions/runs/45929639873)
passed on macOS, Windows and Linux, including the MongoDB and reconnect job.
The Apple Silicon DMG passed `hdiutil verify` and the launcher passed `--smoke` from the mounted image.
The extracted ZIP also passed the launcher smoke check. The native app image captured the Royal Arena
board and avatar picker. All 2,638 JAR entries from the locally packaged app matched the Linux CI JAR.
The [release workflow](https://github.com/jtuongdang/jiexiangtech-royal-chess/actions/runs/45929725556)
verified the staged Mac packages and ran the Linux and Windows native launchers before publishing
the DMG, ZIPs, Linux archive, portable JAR and checksums in [v1.3.0](https://github.com/jtuongdang/jiexiangtech-royal-chess/releases/tag/v1.3.0).
The first release attempt hit an HTTP 429 from Maven Central. The retry passed.

## Earlier releases · November 26, 2026

Verification on November 26, 2026 used Apple Silicon macOS 15.6, OpenJDK 17, and Maven.

## Maven verification (`mvn -o -B -ntp clean verify`)

| Module | Tests passed | Coverage |
| --- | ---: | --- |
| Domain | 14 | Initial and special-position perft, castling, pinned en passant, promotion, FEN/SAN/PGN, checkmate, stalemate, claims, automatic draws, sealed pawns and clocks |
| Application | 3 | Restore repetition and clocks, validate and clear password buffers, verify each puzzle solution |
| Infrastructure | 6 | Migrate and reopen SQLite, authenticate, force and recover a transaction rollback, restore and undo a game, award once, unlock theme and validate legal AI output |
| Desktop | 8 | Sprite and bezel alpha, marble variants, board geometry and hit testing, intermediate and final animation frames, drag release, capture, castling, promotion, icons and locale bundles |
| Server | 6 | Invitation authorization, seat isolation, clocks, replay and dedupe, plus HTTP/WebSocket with real MongoDB across restart |
| **Total** | **37** | **0 failures, 0 errors, 0 skipped** |

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

The November 26 cloud deployment passed the public `/health` check for `834ff84` against
MongoDB Atlas. Two instances of the desktop network client verified organizer authorization,
room creation and join, two legal moves over HTTPS, notifications over WSS, idempotent retry,
resynchronization after disconnect and a persisted resignation result.

[GitHub Actions for the deployed revision](https://github.com/jtuongdang/jiexiangtech-royal-chess/actions/runs/45824365829)
passed on Linux and Windows, and the live MongoDB integration job passed. The hosted macOS
job was cancelled. The local macOS Maven reactor and desktop smoke test passed.

For desktop v1.1.0, the macOS Swing launcher refreshed the live Focus capture and reference
comparison. The Apple Silicon DMG was generated, its integrity verified with `hdiutil`, and
its bundled native launcher passed `--smoke` after mounting the image.
The Apple Silicon ZIP's native launcher also passed. In a warmed, offscreen 2× Retina
sample, the Expert engine returned a legal reply and the two Ultra renderers painted it.
The 95th-percentile paint times were 5.2 ms for Classic and 11.8 ms for Focus on this Mac.

[Release CI](https://github.com/jtuongdang/jiexiangtech-royal-chess/actions/runs/45839682535)
passed Linux and Windows verification, MongoDB integration, Linux and Windows native launcher
checks, and the Apple Silicon DMG and ZIP payload and architecture checks. The Mac launcher
ran on the local Apple Silicon machine. The workflow published the packages and SHA-256
checksums in [v1.1.0](https://github.com/jtuongdang/jiexiangtech-royal-chess/releases/tag/v1.1.0).

For desktop v1.2.0 (Online UI/UX upgrade: tabbed Create/Join lobby, collapsible wide server
settings, cold-start notice, waiting invite banner, player names & low-time warning on clocks,
move/result audio cues, and RoyalDialog match-outcome modal with Back-to-Lobby), `mvn -B -ntp clean verify`
passed all 7 modules (Domain 14, Application 3, Infrastructure 6, Desktop 8, Server unit tests;
0 failures, 0 errors). The rebuilt `royal-chess-1.2.0-all.jar` passed `--smoke` (`perft(3)=8902`),
and `scripts/package-macos.sh dist dmg` produced `dist/royal-chess-macos-arm64-1.2.0.dmg` (64 MB)
verified with `hdiutil verify` (`VALID`).
