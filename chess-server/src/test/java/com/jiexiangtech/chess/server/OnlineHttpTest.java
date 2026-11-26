package com.jiexiangtech.chess.server;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiexiangtech.chess.domain.Side;
import com.jiexiangtech.chess.online.OnlineProtocol;
import com.jiexiangtech.chess.online.OnlineProtocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ROYAL_TEST_MONGO_URI", matches = ".+")
class OnlineHttpTest {
    private static final String KEY = "local-testing-organizer-key-only";
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String room = OnlineProtocol.roomId();
    private final String host = OnlineProtocol.seatToken();
    private final String guest = OnlineProtocol.seatToken();
    private final String rooms = OnlineProtocol.API + "/rooms";
    private int port;

    @Test void twoClientsPlayReconnectAndRecoverFromServerRestart() throws Exception {
        Command opening;
        String fen;
        long version;
        try (var running = start()) {
            assertEquals(200, exchange("GET", "/health", null, null, null).statusCode());
            var initial = snapshot("POST", rooms, new Create(room, "White", Control.RAPID_10_5), host, KEY);
            assertFalse(initial.playing());
            var whiteInbox = new LinkedBlockingQueue<String>();
            var whiteSocket = events(host, whiteInbox);
            assertEquals("sync", whiteInbox.poll(4, TimeUnit.SECONDS));
            var joined = snapshot("POST", rooms + "/" + room + "/join", new Join("Black"), guest, null);
            assertEquals(Side.BLACK, joined.seat());
            assertEquals(Long.toString(joined.version()), whiteInbox.poll(4, TimeUnit.SECONDS));

            var blackInbox = new LinkedBlockingQueue<String>();
            var blackSocket = events(guest, blackInbox);
            assertEquals("sync", blackInbox.poll(4, TimeUnit.SECONDS));
            opening = new Command(UUID.randomUUID().toString(), joined.version(), Action.MOVE, "e2e4");
            var after = snapshot("POST", rooms + "/" + room + "/commands", opening, host, null);
            assertEquals(Long.toString(after.version()), blackInbox.poll(4, TimeUnit.SECONDS));
            assertEquals(Side.BLACK, after.turn());
            assertEquals("e4", after.moves().get(0).san());
            assertEquals(403, exchange("GET", rooms + "/" + room, null, OnlineProtocol.seatToken(), null).statusCode());

            blackSocket.abort();
            var resumeInbox = new LinkedBlockingQueue<String>();
            var resumed = events(guest, resumeInbox);
            assertEquals("sync", resumeInbox.poll(4, TimeUnit.SECONDS));
            var view = snapshot("GET", rooms + "/" + room, null, guest, null);
            assertEquals(after.fen(), view.fen());
            var reply = new Command(UUID.randomUUID().toString(), view.version(), Action.MOVE, "e7e5");
            var response = snapshot("POST", rooms + "/" + room + "/commands", reply, guest, null);
            fen = response.fen();
            version = response.version();
            whiteSocket.abort();
            resumed.abort();
        }
        try (var restarted = start()) {
            var view = snapshot("GET", rooms + "/" + room, null, host, null);
            assertEquals(fen, view.fen());
            assertEquals(version, view.version());
            assertEquals(2, view.moves().size());
            var retry = snapshot("POST", rooms + "/" + room + "/commands", opening, host, null);
            assertEquals(version, retry.version());
            assertEquals(2, retry.moves().size());
        }
    }

    private ServletWebServerApplicationContext start() {
        var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(ServerMain.class).run(
                "--spring.data.mongodb.uri=" + System.getenv("ROYAL_TEST_MONGO_URI"),
                "--chess.beta-key=" + KEY, "--server.port=0", "--logging.level.org.mongodb.driver=WARN");
        port = context.getWebServer().getPort();
        return context;
    }

    private WebSocket events(String token, BlockingQueue<String> inbox) throws Exception {
        return http.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                .buildAsync(URI.create("ws://localhost:" + port + OnlineProtocol.API + "/events?room=" + room),
                        new WebSocket.Listener() {
                            @Override public CompletionStage<?> onText(WebSocket websocket, CharSequence data, boolean last) {
                                if (last) inbox.add(data.toString());
                                websocket.request(1);
                                return CompletableFuture.completedFuture(null);
                            }
                        }).get(4, TimeUnit.SECONDS);
    }

    private Snapshot snapshot(String method, String path, Object body, String token, String key) throws Exception {
        var response = exchange(method, path, body, token, key);
        assertEquals(200, response.statusCode(), response.body());
        return json.readValue(response.body(), Snapshot.class);
    }

    private HttpResponse<String> exchange(String method, String path, Object body, String token, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("X-Royal-Beta-Key", key);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
