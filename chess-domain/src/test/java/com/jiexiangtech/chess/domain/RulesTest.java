package com.jiexiangtech.chess.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @ParameterizedTest @CsvSource({"1,20", "2,400", "3,8902", "4,197281"})
    void initialPerft(int depth, long expected) { assertEquals(expected, Rules.perft(Position.initial(), depth)); }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1|3|97862",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1|4|43238",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1|3|9467"
    })
    void specialPerft(String fen, int depth, long expected) {
        assertEquals(expected, Rules.perft(Position.fromFen(fen), depth));
    }

    @Test void enPassantDiscoveredCheckAndRepetitionKey() {
        Position p = Position.fromFen("4k3/8/8/4KPpr/8/8/8/8 w - g6 0 1");
        assertFalse(Rules.legalMoves(p).contains(Move.uci("f5g6")));
        assertTrue(p.repetitionKey().endsWith(" -"));
        Game game = new Game(Position.initial());
        play(game, "e2e4 a7a6 e4e5 d7d5 e5d6");
        assertNull(game.position().piece(Square.parse("d5")));
        assertEquals("exd6", game.moves().get(4).san());
        game.undo();
        assertEquals(Square.parse("d6"), game.position().enPassant());
    }

    @Test void castlingAndPromotion() {
        Game game = new Game(Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"));
        play(game, "e1c1");
        assertEquals("O-O-O", game.moves().get(0).san());
        assertEquals(Kind.ROOK, game.position().piece(Square.parse("d1")).kind());
        game.undo();
        assertTrue(game.position().fen().contains(" KQkq "));
        Position attacked = Position.fromFen("1k3r2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertFalse(Rules.legalMoves(attacked).contains(Move.uci("e1g1")));
        Position promotion = Position.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1");
        assertEquals(4, Rules.legalMoves(promotion, Square.parse("a7")).size());
        assertEquals("a8=Q+", San.format(promotion, Move.uci("a7a8q")));
    }

    @Test void noIllegalOrPostgameMoves() {
        Game game = new Game(Position.initial());
        assertThrows(IllegalArgumentException.class, () -> play(game, "e7e5"));
        assertThrows(IllegalArgumentException.class, () -> play(game, "e2e5"));
        play(game, "f2f3 e7e5 g2g4 d8h4");
        assertEquals(Outcome.Reason.CHECKMATE, game.outcome().reason());
        assertEquals(Side.BLACK, game.outcome().winner());
        assertEquals("Qh4#", game.moves().get(3).san());
        assertThrows(IllegalStateException.class, () -> play(game, "e2e3"));
        game.undo();
        assertNull(game.outcome());
    }

    @Test void automaticAndClaimableDraws() {
        String loop = "g1f3 g8f6 f3g1 f6g8";
        Game game = new Game(Position.initial());
        play(game, loop + " " + loop + " g1f3 g8f6 f3g1");
        assertEquals(Outcome.Reason.THREEFOLD, game.claimable(Move.uci("f6g8")));
        play(game, "f6g8");
        assertEquals(Outcome.Reason.THREEFOLD, game.claimable(null));
        assertNull(game.outcome());
        play(game, loop);
        assertEquals(Outcome.Reason.FIVEFOLD, game.outcome().reason());

        Game clockRule = new Game(Position.fromFen("r6k/8/8/8/8/8/8/R6K w - - 99 42"));
        assertEquals(Outcome.Reason.FIFTY_MOVE, clockRule.claimable(Move.uci("a1b1")));
        clockRule.claimDraw(Move.uci("a1b1"));
        assertEquals(0, clockRule.plyCount());
        Game automatic = new Game(Position.fromFen("r6k/8/8/8/8/8/8/R6K w - - 149 82"));
        play(automatic, "a1b1");
        assertEquals(Outcome.Reason.SEVENTY_FIVE_MOVE, automatic.outcome().reason());
        assertEquals(Outcome.Reason.STALEMATE, new Game(Position.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")).outcome().reason());
        assertEquals(Outcome.Reason.DEAD_POSITION, new Game(Position.fromFen("7k/8/8/8/8/8/2B5/7K w - - 0 1")).outcome().reason());
    }

    @Test void recognizesASealedPawnWallWithoutCallingOpenPawnEndingsDead() {
        Position sealed = Position.fromFen("7k/8/8/8/p1p1p1p1/PpPpPpPp/1P1P1P1P/K7 w - - 0 1");
        assertEquals(Outcome.Reason.DEAD_POSITION, new Game(sealed).outcome().reason());
        Position open = Position.fromFen("7k/8/8/8/p1p1p1p1/PpPpPpP1/1P1P1P1P/K7 w - - 0 1");
        assertFalse(Rules.dead(open));
    }

    @Test void fenSanAndPgnRoundTrip() {
        assertEquals(Position.INITIAL_FEN, Position.initial().fen());
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("8/8/8/8/8/8/8/8 w - - 0 1"));
        Game game = new Game(Position.initial());
        play(game, "e2e4 e7e5 g1f3 b8c6 f1b5");
        String text = Pgn.export(game, "Local game", "Ivory \"A\"", "Onyx", "2026.11.26");
        Pgn.Imported imported = Pgn.parse(text);
        assertEquals("Ivory \"A\"", imported.tags().get("White"));
        assertEquals(game.position().fen(), imported.game().position().fen());
        assertEquals(5, Pgn.parse("1.e4 {note} e5 (1...c5) 2.Nf3 $1 Nc6 3.Bb5 *").game().plyCount());
        assertEquals("Nbd2", San.format(Position.fromFen("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1"), Move.uci("b1d2")));
    }

    @Test void timeoutAndClockPause() {
        var time = new AtomicLong();
        var clock = new GameClock(true, 2000, new GameClock.Times(60000, 60000), time::get);
        clock.start(Side.WHITE);
        time.set(4_000_000_000L);
        clock.pause();
        clock.addIncrement(Side.WHITE);
        time.set(100_000_000_000L);
        assertEquals(58000, clock.read().whiteMillis());
        clock.start(Side.BLACK);
        time.set(161_000_000_000L);
        assertTrue(clock.expired());
        Game game = new Game(Position.fromFen("7k/8/8/8/8/8/2R5/7K w - - 0 1"));
        game.timeout(Side.WHITE);
        assertNull(game.outcome().winner());
        assertEquals(Outcome.Reason.TIMEOUT, game.outcome().reason());
    }

    private static void play(Game game, String moves) {
        for (String move : moves.split(" ")) game.play(Move.uci(move));
    }
}
