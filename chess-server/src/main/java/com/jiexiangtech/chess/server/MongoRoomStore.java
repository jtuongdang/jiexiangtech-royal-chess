package com.jiexiangtech.chess.server;

import java.time.Duration;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
class MongoRoomStore implements RoomStore {
    private final MongoTemplate mongo;

    MongoRoomStore(MongoTemplate mongo) {
        this.mongo = mongo;
        mongo.indexOps(RoomDocument.class).ensureIndex(new Index()
                .on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));
    }

    @Override public RoomDocument load(String id) { return mongo.findById(id, RoomDocument.class); }

    @Override public boolean insert(RoomDocument room) {
        try {
            mongo.insert(room);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override public boolean replace(long expectedVersion, RoomDocument room) {
        var query = Query.query(where("_id").is(room.roomId()).and("version").is(expectedVersion));
        return mongo.findAndReplace(query, room) != null;
    }

    @Override public void ping() { mongo.executeCommand(new Document("ping", 1)); }
}
