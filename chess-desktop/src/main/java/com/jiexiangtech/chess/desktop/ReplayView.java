package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.application.ChessEngine;
import com.jiexiangtech.chess.domain.*;
import com.jiexiangtech.chess.application.MatchOptions;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class ReplayView extends JPanel {
    private final RoyalFrame frame;
    private final I18n text;
    private final Game game;
    private final ChessBoardPanel board;
    private final Moves moves;
    private final Captures captures;
    private final JLabel progress = label(" ", PALE), assessment = label(" ", MUTED);
    private final JButton first, previous, next, last, autoplay, analyze;
    private final Timer timer;
    private SwingWorker<ChessEngine.Analysis, Void> search;
    private int ply;

    ReplayView(RoyalFrame frame, Game game, Side orientation, String theme, String white, String black,
               Runnable returnAction, Runnable export, boolean startAtEnd) {
        super(new BorderLayout(15, 10));
        this.frame = frame;
        this.text = frame.text();
        this.game = game;
        setOpaque(false);
        setBorder(new EmptyBorder(14, 12, 15, 18));
        add(heading(text.get("replay"), row(button(text.get("export"), false, export),
                button(text.get("back"), false, returnAction))), BorderLayout.NORTH);
        board = new ChessBoardPanel(frame.assets(), frame.prefs(), theme, () -> false, options -> null, move -> {});
        board.setFlipped(flip(frame.prefs(), orientation));
        add(board, BorderLayout.CENTER);
        var rail = card(new BorderLayout(0, 10));
        rail.setPreferredSize(new Dimension(290, 430));
        JLabel players = title(text.get("vs", white, black), 17);
        players.setToolTipText(players.getText());
        rail.add(stack(players, Box.createVerticalStrut(7), progress, Box.createVerticalStrut(6), label(text.get("moves"), MUTED)),
                BorderLayout.NORTH);
        moves = new Moves(text);
        JTable table = moves.table();
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint()), column = table.columnAtPoint(e.getPoint());
                if (row < 0 || column < 1) return;
                int index = row * 2 + column - (game.initial().turn() == Side.BLACK ? 1 : 0);
                if (index >= 0 && index <= game.plyCount()) seek(index);
            }
        });
        rail.add(scroll(table), BorderLayout.CENTER);
        captures = new Captures(frame.assets(), text);
        first = button(text.get("first"), false, () -> seek(0));
        previous = button(text.get("previous"), false, () -> seek(ply - 1));
        next = button(text.get("next"), false, () -> seek(ply + 1));
        last = button(text.get("last"), false, () -> seek(game.plyCount()));
        autoplay = button(text.get("autoplay"), false, this::toggle);
        analyze = button(text.get("analyze"), true, this::analyze);
        var controls = transparent(new GridLayout(0, 2, 5, 5));
        for (JButton button : new JButton[]{first, last, previous, next, autoplay, button(text.get("flip"), false, board::flip)}) {
            controls.add(button);
        }
        assessment.setToolTipText(assessment.getText());
        rail.add(stack(captures, controls, Box.createVerticalStrut(7), analyze, Box.createVerticalStrut(5), assessment),
                BorderLayout.SOUTH);
        add(rail, BorderLayout.EAST);
        timer = new Timer(850, event -> {
            seek(ply + 1);
            if (ply == game.plyCount()) stop();
        });
        moves.update(game, game.plyCount());
        seek(startAtEnd ? game.plyCount() : 0);
    }

    void close() { stop(); cancelAnalysis(); }

    private void seek(int nextPly) {
        int old = ply;
        ply = Math.max(0, Math.min(nextPly, game.plyCount()));
        cancelAnalysis();
        assessment.setText(" ");
        assessment.setToolTipText(null);
        Move move = ply == 0 ? null : game.moves().get(ply - 1).move();
        board.update(game.positionAt(ply), move, ply == old + 1 && board.isShowing());
        captures.update(game, ply);
        progress.setText(text.get("plyProgress", ply, game.plyCount()));
        first.setEnabled(ply > 0);
        previous.setEnabled(ply > 0);
        next.setEnabled(ply < game.plyCount());
        last.setEnabled(ply < game.plyCount());
        autoplay.setEnabled(game.plyCount() > 0);
        analyze.setEnabled(true);
    }

    private void toggle() {
        if (timer.isRunning()) { stop(); return; }
        if (ply == game.plyCount()) seek(0);
        timer.start();
        autoplay.setText(text.get("stop"));
    }

    private void stop() { timer.stop(); autoplay.setText(text.get("autoplay")); }

    private void analyze() {
        stop();
        cancelAnalysis();
        assessment.setText(text.get("analysisWorking"));
        analyze.setEnabled(false);
        Position position = game.positionAt(ply);
        search = new SwingWorker<>() {
            @Override protected ChessEngine.Analysis doInBackground() {
                return frame.engines().forLevel(MatchOptions.Level.EXPERT).analyze(position, Duration.ofSeconds(2)).orElse(null);
            }

            @Override protected void done() {
                if (search != this) return;
                search = null;
                analyze.setEnabled(true);
                try {
                    var result = get();
                    if (result == null) assessment.setText(text.get("analysisNone"));
                    else {
                        int score = position.turn() == Side.WHITE ? result.centipawns() : -result.centipawns();
                        if (Math.abs(score) > 29_000) {
                            assessment.setText(text.get("analysisMate", result.depth(), result.nodes()));
                        } else {
                            assessment.setText(text.get("analysisScore", (score >= 0 ? "+" : "") + score, result.depth(), result.nodes()));
                        }
                        board.showHint(result.move());
                    }
                    assessment.setToolTipText(assessment.getText());
                } catch (CancellationException ignored) {
                    // The replay cursor moved while the engine was working.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    frame.error("storeError", e.getCause());
                }
            }
        };
        search.execute();
    }

    private void cancelAnalysis() {
        if (search != null) search.cancel(true);
        search = null;
    }
}
