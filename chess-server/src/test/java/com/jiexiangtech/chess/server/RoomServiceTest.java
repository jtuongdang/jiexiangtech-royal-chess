package com.jiexiangtech.chess.server;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Side;
import com.jiexiangtech.chess.online.OnlineProtocol;
import com.jiexiangtech.chess.online.OnlineProtocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomServiceTest {
    private static final String KEY = "test-organizer-key-not-for-deploy";
    private final TestClock time = new TestClock();
    private final MemoryStore db = new MemoryStore();
    private RoomService service = new RoomService(db, KEY, time);
    private final String id = OnlineProtocol.roomId();
    private final String white = "Bearer " + OnlineProtocol.seatToken();
    private final String black = "Bearer " + OnlineProtocol.seatToken();

    @Test void roomsRequireOrganizerKeyAndGiveEachSeatItsOwnCredential() {
        assertEquals("organizer_key", assertThrows(RoomProblem.class,
                () -> service.create(new Create(id, "White", Control.RAPID_10_5), white, "wrong")).code);
        Snapshot waiting = create();
        assertFalse(waiting.playing());
        assertEquals(Side.WHITE, service.create(new Create(id, "White", Control.RAPID_10_5), white, KEY).seat());
        assertEquals("seat_denied", assertThrows(RoomProblem.class, () -> service.view(id, black)).code);
        Snapshot joined = service.join(id, new Join("Black"), black);
        assertTrue(joined.playing());
        assertEquals(Side.BLACK, joined.seat());
        assertEquals(joined.version(), service.join(id, new Join("Black"), black).version());
        assertEquals("room_full", assertThrows(RoomProblem.class, () -> service.join(id, new Join("Third"),
                "Bearer " + OnlineProtocol.seatToken())).code);
    }

    @Test void turnClocksAndIdempotencySurviveServiceRestart() {
        Snapshot start = live();
        time.now += 4_000;
        var move = command(start.version(), Action.MOVE, "e2e4");
        assertEquals("wrong_turn", assertThrows(RoomProblem.class, () -> service.command(id, move, black)).code);
        Snapshot after = service.command(id, move, white);
        assertEquals(601_000, after.whiteMillis());
        assertEquals(600_000, after.blackMillis());
        assertEquals(Side.BLACK, after.turn());
        time.now += 900;
        service = new RoomService(db, KEY, time);
        Snapshot replay = service.command(id, move, white);
        assertEquals(after.version(), replay.version());
        assertEquals(1, replay.moves().size());
        assertEquals(599_100, replay.blackMillis());
        assertEquals("stale", assertThrows(RoomProblem.class,
                () -> service.command(id, command(start.version(), Action.MOVE, "e7e5"), black)).code);
    }

    @Test void timeoutUsesPersistedDeadlineAndRejectsLateMoves() {
        Snapshot start = live();
        time.now = start.deadline();
        var late = command(start.version(), Action.MOVE, "e2e4");
        assertEquals("game_ended", assertThrows(RoomProblem.class, () -> service.command(id, late, white)).code);
        Snapshot done = service.view(id, black);
        assertEquals(Outcome.Reason.TIMEOUT, done.outcome().reason());
        assertEquals(Side.BLACK, done.outcome().winner());
        assertEquals(0, done.whiteMillis());
        assertTrue(done.moves().isEmpty());
    }

    @Test void illegalMovesLeaveRevisionUnchangedAndOffersDoNotRestartClock() {
        Snapshot start = live();
        assertEquals("illegal_move", assertThrows(RoomProblem.class,
                () -> service.command(id, command(start.version(), Action.MOVE, "e2e5"), white)).code);
        assertEquals(start.version(), service.view(id, black).version());
        var e4 = service.command(id, command(start.version(), Action.MOVE, "e2e4"), white);
        var e5 = service.command(id, command(e4.version(), Action.MOVE, "e7e5"), black);
        time.now += 3_000;
        var offer = service.command(id, command(e5.version(), Action.OFFER_DRAW, null), black);
        assertEquals(e5.deadline(), offer.deadline());
        time.now += 2_000;
        var drawn = service.command(id, command(offer.version(), Action.ACCEPT_DRAW, null), white);
        assertEquals(600_000, drawn.whiteMillis());
        assertEquals(Outcome.Reason.AGREEMENT, drawn.outcome().reason());
    }

    @Test void automaticMateIsStoredAndSurvivesReplay() {
        Snapshot state = live();
        String[] inputs = {"f2f3", "e7e5", "g2g4", "d8h4"};
        for (int index = 0; index < inputs.length; index++) {
            state = service.command(id, command(state.version(), Action.MOVE, inputs[index]), index % 2 == 0 ? white : black);
        }
        service = new RoomService(db, KEY, time);
        Snapshot restored = service.view(id, white);
        assertEquals(Outcome.Reason.CHECKMATE, restored.outcome().reason());
        assertEquals("Qh4#", restored.moves().get(3).san());
        assertEquals(state.fen(), restored.fen());
    }

    private Snapshot create() { return service.create(new Create(id, "White", Control.RAPID_10_5), white, KEY); }
    private Snapshot live() { create(); return service.join(id, new Join("Black"), black); }
    private static Command command(long version, Action action, String uci) {
        return new Command(UUID.randomUUID().toString(), version, action, uci);
    }

    private static final class MemoryStore implements RoomStore {
        private final ConcurrentHashMap<String, RoomDocument> rooms = new ConcurrentHashMap<>();
        @Override public RoomDocument load(String id) { return rooms.get(id); }
        @Override public boolean insert(RoomDocument room) { return rooms.putIfAbsent(room.roomId(), room) == null; }
        @Override public boolean replace(long expected, RoomDocument room) {
            var old = load(room.roomId());
            return old != null && old.version() == expected && rooms.replace(room.roomId(), old, room);
        }
        @Override public void ping() {}
    }

    private static final class TestClock extends Clock {
        long now = 1_000_000;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
