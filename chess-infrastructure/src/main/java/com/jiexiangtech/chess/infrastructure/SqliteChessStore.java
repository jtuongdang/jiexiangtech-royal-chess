package com.jiexiangtech.chess.infrastructure;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.jiexiangtech.chess.application.*;
import com.jiexiangtech.chess.domain.GameClock.Times;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Side;

public final class SqliteChessStore implements ChessStore {
    private static final String PROFILE = """
            SELECT u.id,u.username,u.display_name,s.rating,s.wins,s.losses,s.draws,s.streak,s.puzzle_points
            FROM users u JOIN player_statistics s ON s.user_id=u.id
            """;
    private static final String GAME_SAVE = """
            INSERT INTO games VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET updated=excluded.updated,current_fen=excluded.current_fen,
            white_ms=excluded.white_ms,black_ms=excluded.black_ms,reason=excluded.reason,winner=excluded.winner,
            assisted=excluded.assisted,elapsed_ms=excluded.elapsed_ms
            WHERE games.owner_id=excluded.owner_id AND games.reason IS NULL
            """;
    private static final java.time.format.DateTimeFormatter INSTANT = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();
    private final Database db;

    public SqliteChessStore(Path file) {
        db = new Database(file);
        db.migrate();
    }

    @Override public User register(String username, String displayName, String passwordHash) {
        return db.write(connection -> {
            long id;
            try (var statement = connection.prepareStatement("""
                    INSERT INTO users(username,display_name,password_hash) VALUES(?,?,?)
                    ON CONFLICT(username) DO NOTHING RETURNING id
                    """)) {
                statement.setString(1, username);
                statement.setString(2, displayName);
                statement.setString(3, passwordHash);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new DuplicateUsernameException();
                    id = rows.getLong(1);
                }
            }
            try (var statement = connection.prepareStatement("INSERT INTO player_statistics(user_id) VALUES(?)")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            return new User(id, username, displayName);
        });
    }

    @Override public Optional<User> authenticate(String username, char[] password, Passwords verifier) {
        return db.read(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM users WHERE username=? AND id<>?")) {
                statement.setString(1, username);
                statement.setLong(2, GUEST_ID);
                try (var rows = statement.executeQuery()) {
                    if (rows.next() && verifier.matches(password, rows.getString("password_hash"))) return Optional.of(user(rows));
                    return Optional.empty();
                }
            }
        });
    }

    @Override public SaveResult save(MatchSnapshot game) {
        return db.write(connection -> {
            String id = game.id().toString();
            try (var find = connection.prepareStatement("SELECT owner_id,reason,updated FROM games WHERE id=?")) {
                find.setString(1, id);
                try (var rows = find.executeQuery()) {
                    if (rows.next()) {
                        if (rows.getLong(1) != game.owner()) throw new SQLException("Game ownership mismatch");
                        if (rows.getString(2) != null) return SaveResult.ALREADY_FINISHED;
                        if (Instant.parse(rows.getString(3)).isAfter(game.updated())) throw new SQLException("Stale snapshot");
                    }
                }
            }
            try (var save = connection.prepareStatement(GAME_SAVE)) {
                var options = game.options();
                save.setString(1, id);
                save.setLong(2, game.owner());
                save.setString(3, options.mode().name());
                save.setString(4, options.human().name());
                save.setString(5, options.level().name());
                save.setLong(6, options.baseMillis());
                save.setLong(7, options.incrementMillis());
                save.setString(8, options.theme());
                save.setString(9, INSTANT.format(game.started()));
                save.setString(10, INSTANT.format(game.updated()));
                save.setString(11, game.initialFen());
                save.setString(12, game.currentFen());
                save.setLong(13, game.times().whiteMillis());
                save.setLong(14, game.times().blackMillis());
                save.setString(15, game.outcome() == null ? null : game.outcome().reason().name());
                save.setString(16, game.outcome() == null || game.outcome().winner() == null ? null : game.outcome().winner().name());
                save.setBoolean(17, game.assisted());
                save.setLong(18, game.elapsedMillis());
                save.executeUpdate();
            }
            try (var clear = connection.prepareStatement("DELETE FROM game_moves WHERE game_id=? AND ply>?")) {
                clear.setString(1, id);
                clear.setInt(2, game.moves().size());
                clear.executeUpdate();
            }
            try (var move = connection.prepareStatement("""
                    INSERT INTO game_moves VALUES(?,?,?,?,?,?)
                    ON CONFLICT(game_id,ply) DO UPDATE SET uci=excluded.uci,san=excluded.san,
                    white_ms=excluded.white_ms,black_ms=excluded.black_ms
                    WHERE uci<>excluded.uci OR san<>excluded.san OR white_ms<>excluded.white_ms OR black_ms<>excluded.black_ms
                    """)) {
                int ply = 1;
                for (var entry : game.moves()) {
                    move.setString(1, id);
                    move.setInt(2, ply++);
                    move.setString(3, entry.uci());
                    move.setString(4, entry.san());
                    move.setLong(5, entry.times().whiteMillis());
                    move.setLong(6, entry.times().blackMillis());
                    move.addBatch();
                }
                move.executeBatch();
            }
            if (game.outcome() != null && game.options().mode() == MatchOptions.Mode.COMPUTER) updateProgress(connection, game);
            return SaveResult.SAVED;
        });
    }

    @Override public Optional<MatchSnapshot> load(long owner, UUID id) {
        return db.read(connection -> {
            try (var select = connection.prepareStatement("""
                    SELECT g.*,m.ply,m.uci,m.san,m.white_ms AS move_white,m.black_ms AS move_black
                    FROM games g LEFT JOIN game_moves m ON m.game_id=g.id
                    WHERE g.id=? AND g.owner_id=? ORDER BY m.ply
                    """)) {
                select.setString(1, id.toString());
                select.setLong(2, owner);
                try (var rows = select.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    var options = options(rows);
                    var started = Instant.parse(rows.getString("started"));
                    var updated = Instant.parse(rows.getString("updated"));
                    String initial = rows.getString("initial_fen"), current = rows.getString("current_fen");
                    Times times = new Times(rows.getLong("white_ms"), rows.getLong("black_ms"));
                    Outcome outcome = outcome(rows);
                    boolean assisted = rows.getBoolean("assisted");
                    long elapsed = rows.getLong("elapsed_ms");
                    var moves = new ArrayList<MatchSnapshot.Ply>();
                    do {
                        String uci = rows.getString("uci");
                        if (uci != null) moves.add(new MatchSnapshot.Ply(uci, rows.getString("san"),
                                new Times(rows.getLong("move_white"), rows.getLong("move_black"))));
                    } while (rows.next());
                    return Optional.of(new MatchSnapshot(id, owner, options, started, updated, initial, current, moves,
                            times, outcome, assisted, elapsed));
                }
            }
        });
    }

    @Override public List<Summary> history(long owner) {
        return db.read(connection -> {
            try (var select = connection.prepareStatement("""
                    SELECT g.*,(SELECT count(*) FROM game_moves m WHERE m.game_id=g.id) AS plies
                    FROM games g WHERE owner_id=? ORDER BY updated DESC
                    """)) {
                select.setLong(1, owner);
                try (var rows = select.executeQuery()) {
                    var history = new ArrayList<Summary>();
                    while (rows.next()) history.add(new Summary(UUID.fromString(rows.getString("id")), options(rows),
                            outcome(rows), Instant.parse(rows.getString("updated")), rows.getInt("plies")));
                    return List.copyOf(history);
                }
            }
        });
    }

    @Override public Profile profile(long owner) { return db.read(connection -> profile(connection, owner)); }

    @Override public List<Profile> leaderboard() {
        return db.read(connection -> {
            try (var select = connection.prepareStatement(PROFILE + " WHERE u.id<>? ORDER BY s.rating DESC,s.wins DESC,u.username")) {
                select.setLong(1, GUEST_ID);
                try (var rows = select.executeQuery()) {
                    var profiles = new ArrayList<Profile>();
                    while (rows.next()) profiles.add(profile(rows, List.of()));
                    return List.copyOf(profiles);
                }
            }
        });
    }

    private static Profile profile(Connection connection, long owner) throws SQLException {
        var badges = new ArrayList<String>();
        try (var select = connection.prepareStatement("SELECT achievement_id FROM user_achievements WHERE user_id=? ORDER BY unlocked")) {
            select.setLong(1, owner);
            try (var rows = select.executeQuery()) { while (rows.next()) badges.add(rows.getString(1)); }
        }
        try (var select = connection.prepareStatement(PROFILE + " WHERE u.id=?")) {
            select.setLong(1, owner);
            try (var rows = select.executeQuery()) {
                if (!rows.next()) throw new SQLException("Unknown user");
                return profile(rows, badges);
            }
        }
    }

    private static Profile profile(ResultSet rows, List<String> badges) throws SQLException {
        return new Profile(user(rows), rows.getInt("rating"), rows.getInt("wins"), rows.getInt("losses"),
                rows.getInt("draws"), rows.getInt("streak"), rows.getInt("puzzle_points"), badges);
    }

    private static User user(ResultSet rows) throws SQLException {
        return new User(rows.getLong("id"), rows.getString("username"), rows.getString("display_name"));
    }

    private static MatchOptions options(ResultSet rows) throws SQLException {
        return new MatchOptions(MatchOptions.Mode.valueOf(rows.getString("mode")), Side.valueOf(rows.getString("human")),
                MatchOptions.Level.valueOf(rows.getString("level")), rows.getLong("base_ms"), rows.getLong("increment_ms"),
                rows.getString("theme_id"));
    }

    private static Outcome outcome(ResultSet rows) throws SQLException {
        String reason = rows.getString("reason"), winner = rows.getString("winner");
        return reason == null ? null : new Outcome(Outcome.Reason.valueOf(reason), winner == null ? null : Side.valueOf(winner));
    }

    private static void updateProgress(Connection connection, MatchSnapshot game) throws SQLException {
        Profile previous = profile(connection, game.owner());
        Side winner = game.outcome().winner();
        boolean won = winner == game.options().human(), draw = winner == null;
        int rating = previous.rating();
        boolean rated = !previous.user().guest() && !game.assisted() && game.moves().size() >= 2;
        int streak = rated ? (won ? previous.streak() + 1 : 0) : previous.streak();
        if (rated) {
            double expected = 1.0 / (1.0 + Math.pow(10, (game.options().level().referenceRating - rating) / 400.0));
            rating = Math.max(0, rating + (int) Math.round(24 * ((draw ? 0.5 : won ? 1.0 : 0.0) - expected)));
            try (var insert = connection.prepareStatement("INSERT INTO rating_history VALUES(?,?,?,?)")) {
                insert.setString(1, game.id().toString());
                insert.setLong(2, game.owner());
                insert.setInt(3, previous.rating());
                insert.setInt(4, rating);
                insert.executeUpdate();
            }
        }
        try (var update = connection.prepareStatement("""
                UPDATE player_statistics SET rating=?,wins=wins+?,losses=losses+?,draws=draws+?,streak=? WHERE user_id=?
                """)) {
            update.setInt(1, rating);
            update.setInt(2, won ? 1 : 0);
            update.setInt(3, won || draw ? 0 : 1);
            update.setInt(4, draw ? 1 : 0);
            update.setInt(5, streak);
            update.setLong(6, game.owner());
            update.executeUpdate();
        }
        if (!rated || !won) return;
        long owner = game.owner();
        award(connection, owner, "first-victory");
        if (game.outcome().reason() == Outcome.Reason.CHECKMATE) award(connection, owner, "checkmate-master");
        if (streak >= 3) award(connection, owner, "win-streak");
        if (game.options().level().ordinal() >= MatchOptions.Level.HARD.ordinal()) award(connection, owner, "ai-challenger");
        if (rating >= 1350) award(connection, owner, "royal-champion");
    }

    private static void award(Connection connection, long owner, String badge) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO user_achievements VALUES(?,?,?) ON CONFLICT DO NOTHING")) {
            insert.setLong(1, owner);
            insert.setString(2, badge);
            insert.setString(3, INSTANT.format(Instant.now()));
            insert.executeUpdate();
        }
        if (!badge.equals("puzzle-master")) return;
        try (var insert = connection.prepareStatement("INSERT INTO user_themes VALUES(?,'royal-slate') ON CONFLICT DO NOTHING")) {
            insert.setLong(1, owner);
            insert.executeUpdate();
        }
    }

    @Override public Preferences settings(long owner, String defaultLanguage) {
        return db.read(connection -> {
            try (var select = connection.prepareStatement("SELECT * FROM user_settings WHERE user_id=?")) {
                select.setLong(1, owner);
                try (var rows = select.executeQuery()) {
                    if (!rows.next()) return Preferences.defaults(defaultLanguage);
                    boolean hq = rows.getBoolean("quality");
                    String vmRaw = appSetting(connection, "user:" + owner + ":view_mode");
                    String gqRaw = appSetting(connection, "user:" + owner + ":graphics_quality");
                    Preferences.ViewMode viewMode = vmRaw == null ? Preferences.ViewMode.ROYAL_FOCUS_25D
                            : Preferences.ViewMode.valueOf(vmRaw);
                    Preferences.GraphicsQuality quality = gqRaw == null
                            ? (hq ? Preferences.GraphicsQuality.HIGH : Preferences.GraphicsQuality.MEDIUM)
                            : Preferences.GraphicsQuality.valueOf(gqRaw);
                    return new Preferences(rows.getString("language"), rows.getString("theme_id"), rows.getBoolean("sound"),
                            rows.getBoolean("animation"), Preferences.Orientation.valueOf(rows.getString("orientation")),
                            rows.getInt("piece_scale"), hq, rows.getBoolean("autosave"), viewMode, quality);
                }
            }
        });
    }

    @Override public void settings(long owner, Preferences prefs) {
        db.write(connection -> {
            if (!prefs.theme().equals("royal")) {
                try (var theme = connection.prepareStatement("SELECT 1 FROM user_themes WHERE user_id=? AND theme_id=?")) {
                    theme.setLong(1, owner);
                    theme.setString(2, prefs.theme());
                    try (var rows = theme.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Theme is locked");
                    }
                }
            }
            try (var save = connection.prepareStatement("""
                    INSERT INTO user_settings VALUES(?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(user_id) DO UPDATE SET language=excluded.language,theme_id=excluded.theme_id,
                    sound=excluded.sound,animation=excluded.animation,orientation=excluded.orientation,
                    piece_scale=excluded.piece_scale,quality=excluded.quality,autosave=excluded.autosave
                    """)) {
                save.setLong(1, owner);
                save.setString(2, prefs.language());
                save.setString(3, prefs.theme());
                save.setBoolean(4, prefs.sound());
                save.setBoolean(5, prefs.animation());
                save.setString(6, prefs.orientation().name());
                save.setInt(7, prefs.pieceScale());
                save.setBoolean(8, prefs.highQuality());
                save.setBoolean(9, prefs.autosave());
                save.executeUpdate();
            }
            saveAppSetting(connection, "user:" + owner + ":view_mode", prefs.viewMode().name());
            saveAppSetting(connection, "user:" + owner + ":graphics_quality", prefs.graphicsQuality().name());
            return null;
        });
    }

    @Override public String avatar(long owner) {
        return db.read(connection -> appSetting(connection, "user:" + owner + ":avatar"));
    }

    @Override public void avatar(long owner, String avatarId) {
        db.write(connection -> {
            saveAppSetting(connection, "user:" + owner + ":avatar", avatarId);
            return null;
        });
    }

    private static String appSetting(Connection connection, String key) throws SQLException {
        try (var select = connection.prepareStatement("SELECT value FROM app_settings WHERE key=?")) {
            select.setString(1, key);
            try (var rows = select.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static void saveAppSetting(Connection connection, String key, String value) throws SQLException {
        try (var save = connection.prepareStatement("""
                INSERT INTO app_settings VALUES(?,?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value
                """)) {
            save.setString(1, key);
            save.setString(2, value);
            save.executeUpdate();
        }
    }

    @Override public String rememberedUsername() {
        return db.read(connection -> {
            try (var select = connection.prepareStatement("SELECT value FROM app_settings WHERE key='remembered_username'");
                 var rows = select.executeQuery()) {
                return rows.next() ? rows.getString(1) : "";
            }
        });
    }

    @Override public void rememberUsername(String username) {
        db.write(connection -> {
            try (var save = connection.prepareStatement("""
                    INSERT INTO app_settings VALUES('remembered_username',?)
                    ON CONFLICT(key) DO UPDATE SET value=excluded.value
                    """)) {
                save.setString(1, username);
                save.executeUpdate();
            }
            return null;
        });
    }

    @Override public int recordPuzzle(long owner, String puzzleId, boolean solved, boolean hinted) {
        return db.write(connection -> {
            int points = 0;
            if (solved && !hinted) {
                try (var select = connection.prepareStatement("""
                        SELECT points FROM puzzles p WHERE p.id=? AND NOT EXISTS
                        (SELECT 1 FROM puzzle_attempts a WHERE a.puzzle_id=p.id AND a.user_id=? AND a.points>0)
                        """)) {
                    select.setString(1, puzzleId);
                    select.setLong(2, owner);
                    try (var rows = select.executeQuery()) { if (rows.next()) points = rows.getInt(1); }
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO puzzle_attempts(user_id,puzzle_id,solved,hinted,points,attempted) VALUES(?,?,?,?,?,?)")) {
                insert.setLong(1, owner);
                insert.setString(2, puzzleId);
                insert.setBoolean(3, solved);
                insert.setBoolean(4, hinted);
                insert.setInt(5, points);
                insert.setString(6, INSTANT.format(Instant.now()));
                insert.executeUpdate();
            }
            if (points > 0) {
                try (var update = connection.prepareStatement("UPDATE player_statistics SET puzzle_points=puzzle_points+? WHERE user_id=?")) {
                    update.setInt(1, points);
                    update.setLong(2, owner);
                    update.executeUpdate();
                }
                try (var select = connection.prepareStatement("SELECT count(*) FROM puzzle_attempts WHERE user_id=? AND points>0")) {
                    select.setLong(1, owner);
                    try (var rows = select.executeQuery()) {
                        if (rows.next() && rows.getInt(1) >= 3) award(connection, owner, "puzzle-master");
                    }
                }
            }
            return points;
        });
    }

    @Override public List<PuzzleAttempt> puzzleHistory(long owner) {
        return db.read(connection -> {
            try (var select = connection.prepareStatement("SELECT * FROM puzzle_attempts WHERE user_id=? ORDER BY id DESC")) {
                select.setLong(1, owner);
                try (var rows = select.executeQuery()) {
                    var attempts = new ArrayList<PuzzleAttempt>();
                    while (rows.next()) attempts.add(new PuzzleAttempt(rows.getString("puzzle_id"), rows.getBoolean("solved"),
                            rows.getBoolean("hinted"), rows.getInt("points"), Instant.parse(rows.getString("attempted"))));
                    return List.copyOf(attempts);
                }
            }
        });
    }
}
