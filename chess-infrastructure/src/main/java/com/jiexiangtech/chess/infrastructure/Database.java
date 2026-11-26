package com.jiexiangtech.chess.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import com.jiexiangtech.chess.application.ChessStore.StorageException;
import com.jiexiangtech.chess.application.Puzzles;

final class Database {
    interface Work<T> { T run(Connection connection) throws SQLException; }
    private final String url;

    Database(Path file) {
        Path full = file.toAbsolutePath();
        try {
            Files.createDirectories(full.getParent());
        } catch (IOException e) {
            throw new StorageException(e);
        }
        url = "jdbc:sqlite:" + full;
    }

    Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    <T> T read(Work<T> work) {
        try (var connection = connect()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    <T> T write(Work<T> work) {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            try {
                T result = work.run(connection);
                statement.execute("COMMIT");
                return result;
            } catch (SQLException | RuntimeException e) {
                try { statement.execute("ROLLBACK"); } catch (SQLException failure) { e.addSuppressed(failure); }
                throw e;
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    void migrate() {
        read(connection -> {
            try (var statement = connection.createStatement()) { statement.execute("PRAGMA journal_mode = WAL"); }
            return null;
        });
        write(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY)");
                try (var rows = statement.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_migrations")) {
                    if (rows.next()) {
                        int version = rows.getInt(1);
                        if (version == 1) return null;
                        if (version != 0) throw new SQLException("Unsupported database schema: " + version);
                    }
                }
                for (String sql : migration().split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
                statement.execute("INSERT INTO schema_migrations(version) VALUES(1)");
            }
            try (var insert = connection.prepareStatement("INSERT INTO puzzles VALUES(?,?,?,?,?)")) {
                for (var puzzle : Puzzles.catalog()) {
                    insert.setString(1, puzzle.id());
                    insert.setString(2, puzzle.level().name());
                    insert.setString(3, puzzle.fen());
                    insert.setString(4, puzzle.solution().uci());
                    insert.setInt(5, puzzle.points());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
    }

    private static String migration() throws SQLException {
        try (var stream = Database.class.getResourceAsStream("/db/V001.sql")) {
            if (stream == null) throw new IOException("Migration not found");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException(e);
        }
    }
}
