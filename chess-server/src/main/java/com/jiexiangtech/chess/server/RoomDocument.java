package com.jiexiangtech.chess.server;

import java.util.Date;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.jiexiangtech.chess.domain.Outcome;
import com.jiexiangtech.chess.domain.Side;
import com.jiexiangtech.chess.online.OnlineProtocol.*;

@Document("online_games")
record RoomDocument(@Id String roomId, long version, Control control, Seat white, Seat black,
                    BoardState board, List<Applied> commands, Date expiresAt) {
    record Seat(String name, String tokenHash, String avatar) {}
    record BoardState(String fen, Side turn, long whiteMillis, long blackMillis, long deadline,
                      Side drawOfferedBy, Outcome outcome) {
        long remaining(Side side) { return side == Side.WHITE ? whiteMillis : blackMillis; }
    }

    record Applied(String id, Side side, Action action, String uci, String san) {}

    RoomDocument { commands = List.copyOf(commands); }
    Seat seat(Side side) { return side == Side.WHITE ? white : black; }
}
