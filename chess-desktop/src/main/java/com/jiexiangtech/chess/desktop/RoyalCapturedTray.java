package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalCapturedTray extends JPanel {
    private final AssetManager assets;
    private final I18n i18n;
    private final RowStrip whiteCaptures;
    private final RowStrip blackCaptures;

    RoyalCapturedTray(AssetManager assets, I18n i18n) {
        super(new GridLayout(2, 1, 0, 5));
        this.assets = assets;
        this.i18n = i18n;
        setOpaque(false);
        whiteCaptures = new RowStrip(i18n.get("side.WHITE"), true);
        blackCaptures = new RowStrip(i18n.get("side.BLACK"), false);
        add(whiteCaptures);
        add(blackCaptures);
    }

    void update(Game game, int count) {
        List<Piece> capturedByWhite = new ArrayList<>();
        List<Piece> capturedByBlack = new ArrayList<>();
        int whitePoints = 0, blackPoints = 0;
        var moves = game.moves();
        int limit = Math.min(count, moves.size());
        for (int i = 0; i < limit; i++) {
            Piece captured = moves.get(i).captured();
            if (captured == null) continue;
            if (captured.side() == Side.BLACK) {
                capturedByWhite.add(captured);
                whitePoints += value(captured.kind());
            } else {
                capturedByBlack.add(captured);
                blackPoints += value(captured.kind());
            }
        }
        Comparator<Piece> order = Comparator.comparingInt(p -> -value(p.kind()));
        capturedByWhite.sort(order);
        capturedByBlack.sort(order);
        whiteCaptures.setPieces(capturedByWhite, Math.max(0, whitePoints - blackPoints));
        blackCaptures.setPieces(capturedByBlack, Math.max(0, blackPoints - whitePoints));
    }

    private static int value(Kind kind) {
        return switch (kind) {
            case QUEEN -> 9;
            case ROOK -> 5;
            case BISHOP, KNIGHT -> 3;
            case PAWN -> 1;
            case KING -> 0;
        };
    }

    private final class RowStrip extends JPanel {
        private final boolean lightCapturer;
        private List<Piece> pieces = List.of();
        private final JLabel delta = new JLabel();

        RowStrip(String sideLabel, boolean lightCapturer) {
            super(new BorderLayout(8, 0));
            this.lightCapturer = lightCapturer;
            setOpaque(false);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            setPreferredSize(new Dimension(220, 34));

            JLabel tag = new JLabel(sideLabel);
            tag.setForeground(MUTED);
            tag.setFont(sans(Font.BOLD, 11.5f));
            tag.setPreferredSize(new Dimension(48, 22));
            add(tag, BorderLayout.WEST);

            JComponent strip = new JComponent() {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    if (pieces.isEmpty()) {
                        g.setColor(new Color(0x5A544B));
                        g.setFont(sans(Font.PLAIN, 12f));
                        g.drawString("—", 2, getHeight() / 2 + 4);
                        g.dispose();
                        return;
                    }
                    int slotH = Math.max(18, getHeight() - 2);
                    int step = pieces.size() > 9 ? Math.max(10, (getWidth() - slotH) / Math.max(1, pieces.size() - 1)) : 16;
                    int x = 0;
                    for (Piece p : pieces) {
                        // Warm backdrop lift behind black captured pieces so silhouettes remain crisp on dark surface
                        if (p.side() == Side.BLACK) {
                            g.setPaint(new RadialGradientPaint(x + slotH / 2f, 1 + slotH / 2f, slotH * 0.56f,
                                    new float[]{0f, 1f}, new Color[]{new Color(235, 198, 108, 86), new Color(0, 0, 0, 0)}));
                            g.fillOval(x, 0, slotH, slotH);
                        }
                        assets.drawPieceFitted(g, p, x, 1, slotH, slotH);
                        x += step;
                    }
                    g.dispose();
                }
            };
            add(strip, BorderLayout.CENTER);

            delta.setForeground(PALE);
            delta.setFont(sans(Font.BOLD, 11.5f));
            add(delta, BorderLayout.EAST);
        }

        void setPieces(List<Piece> pieces, int advantage) {
            this.pieces = List.copyOf(pieces);
            delta.setText(advantage > 0 ? "+" + advantage : "");
            setToolTipText(pieces.isEmpty() ? null : i18n.get("capturedBy",
                    i18n.get(lightCapturer ? "side.WHITE" : "side.BLACK")) + ": " +
                    pieces.stream().map(p -> i18n.get("kind." + p.kind())).filter(Objects::nonNull).reduce((a, b) -> a + ", " + b).orElse(""));
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x121115));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
            g.setColor(new Color(0x2B251C));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
            g.dispose();
        }
    }
}
