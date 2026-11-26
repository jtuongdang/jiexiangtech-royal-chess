package com.jiexiangtech.chess.infrastructure;

import java.nio.file.Path;
import java.util.Locale;

public final class DataPath {
    private DataPath() {}

    public static Path defaultDirectory() {
        String override = System.getProperty("royal.dataDir");
        if (override == null || override.isBlank()) override = System.getenv("CHESSMASTER_DATA_DIR");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath();
        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return home.resolve("Library/Application Support/ChessMaster Royal");
        if (os.contains("windows")) {
            String appData = System.getenv("LOCALAPPDATA");
            return (appData == null ? home.resolve("AppData/Local") : Path.of(appData)).resolve("ChessMaster Royal");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return (xdg == null || xdg.isBlank() ? home.resolve(".local/share") : Path.of(xdg)).resolve("chessmaster-royal");
    }
}
