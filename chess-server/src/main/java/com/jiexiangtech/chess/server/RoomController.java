package com.jiexiangtech.chess.server;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import com.jiexiangtech.chess.online.OnlineProtocol;
import com.jiexiangtech.chess.online.OnlineProtocol.*;

@RestController
class RoomController {
    private final RoomService rooms;
    private final RoomEvents events;
    private final RoomStore store;

    RoomController(RoomService rooms, RoomEvents events, RoomStore store) {
        this.rooms = rooms;
        this.events = events;
        this.store = store;
    }

    @GetMapping("/health")
    java.util.Map<String, String> health() {
        store.ping();
        return java.util.Map.of("status", "ok", "revision", System.getenv().getOrDefault("ROYAL_BUILD_REV", "local"));
    }

    @PostMapping(OnlineProtocol.API + "/rooms")
    Snapshot create(@RequestBody Create create,
                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                    @RequestHeader(value = "X-Royal-Beta-Key", required = false) String betaKey) {
        return rooms.create(create, auth, betaKey);
    }

    @PostMapping(OnlineProtocol.API + "/rooms/{id}/join")
    Snapshot join(@PathVariable("id") String id, @RequestBody Join join,
                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        Snapshot snapshot = rooms.join(id, join, auth);
        events.changed(id, snapshot.version());
        return snapshot;
    }

    @GetMapping(OnlineProtocol.API + "/rooms/{id}")
    Snapshot view(@PathVariable("id") String id,
                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return rooms.view(id, auth);
    }

    @PostMapping(OnlineProtocol.API + "/rooms/{id}/commands")
    Snapshot command(@PathVariable("id") String id, @RequestBody Command command,
                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        Snapshot snapshot = rooms.command(id, command, auth);
        events.changed(id, snapshot.version());
        return snapshot;
    }

    @ExceptionHandler(RoomProblem.class)
    ResponseEntity<Problem> roomProblem(RoomProblem exception) {
        return ResponseEntity.status(exception.status).body(new Problem(exception.code));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Problem> malformed() {
        return ResponseEntity.badRequest().body(new Problem("invalid_request"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Problem> storeUnavailable() {
        return ResponseEntity.status(503).body(new Problem("temporarily_unavailable"));
    }
}
