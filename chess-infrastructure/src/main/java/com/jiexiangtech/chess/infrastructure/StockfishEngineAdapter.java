package com.jiexiangtech.chess.infrastructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.jiexiangtech.chess.application.ChessEngine;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Rules;

/** UCI integration with a user-installed engine executable. */
public final class StockfishEngineAdapter implements ChessEngine {
    private static final String EOF = "\0";
    private final Path executable;

    public StockfishEngineAdapter(Path executable) { this.executable = executable.toAbsolutePath(); }

    public static Optional<StockfishEngineAdapter> discover() {
        String path = System.getenv("CHESSMASTER_STOCKFISH");
        if (path != null && !path.isBlank()) {
            try { return usable(Path.of(path)); } catch (InvalidPathException e) { return Optional.empty(); }
        }
        String filename = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows") ? "stockfish.exe" : "stockfish";
        String search = System.getenv("PATH");
        if (search == null) return Optional.empty();
        for (String directory : search.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (directory.isBlank()) continue;
            try {
                var engine = usable(Path.of(directory, filename));
                if (engine.isPresent()) return engine;
            } catch (InvalidPathException ignored) {
                // Continue through the user's PATH entries.
            }
        }
        return Optional.empty();
    }

    private static Optional<StockfishEngineAdapter> usable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path)
                ? Optional.of(new StockfishEngineAdapter(path)) : Optional.empty();
    }

    @Override public Optional<Analysis> analyze(Position position, Duration budget) {
        var legal = Rules.legalMoves(position);
        if (legal.isEmpty() || budget.toMillis() < 80 || Thread.currentThread().isInterrupted()) return Optional.empty();
        long deadline = System.nanoTime() + budget.toNanos();
        Process engine;
        try {
            engine = new ProcessBuilder(executable.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            return Optional.empty();
        }
        var output = new LinkedBlockingQueue<String>();
        Thread drain = new Thread(() -> drain(engine, output), "royal-uci");
        drain.setDaemon(true);
        drain.start();
        try (var commands = new PrintWriter(engine.getOutputStream(), true, StandardCharsets.UTF_8)) {
            commands.println("uci");
            if (!await(output, "uciok", deadline)) return Optional.empty();
            commands.println("setoption name Threads value 1");
            commands.println("setoption name Hash value 32");
            commands.println("isready");
            if (!await(output, "readyok", deadline)) return Optional.empty();
            long millis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()) - 55;
            if (millis < 25) return Optional.empty();
            commands.println("position fen " + position.fen());
            commands.println("go movetime " + millis);
            Evaluation score = null;
            for (String line; (line = read(output, deadline)) != null;) {
                if (line.startsWith("info ")) score = evaluate(line, score);
                if (!line.startsWith("bestmove ")) continue;
                Move move = Move.uci(line.split("\\s+")[1]);
                if (!legal.contains(move) || score == null) return Optional.empty();
                return Optional.of(new Analysis(move, score.centipawns(), score.depth(), score.nodes()));
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        } finally {
            engine.destroy();
            if (engine.isAlive()) engine.destroyForcibly();
        }
    }

    private static void drain(Process engine, LinkedBlockingQueue<String> output) {
        try (var stream = new BufferedReader(new InputStreamReader(engine.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = stream.readLine()) != null;) output.offer(line);
        } catch (IOException ignored) {
            // Closing a canceled subprocess also closes its stdout.
        } finally {
            output.offer(EOF);
        }
    }

    private static boolean await(LinkedBlockingQueue<String> output, String expected, long deadline) throws InterruptedException {
        for (String line; (line = read(output, deadline)) != null;) {
            if (line.equals(expected)) return true;
        }
        return false;
    }

    private static String read(LinkedBlockingQueue<String> output, long deadline) throws InterruptedException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return null;
        String line = output.poll(remaining, TimeUnit.NANOSECONDS);
        return EOF.equals(line) ? null : line;
    }

    private record Evaluation(int centipawns, int depth, long nodes) {}

    private static Evaluation evaluate(String line, Evaluation last) {
        String[] words = line.split("\\s+");
        Integer cp = null;
        int depth = 0;
        long nodes = 0;
        try {
            for (int i = 1; i < words.length - 1; i++) {
                switch (words[i]) {
                    case "string", "pv" -> { return cp == null ? last : new Evaluation(cp, depth, nodes); }
                    case "depth" -> depth = Integer.parseInt(words[++i]);
                    case "nodes" -> nodes = Long.parseLong(words[++i]);
                    case "score" -> {
                        if (i + 2 >= words.length) return last;
                        String kind = words[++i];
                        int value = Integer.parseInt(words[++i]);
                        if (kind.equals("cp")) cp = value;
                        else if (kind.equals("mate")) cp = (value > 0 ? 1 : -1) * (30_000 - Math.min(500, Math.abs(value)));
                    }
                    default -> {}
                }
            }
            return cp == null ? last : new Evaluation(cp, depth, nodes);
        } catch (NumberFormatException malformed) {
            return last;
        }
    }
}
