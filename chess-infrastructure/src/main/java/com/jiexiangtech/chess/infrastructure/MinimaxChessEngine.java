package com.jiexiangtech.chess.infrastructure;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.jiexiangtech.chess.application.ChessEngine;
import com.jiexiangtech.chess.domain.*;

public class MinimaxChessEngine implements ChessEngine {
    private static final int INFINITY = 100_000;
    private static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded() { super(null, null, false, false); }
    }
    private final int maxDepth;
    private final boolean pruning;
    private final int captureExtension;

    public MinimaxChessEngine() { this(3, false, 0); }

    protected MinimaxChessEngine(int maxDepth, boolean pruning, int captureExtension) {
        this.maxDepth = maxDepth;
        this.pruning = pruning;
        this.captureExtension = captureExtension;
    }

    @Override public Optional<Analysis> analyze(Position p, Duration budget) {
        var moves = Rules.legalMoves(p);
        if (moves.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
        order(p, moves, null);
        Search search = new Search(System.nanoTime() + Math.max(0, budget.toNanos()));
        Move first = moves.get(0);
        Position next = p.afterGeneratedMove(first);
        Analysis best = new Analysis(first, -search.evaluate(next, Rules.legalMoves(next).size()), 0, 1);
        for (int depth = 1; depth <= maxDepth; depth++) {
            order(p, moves, best.move());
            int score = -INFINITY;
            Move choice = first;
            try {
                for (Move move : moves) {
                    int result = -search.negamax(p.afterGeneratedMove(move), depth - 1,
                            -INFINITY, pruning ? -score : INFINITY, 1);
                    if (result > score) { score = result; choice = move; }
                }
                best = new Analysis(choice, score, depth, search.nodes);
                if (Math.abs(score) > 29_000) break;
            } catch (BudgetExceeded stop) {
                break;
            }
        }
        return Thread.currentThread().isInterrupted() ? Optional.empty() : Optional.of(best);
    }

    private static void order(Position p, List<Move> moves, Move preferred) {
        moves.sort(Comparator.comparingInt((Move move) -> {
            if (move.equals(preferred)) return 100_000;
            Piece victim = p.captured(move);
            return (victim == null ? 0 : victim.kind().value * 10 - p.piece(move.from()).kind().value)
                    + (move.promotion() == null ? 0 : move.promotion().value);
        }).reversed());
    }

    private final class Search {
        private final long deadline;
        private final PositionEvaluator evaluator = new PositionEvaluator();
        private long nodes;

        Search(long deadline) { this.deadline = deadline; }

        int evaluate(Position p, int mobility) { return evaluator.evaluate(p, mobility); }

        int negamax(Position p, int depth, int alpha, int beta, int ply) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() - deadline >= 0) throw new BudgetExceeded();
            nodes++;
            List<Move> moves = Rules.legalMoves(p);
            boolean check = Rules.inCheck(p, p.turn());
            if (moves.isEmpty()) return check ? -30_000 + ply : 0;
            if (Rules.dead(p) || p.halfmove() >= 150) return 0;
            int best = -INFINITY;
            if (depth <= 0) {
                if (depth <= -captureExtension) return evaluate(p, moves.size());
                if (!check) {
                    best = evaluate(p, moves.size());
                    if (best >= beta) return best;
                    alpha = Math.max(alpha, best);
                    moves.removeIf(move -> p.captured(move) == null && move.promotion() == null);
                }
            }
            if (pruning) order(p, moves, null);
            for (Move move : moves) {
                int score = -negamax(p.afterGeneratedMove(move), depth - 1, -beta, -alpha, ply + 1);
                best = Math.max(best, score);
                if (pruning) {
                    alpha = Math.max(alpha, best);
                    if (alpha >= beta) break;
                }
            }
            return best;
        }
    }
}
