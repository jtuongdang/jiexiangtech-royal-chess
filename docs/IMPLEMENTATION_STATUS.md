# Implementation status

November 26, 2026 · ChessMaster – Royal Edition · JIEXIANGTECH (杰祥科技)

The existing project was kept in place and refactored to the requested Maven group and Java
package. Work began with environment inspection and source art classification, then moved
through domain checks, database and application services, gameplay, branding and verification.

| Area | State |
| --- | --- |
| Maven and module boundaries | Implemented as four modules. Reactor is com.jiexiangtech:royal-chess |
| Royal assets | 12 normalized sprites and 4 board images. Both source reference sheets are preserved locally |
| Corporate assets | Both wordmarks, company emblem, supplied splash, login / dashboard derivatives, seven PNG icon sizes, ICNS and ICO |
| Splash, login, registration and About | Implemented. Startup progress reflects actual stages. Login supports reveal and English / Vietnamese |
| Gameplay, clocks and notation | Implemented for local and AI matches. Includes PGN export and single-game replay import |
| Draw adjudication | Common material and sealed-pawn dead positions verified. General mate reachability remains incomplete |
| Persistence | SQLite migration, PBKDF2, per-profile data, atomic snapshots, safe rollback and replay-based restore |
| AI | Four levels, time limits, cancellation, analysis and optional external Stockfish validated with Stockfish 19 |
| Progression | Local leaderboard, match statistics, achievements, four puzzles and a verified theme unlock |
| Desktop verification | Visual smoke test on the branded macOS app at normal and minimum size. Maven passed 27 tests |
| Packaging | Portable JAR ready for user testing. A development ARM64 app image passed a smoke check. Additional native packaging is deferred during user testing |
| GitHub | Private repository created at jtuongdang/jiexiangtech-royal-chess. CI verifies the portable JAR. Native packaging and publishing are reserved for the tag-gated release workflow |

## Remaining scope

- General dead-position and flag-fall adjudication still lack a complete reachability solver for unusual positions.
- The replay import supports one PGN main line per file and is session-local. The history view filters local results without pagination.
- Four verified puzzles currently cover mate in one. The levels distinguish the supplied patterns. Multi-move puzzles are outside this release.
- Native release signing and notarization require developer signing resources. The checkout provides a runnable local macOS app image and packaging scripts.
- Runtime assets and reviewed screenshots are tracked. The supplied source-image directories remain preserved in the local workspace.

[Test report](TEST_REPORT.md) · [Asset manifest](ASSET_MANIFEST.md) · [Private repository](https://github.com/jtuongdang/jiexiangtech-royal-chess)
