package com.jiexiangtech.chess.infrastructure;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import com.jiexiangtech.chess.application.ChessEngine;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Rules;

public final class RandomChessEngine implements ChessEngine {
    @Override public Optional<Analysis> analyze(Position p, Duration budget) {
        var moves = Rules.legalMoves(p);
        if (moves.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
        var move = moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
        var next = p.afterGeneratedMove(move);
        int estimate = -new PositionEvaluator().evaluate(next, Rules.legalMoves(next).size());
        return Optional.of(new Analysis(move, estimate, 0, 1));
    }
}
