package com.jiexiangtech.chess.desktop;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import com.jiexiangtech.chess.infrastructure.DataPath;

final class OnlineSeatFile {
    private final Path file;

    OnlineSeatFile(long localProfile) {
        this(DataPath.defaultDirectory(), localProfile);
    }

    OnlineSeatFile(Path directory, long localProfile) {
        file = directory.resolve("online-seat-" + localProfile + ".json");
    }

    OnlineClient.Seat read() throws IOException {
        if (!Files.isRegularFile(file)) return null;
        if (Files.size(file) > 4096) throw new IOException("Invalid seat file");
        return OnlineClient.JSON.readValue(file.toFile(), OnlineClient.Seat.class);
    }

    void save(OnlineClient.Seat seat) throws IOException {
        Path parent = Files.createDirectories(file.getParent());
        Path temp = Files.getFileStore(parent).supportsFileAttributeView("posix")
                ? Files.createTempFile(parent, ".online-seat-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(parent, ".online-seat-", ".tmp");
        try {
            OnlineClient.JSON.writeValue(temp.toFile(), seat);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
