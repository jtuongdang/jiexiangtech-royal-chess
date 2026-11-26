package com.jiexiangtech.chess.server;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import com.jiexiangtech.chess.online.OnlineProtocol;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class RoomEvents extends TextWebSocketHandler implements WebSocketConfigurer {
    private static final String ROOM_ID = "roomId";
    private final RoomService rooms;
    private final ConcurrentMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    RoomEvents(RoomService rooms) { this.rooms = rooms; }

    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this, OnlineProtocol.API + "/events")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                             WebSocketHandler handler, java.util.Map<String, Object> attributes) {
                        String id = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("room");
                        try {
                            rooms.authorize(id, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                            attributes.put(ROOM_ID, id);
                            return true;
                        } catch (RoomProblem denied) {
                            response.setStatusCode(denied.status);
                            return false;
                        }
                    }

                    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                         WebSocketHandler handler, Exception failure) {}
                });
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        String id = (String) session.getAttributes().get(ROOM_ID);
        session.setTextMessageSizeLimit(256);
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 2000, 4096));
        subscriptions.compute(id, (key, ids) -> {
            if (ids == null) ids = ConcurrentHashMap.newKeySet();
            ids.add(session.getId());
            return ids;
        });
        notifySession(session.getId(), new TextMessage("sync"));
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptions.computeIfPresent((String) session.getAttributes().get(ROOM_ID), (key, ids) -> {
            ids.remove(session.getId());
            return ids.isEmpty() ? null : ids;
        });
    }

    void changed(String id, long version) {
        var ids = subscriptions.get(id);
        if (ids == null) return;
        var message = new TextMessage(Long.toString(version));
        for (String session : ids) notifySession(session, message);
    }

    private void notifySession(String id, TextMessage message) {
        var session = sessions.get(id);
        if (session == null || !session.isOpen()) return;
        try { session.sendMessage(message); }
        catch (IOException | IllegalStateException failure) {
            try { session.close(CloseStatus.SERVER_ERROR); }
            catch (IOException ignored) { /* Heartbeat and transport cleanup remove disconnected sessions. */ }
        }
    }
}
