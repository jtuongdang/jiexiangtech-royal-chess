package com.jiexiangtech.chess.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class Pgn {
    private static final Pattern TAG = Pattern.compile("^\\[([A-Za-z0-9_]+) \"((?:\\\\.|[^\"\\\\])*)\"\\]$");

    private Pgn() {}

    public static String export(Game game, String event, String white, String black, String date) {
        String result = game.outcome() == null ? "*" : game.outcome().pgn();
        var tags = new LinkedHashMap<String, String>();
        tags.put("Event", event);
        tags.put("Site", "Local");
        tags.put("Date", date);
        tags.put("Round", "-");
        tags.put("White", white);
        tags.put("Black", black);
        tags.put("Result", result);
        if (!game.initial().fen().equals(Position.INITIAL_FEN)) {
            tags.put("SetUp", "1");
            tags.put("FEN", game.initial().fen());
        }
        StringBuilder output = new StringBuilder();
        tags.forEach((key, value) -> output.append('[').append(key).append(" \"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n]", " "))
                .append("\"]\n"));
        output.append('\n');
        int column = 0, index = 0;
        for (Game.Ply ply : game.moves()) {
            Position before = game.positionAt(index);
            String prefix = before.turn() == Side.WHITE ? before.fullmove() + ". "
                    : index == 0 ? before.fullmove() + "... " : "";
            String token = prefix + ply.san();
            if (column > 0 && column + token.length() > 76) {
                output.append('\n');
                column = 0;
            }
            output.append(token).append(' ');
            column += token.length() + 1;
            index++;
        }
        return output.append(result).append('\n').toString();
    }

    public record Imported(Game game, Map<String, String> tags, String result) {}

    public static Imported parse(String pgn) {
        Map<String, String> tags = new LinkedHashMap<>();
        StringBuilder moves = new StringBuilder();
        for (String line : pgn.lines().toList()) {
            var match = TAG.matcher(line.strip());
            if (moves.toString().isBlank() && match.matches()) {
                tags.put(match.group(1), match.group(2).replaceAll("\\\\([\\\\\"])", "$1"));
            } else if (!line.startsWith("%")) {
                moves.append(line).append('\n');
            }
        }
        Game game = new Game(Position.fromFen(tags.getOrDefault("FEN", Position.INITIAL_FEN)));
        StringBuilder mainLine = new StringBuilder();
        boolean comment = false, lineComment = false;
        int variation = 0;
        for (int i = 0; i < moves.length(); i++) {
            char ch = moves.charAt(i);
            if (lineComment) lineComment = ch != '\n';
            else if (comment) comment = ch != '}';
            else if (ch == '{') { mainLine.append(' '); comment = true; }
            else if (ch == ';') { mainLine.append(' '); lineComment = true; }
            else if (ch == '(') { mainLine.append(' '); variation++; }
            else if (ch == ')') { if (--variation < 0) throw new IllegalArgumentException("Unbalanced PGN variation"); }
            else if (variation == 0) mainLine.append(ch);
        }
        if (comment || variation != 0) throw new IllegalArgumentException("Unclosed PGN comment or variation");
        String result = null;
        for (String token : mainLine.toString().replaceAll("\\$\\d+", " ").replaceAll("\\d+\\.(?:\\.\\.)?", " ").strip().split("\\s+")) {
            if (token.isEmpty() || token.equals("e.p.")) continue;
            if (result != null) throw new IllegalArgumentException("Expected one PGN game");
            if (token.matches("1-0|0-1|1/2-1/2|\\*")) result = token;
            else game.play(San.parse(game.position(), token));
        }
        if (result == null) result = tags.getOrDefault("Result", "*");
        if (!result.matches("1-0|0-1|1/2-1/2|\\*") || !result.equals(tags.getOrDefault("Result", result))
                || (game.outcome() != null && !result.equals(game.outcome().pgn()))) {
            throw new IllegalArgumentException("Inconsistent PGN result");
        }
        return new Imported(game, Map.copyOf(tags), result);
    }
}
