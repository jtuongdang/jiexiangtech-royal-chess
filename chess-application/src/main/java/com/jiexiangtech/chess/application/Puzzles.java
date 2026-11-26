package com.jiexiangtech.chess.application;

import java.util.List;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Rules;

public final class Puzzles {
    public record Puzzle(String id, MatchOptions.Level level, String fen, Move solution, int points) {
        public Puzzle {
            var position = Position.fromFen(fen);
            List<Move> mates = Rules.legalMoves(position).stream().filter(move -> {
                Position next = position.afterGeneratedMove(move);
                return Rules.inCheck(next, next.turn()) && Rules.legalMoves(next).isEmpty();
            }).toList();
            if (!mates.equals(List.of(solution))) throw new IllegalArgumentException("Puzzle must have a unique mate: " + id);
        }
        public Game game() { return new Game(Position.fromFen(fen)); }
    }

    private static final List<Puzzle> CATALOG = List.of(
        new Puzzle("back-rank", MatchOptions.Level.EASY, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", Move.uci("a1a8"), 10),
        new Puzzle("ivory-crown", MatchOptions.Level.MEDIUM, "7k/8/5KQ1/8/8/8/8/8 w - - 0 1", Move.uci("g6g7"), 20),
        new Puzzle("onyx-crown", MatchOptions.Level.MEDIUM, "8/8/8/8/8/1qk5/8/K7 b - - 0 1", Move.uci("b3b2"), 20),
        new Puzzle("underpromotion", MatchOptions.Level.HARD, "5N2/4B1P1/5K1k/R7/8/8/8/8 w - - 0 1", Move.uci("g7g8n"), 30)
    );

    private Puzzles() {}
    public static List<Puzzle> catalog() { return CATALOG; }

    public static boolean solve(Game game, Move move) {
        if (!Rules.legalMoves(game.position(), move.from()).contains(move)) return false;
        Position next = game.position().afterGeneratedMove(move);
        if (!Rules.inCheck(next, next.turn()) || !Rules.legalMoves(next).isEmpty()) return false;
        return game.play(move) != null && game.outcome().reason() == Outcome.Reason.CHECKMATE;
    }
}
