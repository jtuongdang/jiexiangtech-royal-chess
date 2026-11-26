package com.jiexiangtech.chess.infrastructure;

import java.time.Duration;
import java.util.Optional;
import com.jiexiangtech.chess.application.ChessEngine;
import com.jiexiangtech.chess.application.MatchOptions.Level;

public final class ChessEngines {
    private final Optional<StockfishEngineAdapter> stockfish;

    public ChessEngines() { stockfish = StockfishEngineAdapter.discover(); }

    public ChessEngine forLevel(Level level) {
        return switch (level) {
            case EASY -> new RandomChessEngine();
            case MEDIUM -> new MinimaxChessEngine();
            case HARD -> new AlphaBetaChessEngine(false);
            case EXPERT -> (position, budget) -> {
                if (stockfish.isEmpty()) return new AlphaBetaChessEngine(true).analyze(position, budget);
                long begin = System.nanoTime();
                var result = stockfish.get().analyze(position, budget.multipliedBy(4).dividedBy(5));
                if (result.isPresent() || Thread.currentThread().isInterrupted()) return result;
                long remaining = Math.max(0, budget.toNanos() - (System.nanoTime() - begin));
                return new AlphaBetaChessEngine(true).analyze(position, Duration.ofNanos(remaining));
            };
        };
    }

    public boolean stockfishDetected() { return stockfish.isPresent(); }
}
