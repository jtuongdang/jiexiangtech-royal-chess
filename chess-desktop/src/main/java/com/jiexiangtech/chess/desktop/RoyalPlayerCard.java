package com.jiexiangtech.chess.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalPlayerCard extends JPanel {
    private final Side side;
    private final RoyalGameClock clock = new RoyalGameClock();
    private final JLabel turnPill;
    private final JLabel nameLabel;
    private boolean activeTurn;
    private boolean inCheck;

    RoyalPlayerCard(AssetManager assets, Side side, String playerName, String roleLabel, String turnLabel) {
        super(new BorderLayout(12, 0));
        this.side = side;
        setOpaque(false);
        setBorder(new EmptyBorder(8, 14, 8, 12));

        // Left side medallion with actual piece sprite
        JComponent avatar = new JComponent() {
            {
                setPreferredSize(new Dimension(38, 38));
                setMinimumSize(new Dimension(38, 38));
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth(), h = getHeight();
                Color bgTop = side == Side.WHITE ? new Color(0x2D2820) : new Color(0x1E1C21);
                Color bgBot = side == Side.WHITE ? new Color(0x1D1A15) : new Color(0x111014);
                g.setPaint(new GradientPaint(0, 0, bgTop, 0, h, bgBot));
                g.fillOval(1, 1, w - 2, h - 2);
                if (side == Side.BLACK) {
                    g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, w * 0.48f,
                            new float[]{0f, 1f}, new Color[]{new Color(228, 192, 102, 82), new Color(0, 0, 0, 0)}));
                    g.fillOval(2, 2, w - 4, h - 4);
                }
                g.setColor(activeTurn ? GOLD : LINE_GOLD);
                g.setStroke(new BasicStroke(activeTurn ? 1.8f : 1.1f));
                g.drawOval(1, 1, w - 3, h - 3);
                assets.drawPieceFitted(g, Piece.of(side, Kind.KING), 6, 4, w - 12, h - 8);
                g.dispose();
            }
        };

        nameLabel = new JLabel(playerName);
        nameLabel.setForeground(IVORY);
        nameLabel.setFont(sans(Font.BOLD, 14.5f));

        JLabel roleBadge = pill(roleLabel, new Color(0x211E24), new Color(0x3B3224), MUTED);
        turnPill = pill(turnLabel, new Color(212, 175, 55, 36), new Color(212, 175, 55, 165), PALE);
        turnPill.setVisible(false);

        JPanel metaRow = transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        metaRow.add(roleBadge);
        metaRow.add(turnPill);

        JPanel textCol = transparent(new BorderLayout(0, 3));
        textCol.add(nameLabel, BorderLayout.NORTH);
        textCol.add(metaRow, BorderLayout.SOUTH);

        add(avatar, BorderLayout.WEST);
        add(textCol, BorderLayout.CENTER);
        add(clock, BorderLayout.EAST);
    }

    void update(String clockText, boolean timed, boolean active, boolean lowTime, boolean check, String turnBadgeText) {
        this.activeTurn = active;
        this.inCheck = active && check;
        turnPill.setText(turnBadgeText);
        turnPill.setVisible(active);
        turnPill.setForeground(inCheck ? ERROR_TEXT : PALE);
        nameLabel.setForeground(active ? IVORY : new Color(0xC2BCB2));
        clock.update(clockText, timed, active, lowTime);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        Color top = activeTurn ? new Color(0x211D16) : new Color(0x161519);
        Color bottom = activeTurn ? new Color(0x151310) : new Color(0x111014);
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRoundRect(0, 0, w, h, 14, 14);

        if (activeTurn) {
            // Subtle active gold left bar + ambient glow
            g.setPaint(new GradientPaint(0, 0, inCheck ? new Color(220, 65, 55, 48) : new Color(212, 175, 55, 34),
                    w * 0.45f, 0, new Color(0, 0, 0, 0)));
            g.fillRoundRect(0, 0, w, h, 14, 14);
            g.setColor(inCheck ? ERROR_TEXT : GOLD);
            g.fillRoundRect(2, 10, 3, h - 20, 3, 3);
        }

        g.setColor(inCheck ? new Color(220, 75, 65, 190) : activeTurn ? new Color(212, 175, 55, 155) : LINE);
        g.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
        g.dispose();
    }
}
