# Requirements

ChessMaster – Royal Edition is a Java 17 Swing application designed for Apple Silicon and
packaged for operating systems with an appropriate JVM or bundled runtime. Games, accounts,
preferences, puzzles and analysis work offline. Stockfish is optional.

| Area | Scope |
| --- | --- |
| Authentication | Register, sign in, remember username, guest profile, local hashed credentials |
| Match setup | Two local players, four AI difficulties, random or chosen color, time and increment, available theme |
| Live match | 64 fixed squares, click or drag, promotion, movement feedback, clocks, move list, captures, save, undo, hint, resign and draw |
| Continuity | Save state and moves atomically. Restore turn, rules state, repetition, clocks and move history. Keep time paused while closed |
| History | Search, filter, view details, resume, replay and export. Open an imported single-game PGN for replay |
| Progress | Local profiles, AI results, rating history, achievements, puzzles, rewards and Royal Slate |
| Preferences | English and Vietnamese, theme, sound, animation, orientation, piece scale, graphics quality, auto-save and reset |
| Branding | JIEXIANGTECH identity, supplied artwork, stage-driven splash, branded login, About and operating system icons |
| Delivery | Maven tests, runnable JAR, jpackage scripts and private GitHub CI with verified-only release workflow |

[Implementation status](IMPLEMENTATION_STATUS.md) records partial and platform-specific deliverables.
