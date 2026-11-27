package com.jiexiangtech.chess.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final ObjectMapper json;

    RoomController(RoomService rooms, RoomEvents events, RoomStore store, ObjectMapper json) {
        this.rooms = rooms;
        this.events = events;
        this.store = store;
        this.json = json;
    }

    @GetMapping("/health")
    java.util.Map<String, String> health() {
        store.ping();
        return java.util.Map.of("status", "ok", "revision", System.getenv().getOrDefault("ROYAL_BUILD_REV", "local"));
    }

    @PostMapping(OnlineProtocol.API + "/rooms")
    Object create(@RequestBody Create create,
                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                    @RequestHeader(value = "X-Royal-Beta-Key", required = false) String betaKey,
                    @RequestHeader(value = OnlineProtocol.AVATAR_HEADER, required = false) String avatars) {
        return response(rooms.create(create, auth, betaKey), avatars);
    }

    @PostMapping(OnlineProtocol.API + "/rooms/{id}/join")
    Object join(@PathVariable("id") String id, @RequestBody Join join,
                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                @RequestHeader(value = OnlineProtocol.AVATAR_HEADER, required = false) String avatars) {
        Snapshot snapshot = rooms.join(id, join, auth);
        events.changed(id, snapshot.version());
        return response(snapshot, avatars);
    }

    @GetMapping(OnlineProtocol.API + "/rooms/{id}")
    Object view(@PathVariable("id") String id,
                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                @RequestHeader(value = OnlineProtocol.AVATAR_HEADER, required = false) String avatars) {
        return response(rooms.view(id, auth), avatars);
    }

    @PostMapping(OnlineProtocol.API + "/rooms/{id}/commands")
    Object command(@PathVariable("id") String id, @RequestBody Command command,
                   @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                   @RequestHeader(value = OnlineProtocol.AVATAR_HEADER, required = false) String avatars) {
        Snapshot snapshot = rooms.command(id, command, auth);
        events.changed(id, snapshot.version());
        return response(snapshot, avatars);
    }

    private Object response(Snapshot snapshot, String avatars) {
        if ("1".equals(avatars)) return snapshot;
        // Older desktops reject unknown JSON properties on their room snapshots.
        ObjectNode legacy = json.valueToTree(snapshot);
        legacy.remove(java.util.List.of("whiteAvatar", "blackAvatar"));
        return legacy;
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
