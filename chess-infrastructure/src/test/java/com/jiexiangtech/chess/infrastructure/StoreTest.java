package com.jiexiangtech.chess.infrastructure;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.jiexiangtech.chess.application.*;
import com.jiexiangtech.chess.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @TempDir Path directory;

    private Path file() { return directory.resolve("chessmaster.db"); }

    @Test void createsAndReopensVersionedDatabase() throws Exception {
        var store = new SqliteChessStore(file());
        assertEquals(ChessStore.GUEST_ID, store.profile(ChessStore.GUEST_ID).user().id());
        var reopened = new SqliteChessStore(file());
        assertEquals(List.of(), reopened.history(ChessStore.GUEST_ID));
        try (var connection = new Database(file()).connect(); var query = connection.createStatement()) {
            try (var rows = query.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            try (var rows = query.executeQuery("SELECT count(*),max(version) FROM schema_migrations")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
            }
        }
    }

    @Test void authenticationUsesUniqueSaltAndPersistentRememberedUsername() {
        var store = new SqliteChessStore(file());
        var passwords = new Pbkdf2Passwords();
        char[] secret = "castling-2026".toCharArray();
        String first = passwords.hash(secret), second = passwords.hash(secret);
        assertNotEquals(first, second);
        assertTrue(passwords.matches(secret, first));
        assertFalse(passwords.matches("invalid".toCharArray(), first));
        var accounts = new Accounts(store, passwords);
        var player = accounts.register("player", "Player", secret.clone(), secret.clone());
        assertThrows(ChessStore.DuplicateUsernameException.class,
                () -> accounts.register("PLAYER", "Duplicate", secret.clone(), secret.clone()));
        assertTrue(accounts.signIn("player", "invalid".toCharArray(), true).isEmpty());
        assertEquals(player, accounts.signIn("player", secret.clone(), true).orElseThrow());
        assertEquals("player", new SqliteChessStore(file()).rememberedUsername());
        assertEquals(List.of(player), store.leaderboard().stream().map(ChessStore.Profile::user).toList());
    }

    @Test void avatarIsPerProfileAndSurvivesReopeningAnExistingDatabase() {
        var store = new SqliteChessStore(file());
        var player = store.register("knight", "Royal Knight", new Pbkdf2Passwords().hash("testpass".toCharArray()));
        assertNull(store.avatar(player.id()));
        store.avatar(player.id(), "knight");
        store.avatar(ChessStore.GUEST_ID, "sage");
        store.settings(player.id(), Preferences.defaults("vi"));

        var reopened = new SqliteChessStore(file());
        assertEquals("knight", reopened.avatar(player.id()));
        assertEquals("sage", reopened.avatar(ChessStore.GUEST_ID));
    }

    @Test void moveAndHeaderRollbackTogetherThenRestoreAfterRetry() throws Exception {
        var store = new SqliteChessStore(file());
        long owner = ChessStore.GUEST_ID;
        var game = newGame(owner, MatchOptions.Mode.LOCAL, Side.WHITE);
        assertTrue(game.play(Move.uci("e2e4")));
        var saved = game.snapshot();
        store.save(saved);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file()); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER test_fail BEFORE INSERT ON game_moves WHEN NEW.ply=2 BEGIN SELECT RAISE(ABORT,'forced failure'); END");
            assertTrue(game.play(Move.uci("e7e5")));
            var attempted = game.snapshot();
            assertThrows(ChessStore.StorageException.class, () -> store.save(attempted));
            assertEquals(saved, store.load(owner, saved.id()).orElseThrow());
            statement.execute("DROP TRIGGER test_fail");
            store.save(attempted);
            assertEquals(attempted, store.load(owner, saved.id()).orElseThrow());
            var restored = GameSession.restore(attempted, System::nanoTime);
            assertEquals("e6", Square.name(restored.game().position().enPassant()));
            assertTrue(restored.game().position().fen().contains(" KQkq "));
            assertEquals(Side.WHITE, restored.game().position().turn());
            restored.resume();
            restored.undo();
            store.save(restored.snapshot());
            assertEquals(1, store.load(owner, saved.id()).orElseThrow().moves().size());
            assertTrue(store.load(999, saved.id()).isEmpty());
        }
    }

    @Test void completedGameRewardsOnceAndAssistedGamesNeverChangeRating() {
        var store = new SqliteChessStore(file());
        var player = store.register("onyx", "Onyx", "encoded");
        var game = newGame(player.id(), MatchOptions.Mode.COMPUTER, Side.BLACK);
        for (String move : "f2f3 e7e5 g2g4 d8h4".split(" ")) assertTrue(game.play(Move.uci(move)));
        var result = game.snapshot();
        assertEquals(ChessStore.SaveResult.SAVED, store.save(result));
        assertEquals(ChessStore.SaveResult.ALREADY_FINISHED, store.save(result));
        var profile = store.profile(player.id());
        assertEquals(1, profile.wins());
        assertTrue(profile.rating() > 1200);
        assertTrue(profile.achievements().containsAll(List.of("first-victory", "checkmate-master")));
        var assisted = newGame(player.id(), MatchOptions.Mode.COMPUTER, Side.BLACK);
        assisted.markAssisted();
        for (String move : "f2f3 e7e5 g2g4 d8h4".split(" ")) assisted.play(Move.uci(move));
        store.save(assisted.snapshot());
        assertEquals(profile.rating(), store.profile(player.id()).rating());
        assertEquals(2, store.profile(player.id()).wins());
    }

    @Test void puzzleRewardsAreIdempotentAndUnlockASelectableTheme() {
        var store = new SqliteChessStore(file());
        long owner = ChessStore.GUEST_ID;
        var slate = new Preferences("vi", "royal-slate", false, false, Preferences.Orientation.BLACK, 88, false, false);
        assertThrows(ChessStore.StorageException.class, () -> store.settings(owner, slate));
        assertEquals(0, store.recordPuzzle(owner, "back-rank", true, true));
        assertEquals(10, store.recordPuzzle(owner, "back-rank", true, false));
        assertEquals(0, store.recordPuzzle(owner, "back-rank", true, false));
        assertEquals(20, store.recordPuzzle(owner, "ivory-crown", true, false));
        assertEquals(20, store.recordPuzzle(owner, "onyx-crown", true, false));
        assertEquals(50, store.profile(owner).puzzlePoints());
        assertEquals(5, store.puzzleHistory(owner).size());
        store.settings(owner, slate);
        assertEquals(slate, store.settings(owner, "en"));
    }

    @Test void allEnginesChooseLegalMovesAndRespectAnAlreadyTerminalPosition() {
        var engines = List.of(new RandomChessEngine(), new MinimaxChessEngine(),
                new AlphaBetaChessEngine(false), new AlphaBetaChessEngine(true));
        Position position = Position.initial();
        Position stalemate = Position.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
        for (var engine : engines) {
            var result = engine.analyze(position, Duration.ofMillis(80)).orElseThrow();
            assertTrue(Rules.legalMoves(position).contains(result.move()));
            assertTrue(engine.analyze(stalemate, Duration.ofMillis(10)).isEmpty());
        }
    }

    private static GameSession newGame(long owner, MatchOptions.Mode mode, Side side) {
        var options = new MatchOptions(mode, side, MatchOptions.Level.MEDIUM, 120_000, 3000, "royal");
        var game = GameSession.start(owner, options, System::nanoTime);
        game.resume();
        return game;
    }
}
