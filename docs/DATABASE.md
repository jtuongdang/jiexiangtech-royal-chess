# SQLite schema

Migrations run transactionally on startup. Each connection enables foreign keys and a
5-second busy timeout. The database uses WAL. Reads use prepared statements, and writes
use immediate transactions. Saved game rows are accessible only to their owning profile.

| Table | Data |
| --- | --- |
| schema_migrations | Applied schema version |
| users | Unique username, display name, salted PBKDF2-HMAC-SHA256 hash |
| player_statistics | Rating, AI wins/losses/draws, rated win streak and puzzle points |
| games | Owner, mode, side, level, theme, time control, FENs, clocks, outcome and elapsed time |
| game_moves | Ordered UCI and SAN plus post-move clock checkpoints, keyed by game and ply |
| rating_history | One rating change per eligible finished game |
| puzzles | Verified seed FENs, solution, difficulty and base points |
| puzzle_attempts | Solved, hint use, points and timestamp for each attempt |
| achievements / user_achievements | Catalog and unique per-profile unlocks |
| themes / user_themes | Theme catalog and unlocks |
| user_settings | Language, theme, audio, animation, orientation, scale, quality and auto-save |
| app_settings | Remembered username |

An unfinished-game save atomically updates the snapshot, removes plies dropped by undo,
and inserts or updates plies that differ. When a computer game ends, result, move history,
rating update and unlocked achievements commit together. A second save of an already
finished game is idempotent. Puzzle points and the theme unlock commit with the attempt.

The starting rating is 1200. An unassisted computer game with at least one move by each
side is eligible for a signed-in user. Rating change uses
`round(24 × (score − 1/(1+10^((opponent−current)/400))))` with outcomes 1, 0.5 and 0.
Reference ratings by difficulty are 700, 1000, 1350 and 1700. These are local game
parameters. Guest and local two-player games do not affect the leaderboard.

The file is `chessmaster.db` under:

| Platform | Default directory |
| --- | --- |
| macOS | `~/Library/Application Support/ChessMaster Royal` |
| Windows | `%LOCALAPPDATA%\ChessMaster Royal` |
| Linux | `${XDG_DATA_HOME:-$HOME/.local/share}/chessmaster-royal` |

Set `CHESSMASTER_DATA_DIR` or `-Droyal.dataDir` for a different data directory.
Documented table names reflect the shipped [migration](../chess-infrastructure/src/main/resources/db/V001.sql).
