package com.jiexiangtech.chess.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.jiexiangtech.chess.domain.*;
import com.jiexiangtech.chess.online.OnlineProtocol.*;
import static com.jiexiangtech.chess.server.RoomDocument.*;
import static org.springframework.http.HttpStatus.*;

@Service
final class RoomService {
    private static final Pattern ROOM_ID = Pattern.compile("[A-Za-z0-9_-]{12}");
    private static final Pattern BEARER = Pattern.compile("Bearer ([A-Za-z0-9_-]{43})");
    private static final Pattern COMMAND_ID = Pattern.compile("[a-fA-F0-9-]{36}");
    private static final long WAIT_MS = Duration.ofMinutes(20).toMillis();
    private static final long KEEP_MS = Duration.ofDays(30).toMillis();

    private final RoomStore store;
    private final Clock clock;
    private final byte[] betaKey;

    @Autowired
    RoomService(RoomStore store, @Value("${chess.beta-key}") String key) {
        this(store, key, Clock.systemUTC());
    }

    RoomService(RoomStore store, String key, Clock clock) {
        if (key == null || key.strip().length() < 24) throw new IllegalArgumentException("CHESS_BETA_KEY requires at least 24 characters");
        this.store = store;
        this.clock = clock;
        this.betaKey = digest(key);
    }

    Snapshot create(Create request, String authorization, String organizerKey) {
        if (organizerKey == null || !MessageDigest.isEqual(betaKey, digest(organizerKey))) {
            throw new RoomProblem(FORBIDDEN, "organizer_key");
        }
        if (request == null || request.control() == null) throw bad();
        validateId(request.roomId());
        String hash = tokenHash(authorization);
        long now = clock.millis();
        long base = request.control().baseMillis;
        var room = new RoomDocument(request.roomId(), 0, request.control(), new Seat(name(request.name()), hash), null,
                new BoardState(Position.INITIAL_FEN, Side.WHITE, base, base, 0, null, null),
                java.util.List.of(), new Date(now + WAIT_MS));
        if (!store.insert(room)) {
            room = find(request.roomId());
            requireSeat(room, hash);
        }
        return snapshot(room, requireSeat(room, hash), now);
    }

    Snapshot join(String id, Join request, String authorization) {
        if (request == null) throw bad();
        Seat seat = new Seat(name(request.name()), tokenHash(authorization));
        RoomDocument current = find(id);
        Side existing = seatFor(current, seat.tokenHash());
        if (existing != null) return snapshot(current, existing, clock.millis());
        if (current.black() != null) throw new RoomProblem(CONFLICT, "room_full");
        long now = clock.millis();
        var next = new RoomDocument(id, current.version() + 1, current.control(), current.white(), seat,
                new BoardState(Position.INITIAL_FEN, Side.WHITE, current.control().baseMillis, current.control().baseMillis,
                        now + current.control().baseMillis, null, null), current.commands(), new Date(now + KEEP_MS));
        if (!store.replace(current.version(), next)) {
            RoomDocument latest = find(id);
            if (seatFor(latest, seat.tokenHash()) == Side.BLACK) return snapshot(latest, Side.BLACK, clock.millis());
            throw new RoomProblem(CONFLICT, "room_full");
        }
        return snapshot(next, Side.BLACK, now);
    }

    void authorize(String id, String authorization) { requireSeat(find(id), tokenHash(authorization)); }

    Snapshot view(String id, String authorization) {
        RoomDocument room = find(id);
        Side side = requireSeat(room, tokenHash(authorization));
        long now = clock.millis();
        return snapshot(expire(room, now), side, now);
    }

    Snapshot command(String id, Command request, String authorization) {
        if (request == null || request.action() == null || request.id() == null
                || !COMMAND_ID.matcher(request.id()).matches() || request.version() < 0) throw bad();
        String token = tokenHash(authorization);
        RoomDocument current = find(id);
        Side actor = requireSeat(current, token);
        long now = clock.millis();
        if (seen(current, actor, request)) return snapshot(current, actor, now);
        current = expire(current, now);
        BoardState state = current.board();
        if (state.outcome() != null) throw new RoomProblem(CONFLICT, "game_ended");
        if (current.black() == null) throw new RoomProblem(CONFLICT, "waiting");
        if (request.version() != current.version()) throw new RoomProblem(CONFLICT, "stale");
        var game = restore(current);
        Move move = null;
        String san = null;
        Side offer = state.drawOfferedBy();
        long white = remaining(state, Side.WHITE, now), black = remaining(state, Side.BLACK, now);
        long deadline = state.deadline();
        try {
            switch (request.action()) {
                case MOVE -> {
                    if (actor != state.turn()) throw new RoomProblem(CONFLICT, "wrong_turn");
                    move = parseMove(request.uci());
                    san = game.play(move).san();
                    if (actor == Side.WHITE) white += current.control().incrementMillis;
                    else black += current.control().incrementMillis;
                    deadline = now + (actor == Side.WHITE ? black : white);
                    if (offer == actor.opposite()) offer = null;
                }
                case RESIGN -> game.resign(actor);
                case OFFER_DRAW -> {
                    if (game.plyCount() < 2 || offer == actor) throw new RoomProblem(CONFLICT, "draw_unavailable");
                    if (offer == actor.opposite()) game.agreeDraw();
                    else offer = actor;
                }
                case ACCEPT_DRAW -> {
                    if (offer != actor.opposite()) throw new RoomProblem(CONFLICT, "draw_unavailable");
                    game.agreeDraw();
                }
                case CLAIM_DRAW -> {
                    if (actor != state.turn()) throw new RoomProblem(CONFLICT, "wrong_turn");
                    Move intended = request.uci() == null ? null : parseMove(request.uci());
                    if (game.claimable(intended) == null) throw new RoomProblem(CONFLICT, "draw_unavailable");
                    game.claimDraw(intended);
                }
            }
        } catch (IllegalArgumentException invalidMove) {
            throw new RoomProblem(UNPROCESSABLE_ENTITY, "illegal_move");
        }
        var history = new ArrayList<>(current.commands());
        history.add(new Applied(request.id(), actor, request.action(), move == null ? null : move.uci(), san));
        // Unchanged deadlines retain the original stored times. Otherwise a draw offer would debit a clock twice.
        if (request.action() != Action.MOVE && game.outcome() == null) {
            white = state.whiteMillis();
            black = state.blackMillis();
        }
        var nextBoard = new BoardState(game.position().fen(), game.position().turn(), white, black,
                game.outcome() == null ? deadline : 0, game.outcome() == null ? offer : null, game.outcome());
        var next = new RoomDocument(id, current.version() + 1, current.control(), current.white(), current.black(),
                nextBoard, history, new Date(now + KEEP_MS));
        if (!store.replace(current.version(), next)) {
            RoomDocument latest = find(id);
            if (seen(latest, actor, request)) return snapshot(latest, actor, clock.millis());
            throw new RoomProblem(CONFLICT, "stale");
        }
        return snapshot(next, actor, now);
    }

    private RoomDocument expire(RoomDocument room, long now) {
        var state = room.board();
        if (state.deadline() == 0 || now < state.deadline()) return room;
        var game = restore(room);
        game.timeout(state.turn());
        var ended = new BoardState(state.fen(), state.turn(), remaining(state, Side.WHITE, now),
                remaining(state, Side.BLACK, now), 0, null, game.outcome());
        var next = new RoomDocument(room.roomId(), room.version() + 1, room.control(), room.white(), room.black(),
                ended, room.commands(), new Date(now + KEEP_MS));
        return store.replace(room.version(), next) ? next : find(room.roomId());
    }

    private RoomDocument find(String id) {
        validateId(id);
        var found = store.load(id);
        if (found == null || found.expiresAt().getTime() <= clock.millis()) throw new RoomProblem(NOT_FOUND, "room_missing");
        return found;
    }

    private static Game restore(RoomDocument room) {
        var game = new Game(Position.initial());
        for (var entry : room.commands()) {
            if (entry.action() == Action.MOVE) game.play(Move.uci(entry.uci()));
        }
        game.restoreOutcome(room.board().outcome());
        return game;
    }

    private static boolean seen(RoomDocument room, Side seat, Command command) {
        for (var entry : room.commands()) {
            if (entry.side() == seat && entry.id().equals(command.id())) {
                if (entry.action() != command.action() || (entry.action() == Action.MOVE && !entry.uci().equals(command.uci()))) {
                    throw new RoomProblem(CONFLICT, "duplicate_id");
                }
                return true;
            }
        }
        return false;
    }

    private static Snapshot snapshot(RoomDocument room, Side side, long now) {
        var state = room.board();
        var moves = room.commands().stream().filter(entry -> entry.action() == Action.MOVE)
                .map(entry -> new Ply(entry.uci(), entry.san())).toList();
        return new Snapshot(room.roomId(), room.version(), side, room.control(), room.white().name(),
                room.black() == null ? null : room.black().name(), state.fen(), state.turn(),
                remaining(state, Side.WHITE, now), remaining(state, Side.BLACK, now), state.deadline(),
                state.drawOfferedBy(), state.outcome(), moves, now);
    }

    private static long remaining(BoardState state, Side side, long now) {
        return state.deadline() > 0 && state.turn() == side
                ? Math.max(0, Math.min(state.remaining(side), state.deadline() - now)) : state.remaining(side);
    }

    private static Move parseMove(String uci) {
        if (uci == null || !uci.matches("[a-h][1-8][a-h][1-8][qrbn]?")) throw new IllegalArgumentException();
        return Move.uci(uci);
    }

    private static void validateId(String id) {
        if (id == null || !ROOM_ID.matcher(id).matches()) throw bad();
    }

    private static String name(String proposed) {
        if (proposed == null) throw bad();
        String value = proposed.strip();
        if (value.isEmpty() || value.length() > 24 || value.codePoints().anyMatch(ch -> Character.isISOControl(ch)
                || Character.getType(ch) == Character.FORMAT || ch == '<' || ch == '>')) throw bad();
        return value;
    }

    private static Side requireSeat(RoomDocument room, String hash) {
        Side seat = seatFor(room, hash);
        if (seat == null) throw new RoomProblem(FORBIDDEN, "seat_denied");
        return seat;
    }

    private static Side seatFor(RoomDocument room, String hash) {
        for (Side side : Side.values()) {
            Seat seat = room.seat(side);
            if (seat != null && MessageDigest.isEqual(seat.tokenHash().getBytes(StandardCharsets.US_ASCII),
                    hash.getBytes(StandardCharsets.US_ASCII))) return side;
        }
        return null;
    }

    private static String tokenHash(String authorization) {
        var matcher = BEARER.matcher(authorization == null ? "" : authorization);
        if (!matcher.matches()) throw new RoomProblem(UNAUTHORIZED, "seat_denied");
        return HexFormat.of().formatHex(digest(matcher.group(1)));
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static RoomProblem bad() { return new RoomProblem(BAD_REQUEST, "invalid_request"); }
}
