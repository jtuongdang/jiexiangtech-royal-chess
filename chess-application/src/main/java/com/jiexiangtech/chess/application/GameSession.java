package com.jiexiangtech.chess.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.LongSupplier;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.GameClock;
import com.jiexiangtech.chess.domain.GameClock.Times;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Rules;
import com.jiexiangtech.chess.domain.Side;

public final class GameSession {
    private final UUID id;
    private final long owner;
    private final MatchOptions options;
    private final Instant started;
    private final Game game;
    private final GameClock clock;
    private final LongSupplier nanos;
    private final ArrayList<Times> checkpoints = new ArrayList<>();
    private boolean assisted, running;
    private long elapsedMillis, startedNanos;

    private GameSession(UUID id, long owner, MatchOptions options, Instant started, Game game, Times times, LongSupplier nanos) {
        this.id = id;
        this.owner = owner;
        this.options = options;
        this.started = started;
        this.game = game;
        this.nanos = nanos;
        clock = new GameClock(options.timed(), options.incrementMillis(), times, nanos);
        checkpoints.add(new Times(options.baseMillis(), options.baseMillis()));
    }

    public static GameSession start(long owner, MatchOptions options, LongSupplier nanos) {
        Times initial = new Times(options.baseMillis(), options.baseMillis());
        return new GameSession(UUID.randomUUID(), owner, options, Instant.now(), new Game(Position.initial()), initial, nanos);
    }

    public static GameSession restore(MatchSnapshot saved, LongSupplier nanos) {
        var game = new Game(Position.fromFen(saved.initialFen()));
        var session = new GameSession(saved.id(), saved.owner(), saved.options(), saved.started(), game, saved.times(), nanos);
        for (var entry : saved.moves()) {
            var move = game.play(Move.uci(entry.uci()));
            if (!move.san().equals(entry.san())) throw new IllegalArgumentException("Inconsistent saved SAN");
            session.checkpoints.add(entry.times());
        }
        if (!game.position().fen().equals(saved.currentFen())) throw new IllegalArgumentException("Inconsistent saved FEN");
        game.restoreOutcome(saved.outcome());
        session.assisted = saved.assisted();
        session.elapsedMillis = saved.elapsedMillis();
        return session;
    }

    public Game game() { return game; }
    public MatchOptions options() { return options; }
    public Times times() { return clock.read(); }
    public boolean computerTurn() { return options.mode() == MatchOptions.Mode.COMPUTER && game.position().turn() != options.human(); }
    public void markAssisted() { assisted = true; }
    public long elapsedMillis() { return elapsedMillis + (running ? (nanos.getAsLong() - startedNanos) / 1_000_000 : 0); }

    public void resume() {
        if (running || game.outcome() != null) return;
        running = true;
        startedNanos = nanos.getAsLong();
        clock.start(game.position().turn());
    }

    public void pause() {
        if (!running) return;
        checkTimeout();
        if (running) stop();
    }

    private void stop() {
        elapsedMillis = elapsedMillis();
        running = false;
        clock.pause();
    }

    public boolean checkTimeout() {
        if (!running || game.outcome() != null || !clock.expired()) return false;
        game.timeout(game.position().turn());
        stop();
        return true;
    }

    public boolean play(Move move) {
        if (checkTimeout() || !running || game.outcome() != null) return false;
        if (!Rules.legalMoves(game.position(), move.from()).contains(move)) throw new IllegalArgumentException("Illegal move");
        Side mover = game.position().turn();
        stop();
        if (options.timed() && clock.read().get(mover) == 0) {
            game.timeout(mover);
            return false;
        }
        game.play(move);
        clock.addIncrement(mover);
        checkpoints.add(clock.read());
        resume();
        return true;
    }

    public void undo() {
        if (checkTimeout() || !running || game.plyCount() == 0 || game.outcome() != null) return;
        int count = options.mode() == MatchOptions.Mode.LOCAL || computerTurn() ? 1 : 2;
        if (game.plyCount() < count) return;
        stop();
        for (int i = 0; i < count; i++) {
            game.undo();
            checkpoints.remove(checkpoints.size() - 1);
        }
        assisted = true;
        clock.restore(checkpoints.get(checkpoints.size() - 1));
        resume();
    }

    public boolean canUndo() {
        int needed = options.mode() == MatchOptions.Mode.COMPUTER && !computerTurn() ? 2 : 1;
        return game.outcome() == null && game.plyCount() >= needed;
    }

    public void resign() {
        if (checkTimeout()) return;
        game.resign(options.mode() == MatchOptions.Mode.LOCAL ? game.position().turn() : options.human());
        stop();
    }

    public void agreeDraw() {
        if (checkTimeout()) return;
        game.agreeDraw();
        stop();
    }

    public void claimDraw(Move intended) {
        if (checkTimeout()) return;
        game.claimDraw(intended);
        stop();
    }

    public MatchSnapshot snapshot() {
        var moves = new ArrayList<MatchSnapshot.Ply>();
        int index = 1;
        for (Game.Ply ply : game.moves()) {
            moves.add(new MatchSnapshot.Ply(ply.move().uci(), ply.san(), checkpoints.get(index++)));
        }
        return new MatchSnapshot(id, owner, options, started, Instant.now(), game.initial().fen(), game.position().fen(),
                moves, times(), game.outcome(), assisted, elapsedMillis());
    }
}
