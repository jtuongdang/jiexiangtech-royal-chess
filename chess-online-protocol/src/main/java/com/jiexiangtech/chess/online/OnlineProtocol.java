package com.jiexiangtech.chess.online;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Side;

public final class OnlineProtocol {
    public static final String API = "/api/v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private OnlineProtocol() {}

    public static String roomId() { return opaque(9); }
    public static String seatToken() { return opaque(32); }

    private static String opaque(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public enum Control {
        RAPID_5_0(5, 0), RAPID_10_5(10, 5), RAPID_15_10(15, 10);

        public final long baseMillis, incrementMillis;

        Control(int minutes, int incrementSeconds) {
            baseMillis = minutes * 60_000L;
            incrementMillis = incrementSeconds * 1_000L;
        }
    }

    public enum Action { MOVE, RESIGN, OFFER_DRAW, ACCEPT_DRAW, CLAIM_DRAW }

    public record Create(String roomId, String name, Control control) {}
    public record Join(String name) {}
    public record Command(String id, long version, Action action, String uci) {}
    public record Ply(String uci, String san) {}
    public record Problem(String code) {}
    public record Snapshot(String roomId, long version, Side seat, Control control, String white, String black,
                           String fen, Side turn, long whiteMillis, long blackMillis, long deadline,
                           Side drawOfferedBy, Outcome outcome, List<Ply> moves, long serverTime) {
        public Snapshot { moves = List.copyOf(moves); }
        public boolean playing() { return black != null && outcome == null; }
    }
}
