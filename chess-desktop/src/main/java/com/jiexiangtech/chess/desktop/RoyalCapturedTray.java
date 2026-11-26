package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalCapturedTray extends JPanel {
    private static final Kind[] ORDER = {Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT, Kind.PAWN};
    private final AssetManager assets;
    private final I18n i18n;
    private final SideTray whiteTray;
    private final SideTray blackTray;

    RoyalCapturedTray(AssetManager assets, I18n i18n) {
        super(new BorderLayout(0, 8));
        this.assets = assets;
        this.i18n = i18n;
        setOpaque(false);
        setBorder(new EmptyBorder(10, 12, 11, 12));

        // Reference-styled header with crossed swords icon + Captured Pieces title
        JPanel titleRow = transparent(new FlowLayout(FlowLayout.LEFT, 7, 0));
        titleRow.add(new JLabel(RoyalIcons.icon(RoyalIcons.Glyph.NEW_GAME, 16, PALE)));
        JLabel title = new JLabel(i18n.get("capture"));
        title.setForeground(IVORY);
        title.setFont(sans(Font.BOLD, 14f));
        titleRow.add(title);
        add(titleRow, BorderLayout.NORTH);

        // Two side-by-side dark recessed trays (White captures left, Black captures right)
        JPanel split = transparent(new GridLayout(1, 2, 10, 0));
        whiteTray = new SideTray(i18n.get("side.WHITE"), true);
        blackTray = new SideTray(i18n.get("side.BLACK"), false);
        split.add(whiteTray);
        split.add(blackTray);
        add(split, BorderLayout.CENTER);
    }

    void update(Game game, int count) {
        Map<Piece, Integer> byWhite = new LinkedHashMap<>();
        Map<Piece, Integer> byBlack = new LinkedHashMap<>();
        int whitePts = 0, blackPts = 0;
        var moves = game.moves();
        int limit = Math.min(count, moves.size());
        for (int i = 0; i < limit; i++) {
            Piece captured = moves.get(i).captured();
            if (captured == null) continue;
            if (captured.side() == Side.BLACK) {
                byWhite.merge(captured, 1, Integer::sum);
                whitePts += value(captured.kind());
            } else {
                byBlack.merge(captured, 1, Integer::sum);
                blackPts += value(captured.kind());
            }
        }
        whiteTray.setCaptured(sorted(byWhite, Side.BLACK), Math.max(0, whitePts - blackPts));
        blackTray.setCaptured(sorted(byBlack, Side.WHITE), Math.max(0, blackPts - whitePts));
    }

    private static Map<Piece, Integer> sorted(Map<Piece, Integer> raw, Side pieceSide) {
        Map<Piece, Integer> out = new LinkedHashMap<>();
        for (Kind kind : ORDER) {
            Piece p = Piece.of(pieceSide, kind);
            Integer c = raw.get(p);
            if (c != null && c > 0) out.put(p, c);
        }
        return out;
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

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, new Color(22, 20, 24, 224), 0, h, new Color(14, 13, 16, 234)));
        g.fillRoundRect(0, 0, w, h, 14, 14);
        RoyalGlowEffect.paintMetallicGoldBorder(g, 0, 0, w, h, 14, 1.2f, false);
        g.dispose();
    }

    private final class SideTray extends JPanel {
        private final boolean whiteCapturer;
        private Map<Piece, Integer> counts = Map.of();
        private final JLabel delta = new JLabel();

        SideTray(String labelText, boolean whiteCapturer) {
            super(new BorderLayout(0, 6));
            this.whiteCapturer = whiteCapturer;
            setOpaque(false);
            setBorder(new EmptyBorder(7, 9, 7, 9));
            setPreferredSize(new Dimension(140, 74));

            JPanel top = transparent(new BorderLayout(4, 0));
            JLabel badge = pill(labelText,
                    whiteCapturer ? new Color(134, 168, 126, 62) : new Color(172, 82, 86, 68),
                    whiteCapturer ? new Color(180, 212, 168, 120) : new Color(216, 118, 120, 125),
                    whiteCapturer ? new Color(0xE8F3E2) : new Color(0xFAD7D7));
            badge.setBorder(new EmptyBorder(2, 8, 2, 8));
            badge.setFont(sans(Font.BOLD, 11f));

            delta.setForeground(PALE);
            delta.setFont(sans(Font.BOLD, 11.5f));
            top.add(badge, BorderLayout.WEST);
            top.add(delta, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            JComponent piecesCanvas = new JComponent() {
                { setPreferredSize(new Dimension(120, 34)); }
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    if (counts.isEmpty()) {
                        g.setColor(new Color(0x5E584F));
                        g.setFont(sans(Font.PLAIN, 12f));
                        g.drawString("—", 3, getHeight() / 2 + 4);
                        g.dispose();
                        return;
                    }
                    int slotH = Math.max(24, getHeight() - 2);
                    int n = counts.size();
                    int step = n > 1 ? Math.min(28, Math.max(18, (getWidth() - slotH) / (n - 1))) : 28;
                    int x = 0;
                    for (var entry : counts.entrySet()) {
                        Piece p = entry.getKey();
                        int qty = entry.getValue();
                        if (p.side() == Side.BLACK) {
                            RoyalGlowEffect.paintAmbientSpotlight(g, x + slotH / 2f, slotH / 2f, slotH * 0.58f,
                                    new Color(235, 196, 102, 88));
                        }
                        assets.drawPieceFitted(g, p, x, 1, slotH - 2, slotH - 2);
                        if (qty > 1) {
                            String txt = "×" + qty;
                            g.setFont(sans(Font.BOLD, 9.5f));
                            FontMetrics fm = g.getFontMetrics();
                            int bw = fm.stringWidth(txt) + 6;
                            int bx = x + slotH - bw + 2, by = slotH - 12;
                            g.setColor(new Color(20, 17, 13, 215));
                            g.fillRoundRect(bx, by, bw, 12, 6, 6);
                            g.setColor(GOLD);
                            g.drawRoundRect(bx, by, bw - 1, 11, 6, 6);
                            g.setColor(PALE);
                            g.drawString(txt, bx + 3, by + 9);
                        }
                        x += step;
                    }
                    g.dispose();
                }
            };
            add(piecesCanvas, BorderLayout.CENTER);
        }

        void setCaptured(Map<Piece, Integer> map, int advantage) {
            this.counts = map;
            delta.setText(advantage > 0 ? "+" + advantage : "");
            setToolTipText(map.isEmpty() ? null : i18n.get("capturedBy",
                    i18n.get(whiteCapturer ? "side.WHITE" : "side.BLACK")));
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(12, 11, 14, 195));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.setColor(new Color(62, 52, 36, 140));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g.dispose();
        }
    }
}
