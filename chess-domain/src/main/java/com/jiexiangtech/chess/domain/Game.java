package com.jiexiangtech.chess.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.jiexiangtech.chess.domain.Outcome.Reason.*;

public final class Game {
    public record Ply(Move move, String san, Piece captured, Position after) {}

    private final Position initial;
    private final ArrayList<Ply> moves = new ArrayList<>();
    private final Map<String, Integer> repetitions = new HashMap<>();
    private Outcome outcome;

    public Game(Position initial) {
        this.initial = Objects.requireNonNull(initial);
        repetitions.put(initial.repetitionKey(), 1);
        outcome = automaticResult();
    }

    public Position initial() { return initial; }
    public Position position() { return positionAt(moves.size()); }
    public List<Ply> moves() { return List.copyOf(moves); }
    public int plyCount() { return moves.size(); }
    public Outcome outcome() { return outcome; }
    public Position positionAt(int ply) { return ply == 0 ? initial : moves.get(ply - 1).after(); }

    public Ply play(Move move) {
        requireActive();
        Position before = position();
        List<Move> legal = Rules.legalMoves(before);
        if (!legal.contains(move)) throw new IllegalArgumentException("Illegal move: " + move.uci());
        Position next = before.afterGeneratedMove(move);
        Ply ply = new Ply(move, San.format(before, move, legal), before.captured(move), next);
        moves.add(ply);
        repetitions.merge(next.repetitionKey(), 1, Integer::sum);
        outcome = automaticResult();
        return ply;
    }

    public void undo() {
        if (moves.isEmpty()) throw new IllegalStateException("No move to undo");
        repetitions.compute(position().repetitionKey(), (key, count) -> count == 1 ? null : count - 1);
        moves.remove(moves.size() - 1);
        outcome = automaticResult();
    }

    public Outcome.Reason claimable(Move intended) {
        if (outcome != null) return null;
        Position next = position();
        if (intended != null) {
            if (!Rules.legalMoves(next, intended.from()).contains(intended)) return null;
            next = next.afterGeneratedMove(intended);
        }
        int appearances = repetitions.getOrDefault(next.repetitionKey(), 0) + (intended == null ? 0 : 1);
        if (appearances >= 3) return THREEFOLD;
        return next.halfmove() >= 100 ? FIFTY_MOVE : null;
    }

    public void claimDraw(Move intended) {
        Outcome.Reason reason = claimable(intended);
        if (reason == null) throw new IllegalStateException("Draw claim is unavailable");
        outcome = new Outcome(reason, null);
    }

    public void agreeDraw() {
        requireActive();
        if (moves.size() < 2) throw new IllegalStateException("Both sides must make a move");
        outcome = new Outcome(AGREEMENT, null);
    }

    public void resign(Side side) { finishByForfeit(RESIGNATION, side); }
    public void timeout(Side side) {
        if (side != position().turn()) throw new IllegalArgumentException("Only the active clock can expire");
        finishByForfeit(TIMEOUT, side);
    }

    private void finishByForfeit(Outcome.Reason reason, Side side) {
        requireActive();
        Side opponent = side.opposite();
        outcome = new Outcome(reason, Rules.canPossiblyMate(position(), opponent) ? opponent : null);
    }

    public void restoreOutcome(Outcome saved) {
        if (Objects.equals(outcome, saved)) return;
        requireActive();
        if (saved == null || switch (saved.reason()) {
            case RESIGNATION, TIMEOUT, AGREEMENT, THREEFOLD, FIFTY_MOVE -> false;
            default -> true;
        }) throw new IllegalArgumentException("Saved result does not match moves");
        outcome = saved;
    }

    private Outcome automaticResult() {
        Position p = position();
        if (Rules.legalMoves(p).isEmpty()) {
            return Rules.inCheck(p, p.turn()) ? new Outcome(CHECKMATE, p.turn().opposite()) : new Outcome(STALEMATE, null);
        }
        if (Rules.dead(p)) return new Outcome(DEAD_POSITION, null);
        if (repetitions.get(p.repetitionKey()) >= 5) return new Outcome(FIVEFOLD, null);
        if (p.halfmove() >= 150) return new Outcome(SEVENTY_FIVE_MOVE, null);
        return null;
    }

    private void requireActive() {
        if (outcome != null) throw new IllegalStateException("Game has ended");
    }
}
