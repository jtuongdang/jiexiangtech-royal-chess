package com.jiexiangtech.chess.desktop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiexiangtech.chess.online.OnlineProtocol;
import com.jiexiangtech.chess.online.OnlineProtocol.*;

final class OnlineClient {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ROOM_ID = Pattern.compile("[A-Za-z0-9_-]{12}");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    record Seat(String server, String room, String token) {
        Seat {
            server = normalizeServer(server);
            if (room == null || !ROOM_ID.matcher(room).matches() || token == null
                    || !token.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException();
        }
        @Override public String toString() { return "Seat[" + room + "]"; }
    }

    static final class Failure extends RuntimeException {
        final int status;
        final String code;

        Failure(int status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }
    }

    static String normalizeServer(String input) {
        URI uri = URI.create(input.strip()).normalize();
        String scheme = uri.getScheme(), host = uri.getHost(), path = uri.getPath();
        if (host == null || scheme == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException();
        }
        boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(Locale.ROOT));
        if (!scheme.equalsIgnoreCase("https") && !(loopback && scheme.equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException();
        }
        return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority();
    }

    CompletableFuture<Snapshot> create(Seat seat, String name, Control control, String organizerKey) {
        return send(seat, "/rooms", new Create(seat.room(), name, control), organizerKey);
    }

    CompletableFuture<Snapshot> join(Seat seat, String name) {
        return send(seat, endpoint(seat) + "/join", new Join(name), null);
    }

    CompletableFuture<Snapshot> sync(Seat seat) { return send(seat, endpoint(seat), null, null); }

    CompletableFuture<Snapshot> command(Seat seat, Command command) {
        return send(seat, endpoint(seat) + "/commands", command, null);
    }

    CompletableFuture<WebSocket> events(Seat seat, Runnable changed, Consumer<WebSocket> ended) {
        String server = seat.server();
        URI uri = URI.create("ws" + server.substring(4) + OnlineProtocol.API + "/events?room=" + seat.room());
        return http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + seat.token())
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override public void onOpen(WebSocket ws) { ws.request(1); }

                    @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        if (last) changed.run();
                        ws.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override public java.util.concurrent.CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                        ended.accept(ws);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override public void onError(WebSocket ws, Throwable error) { ended.accept(ws); }
                });
    }

    private static String endpoint(Seat seat) { return "/rooms/" + seat.room(); }

    private CompletableFuture<Snapshot> send(Seat seat, String path, Object body, String key) {
        try {
            var request = HttpRequest.newBuilder(URI.create(seat.server() + OnlineProtocol.API + path))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + seat.token())
                    .timeout(Duration.ofSeconds(95));
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
            if (key != null) request.header("X-Royal-Beta-Key", key);
            return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                int status = response.statusCode();
                if (status != 200) {
                    String code;
                    try { code = JSON.readValue(response.body(), Problem.class).code(); }
                    catch (Exception nonJson) { code = "temporarily_unavailable"; }
                    throw new Failure(status, code);
                }
                try { return JSON.readValue(response.body(), Snapshot.class); }
                catch (Exception incompatible) { throw new Failure(502, "incompatible"); }
            });
        } catch (Exception invalid) {
            return CompletableFuture.failedFuture(new Failure(400, "invalid_request"));
        }
    }
}
