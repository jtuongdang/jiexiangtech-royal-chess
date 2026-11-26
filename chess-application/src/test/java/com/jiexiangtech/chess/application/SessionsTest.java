package com.jiexiangtech.chess.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Side;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionsTest {
    @Test void autosaveRestoreRetainsRepetitionAndPausesOfflineTime() {
        AtomicLong now = new AtomicLong();
        MatchOptions options = new MatchOptions(MatchOptions.Mode.LOCAL, Side.WHITE, MatchOptions.Level.EASY,
                60_000, 2000, "royal");
        var session = GameSession.start(9, options, now::get);
        session.resume();
        for (int n = 0; n < 2; n++) {
            for (String move : "g1f3 g8f6 f3g1 f6g8".split(" ")) {
                now.addAndGet(3_000_000_000L);
                assertTrue(session.play(Move.uci(move)));
            }
        }
        session.pause();
        MatchSnapshot snapshot = session.snapshot();
        assertEquals(56_000, snapshot.times().whiteMillis());
        assertEquals(24_000, snapshot.elapsedMillis());
        now.addAndGet(900_000_000_000L);
        var restored = GameSession.restore(snapshot, now::get);
        restored.resume();
        assertEquals(snapshot.times(), restored.times());
        assertEquals(Outcome.Reason.THREEFOLD, restored.game().claimable(null));
        restored.undo();
        assertEquals(7, restored.game().plyCount());
        assertTrue(restored.snapshot().assisted());
        assertEquals(57_000, restored.times().blackMillis());
    }

    @Test void credentialsAreValidatedAndClearedOnBothPaths() {
        ChessStore store = mock(ChessStore.class);
        Passwords passwords = mock(Passwords.class);
        var accounts = new Accounts(store, passwords);
        var user = new ChessStore.User(2, "ivory", "Ivory");
        when(passwords.hash(any(char[].class))).thenReturn("encoded");
        when(store.register("ivory", "Ivory", "encoded")).thenReturn(user);
        char[] password = "medieval42".toCharArray(), confirmation = password.clone();
        assertEquals(user, accounts.register(" IVORY ", " Ivory ", password, confirmation));
        assertArrayEquals(new char[10], password);
        assertArrayEquals(new char[10], confirmation);
        when(store.authenticate(eq("ivory"), any(char[].class), eq(passwords))).thenReturn(Optional.of(user));
        char[] login = "medieval42".toCharArray();
        assertEquals(user, accounts.signIn("Ivory", login, true).orElseThrow());
        verify(store).rememberUsername("ivory");
        assertArrayEquals(new char[10], login);
        char[] invalid = "short".toCharArray();
        assertEquals(Accounts.Problem.PASSWORD,
                assertThrows(Accounts.Invalid.class, () -> accounts.register("ivory", "Ivory", invalid, invalid.clone())).problem());
        assertArrayEquals(new char[5], invalid);
    }

    @Test void everyPuzzleHasOneVerifiedSolution() {
        assertEquals(4, Puzzles.catalog().size());
        for (var puzzle : Puzzles.catalog()) {
            var game = puzzle.game();
            assertTrue(Puzzles.solve(game, puzzle.solution()), puzzle.id());
            assertEquals(Outcome.Reason.CHECKMATE, game.outcome().reason());
        }
    }
}
