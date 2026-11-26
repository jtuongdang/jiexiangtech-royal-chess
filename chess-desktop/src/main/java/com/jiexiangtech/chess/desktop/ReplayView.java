package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.application.ChessEngine;
import com.jiexiangtech.chess.application.MatchOptions;
import com.jiexiangtech.chess.domain.*;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;
import static com.jiexiangtech.chess.desktop.ViewParts.*;

final class ReplayView extends JPanel {
    private final RoyalFrame frame;
    private final I18n text;
    private final Game game;
    private final ChessBoardPanel board;
    private final RoyalMoveHistory moveHistory;
    private final RoyalCapturedTray captures;
    private final JLabel progressPill;
    private final JLabel assessment = caption(" ");
    private final RoyalIconButton first, previous, next, last, autoplay, flipBtn;
    private final RoyalButton analyze;
    private final Timer timer;
    private SwingWorker<ChessEngine.Analysis, Void> search;
    private int ply;

    ReplayView(RoyalFrame frame, Game game, Side orientation, String theme, String white, String black,
               Runnable returnAction, Runnable export, boolean startAtEnd) {
        super(new BorderLayout(16, 10));
        this.frame = frame;
        this.text = frame.text();
        this.game = game;
        setOpaque(false);
        setBorder(new EmptyBorder(14, 16, 14, 18));

        add(heading(text.get("replay"), text.get("vs", white, black),
                row(button(text.get("export"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.EXPORT, export),
                        button(text.get("back"), RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.PREV, returnAction))),
                BorderLayout.NORTH);

        board = new ChessBoardPanel(frame.assets(), frame.prefs(), theme, () -> false, options -> null, move -> {});
        board.setFlipped(flip(frame.prefs(), orientation));
        add(board, BorderLayout.CENTER);

        RoyalPanel rail = new RoyalPanel(new BorderLayout(0, 10), new Color(0x19171D), new Color(0x121115), LINE, 16);
        rail.setPreferredSize(new Dimension(312, 460));
        rail.setBorder(new EmptyBorder(16, 16, 14, 16));

        progressPill = pill("0 / " + game.plyCount(), new Color(212, 175, 55, 34), new Color(212, 175, 55, 125), PALE);
        JPanel topHeader = transparent(new BorderLayout(8, 0));
        topHeader.add(eyebrow(text.get("moves")), BorderLayout.WEST);
        topHeader.add(progressPill, BorderLayout.EAST);
        rail.add(stack(topHeader, Box.createVerticalStrut(8), divider()), BorderLayout.NORTH);

        moveHistory = new RoyalMoveHistory(text, this::seek);
        captures = new RoyalCapturedTray(frame.assets(), text);
        JPanel center = transparent(new BorderLayout(0, 8));
        center.add(moveHistory, BorderLayout.CENTER);
        center.add(captures, BorderLayout.SOUTH);
        rail.add(center, BorderLayout.CENTER);

        first = new RoyalIconButton(RoyalIcons.Glyph.FIRST, text.get("first"), () -> seek(0));
        previous = new RoyalIconButton(RoyalIcons.Glyph.PREV, text.get("previous"), () -> seek(ply - 1));
        autoplay = new RoyalIconButton(RoyalIcons.Glyph.PLAY, text.get("autoplay"), this::toggle);
        next = new RoyalIconButton(RoyalIcons.Glyph.NEXT, text.get("next"), () -> seek(ply + 1));
        last = new RoyalIconButton(RoyalIcons.Glyph.LAST, text.get("last"), () -> seek(game.plyCount()));
        flipBtn = new RoyalIconButton(RoyalIcons.Glyph.FLIP, text.get("flip"), board::flip);

        JPanel transport = transparent(new GridLayout(1, 6, 6, 0));
        for (var b : new RoyalIconButton[]{first, previous, autoplay, next, last, flipBtn}) transport.add(b);

        analyze = button(text.get("analyze"), RoyalButton.Variant.PRIMARY, RoyalIcons.Glyph.ANALYZE, this::analyze);
        RoyalPanel evalCard = new RoyalPanel(new BorderLayout(), new Color(0x121115), new Color(0x0E0D10), new Color(0x2B251C), 10);
        evalCard.setBorder(new EmptyBorder(8, 10, 8, 10));
        assessment.setForeground(PALE);
        evalCard.add(assessment, BorderLayout.CENTER);

        JPanel south = transparent(new BorderLayout(0, 8));
        south.add(transport, BorderLayout.NORTH);
        south.add(analyze, BorderLayout.CENTER);
        south.add(evalCard, BorderLayout.SOUTH);
        rail.add(south, BorderLayout.SOUTH);

        add(rail, BorderLayout.EAST);

        timer = new Timer(850, event -> {
            seek(ply + 1);
            if (ply == game.plyCount()) stop();
        });
        moveHistory.update(game, game.plyCount(), startAtEnd ? game.plyCount() : 0);
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
        moveHistory.setActivePly(ply);
        captures.update(game, ply);
        progressPill.setText(text.get("plyProgress", ply, game.plyCount()));
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
        autoplay.setGlyph(RoyalIcons.Glyph.STOP);
        autoplay.setActive(true);
        autoplay.setToolTipText(text.get("stop"));
    }

    private void stop() {
        timer.stop();
        autoplay.setGlyph(RoyalIcons.Glyph.PLAY);
        autoplay.setActive(false);
        autoplay.setToolTipText(text.get("autoplay"));
    }

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
