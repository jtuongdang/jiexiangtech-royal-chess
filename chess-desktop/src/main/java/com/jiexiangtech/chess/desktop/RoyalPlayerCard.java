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
    private final JLabel nameLabel;
    private final JLabel ratingLabel;
    private final JComponent activeDot;
    private boolean activeTurn;
    private boolean inCheck;

    RoyalPlayerCard(AssetManager assets, Side side, String playerName, String badgeText, boolean isAi, String ratingText) {
        super(new BorderLayout(11, 0));
        this.side = side;
        setOpaque(false);
        setBorder(new EmptyBorder(10, 16, 10, 12));
        setPreferredSize(new Dimension(340, 72));
        setMinimumSize(new Dimension(280, 68));

        // Circular Royal Avatar Medallion (with double metallic gold ring)
        JComponent avatar = new JComponent() {
            {
                setPreferredSize(new Dimension(48, 48));
                setMinimumSize(new Dimension(48, 48));
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth(), h = getHeight();
                Color top = side == Side.WHITE ? new Color(0x46371F) : new Color(0x272226);
                Color bot = side == Side.WHITE ? new Color(0x1E180F) : new Color(0x121115);
                g.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
                g.fillOval(2, 2, w - 4, h - 4);

                // Radiant warm portrait backdrop inside circle so both black & white emblems pop cleanly
                RoyalGlowEffect.paintAmbientSpotlight(g, w / 2f, h * 0.48f, w * 0.46f,
                        side == Side.WHITE ? new Color(248, 220, 134, 92) : new Color(224, 186, 94, 98));
                assets.drawPieceFitted(g, Piece.of(side, Kind.KING), 8, 5, w - 16, h - 10);

                // Ornate metallic gold avatar ring
                g.setPaint(new LinearGradientPaint(0, 0, w, h,
                        new float[]{0f, 0.5f, 1f},
                        activeTurn ? new Color[]{new Color(0xFFF0BC), GOLD, new Color(0xF6DA88)}
                                : new Color[]{new Color(0xB89242), new Color(0x5E4922), new Color(0x9E7A32)}));
                g.setStroke(new BasicStroke(activeTurn ? 2.2f : 1.5f));
                g.drawOval(2, 2, w - 5, h - 5);
                g.dispose();
            }
        };

        nameLabel = new JLabel(playerName);
        nameLabel.setForeground(IVORY);
        nameLabel.setFont(sans(Font.BOLD, 13.5f));

        JLabel rolePill = pill(badgeText,
                isAi ? new Color(0xE0B94D) : new Color(212, 175, 55, 36),
                isAi ? new Color(0xF7DF9B) : new Color(212, 175, 55, 135),
                isAi ? BG : PALE);
        rolePill.setBorder(new EmptyBorder(1, 6, 1, 6));
        rolePill.setFont(sans(Font.BOLD, 10f));

        activeDot = new JComponent() {
            {
                setPreferredSize(new Dimension(11, 11));
                setMaximumSize(new Dimension(11, 11));
                setMinimumSize(new Dimension(11, 11));
            }
            @Override protected void paintComponent(Graphics graphics) {
                if (!activeTurn) return;
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = inCheck ? ERROR_TEXT : new Color(0x5AE084);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 95));
                g.fillOval(0, 0, 11, 11);
                g.setColor(c);
                g.fillOval(2, 2, 7, 7);
                g.dispose();
            }
        };

        JPanel nameRow = transparent(null);
        nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
        nameRow.add(nameLabel);
        nameRow.add(Box.createHorizontalStrut(6));
        nameRow.add(rolePill);
        nameRow.add(Box.createHorizontalStrut(6));
        nameRow.add(activeDot);

        ratingLabel = new JLabel(ratingText);
        ratingLabel.setForeground(new Color(0xB8AE9E));
        ratingLabel.setFont(sans(Font.PLAIN, 12.5f));

        JPanel center = transparent(new GridBagLayout());
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0; cc.gridy = 0; cc.weightx = 1; cc.anchor = GridBagConstraints.WEST; cc.fill = GridBagConstraints.HORIZONTAL;
        center.add(nameRow, cc);
        cc.gridy = 1; cc.insets = new Insets(2, 0, 0, 0);
        center.add(ratingLabel, cc);

        JPanel clockWrap = transparent(new GridBagLayout());
        clockWrap.add(clock);

        add(avatar, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(clockWrap, BorderLayout.EAST);
    }

    void update(String clockText, boolean timed, boolean active, boolean lowTime, boolean check, String ratingOverride) {
        this.activeTurn = active;
        this.inCheck = active && check;
        if (ratingOverride != null) ratingLabel.setText(ratingOverride);
        nameLabel.setForeground(active ? IVORY : new Color(0xC6C0B6));
        ratingLabel.setForeground(inCheck ? ERROR_TEXT : active ? new Color(0xE2CFA0) : new Color(0x9E9689));
        clock.update(clockText, timed, active, lowTime);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int cardX = 8, cardW = w - 8;

        if (activeTurn) {
            RoyalGlowEffect.paintOuterGlow(g, cardX + 1, 2, cardW - 2, h - 4, 14,
                    inCheck ? new Color(225, 65, 58, 120) : new Color(235, 190, 72, 115), 3);
            g.setPaint(new LinearGradientPaint(cardX, 0, w, h,
                    new float[]{0f, 0.45f, 1f},
                    inCheck
                            ? new Color[]{new Color(96, 30, 28, 225), new Color(46, 20, 20, 228), new Color(28, 16, 18, 232)}
                            : new Color[]{new Color(122, 92, 32, 205), new Color(56, 42, 18, 222), new Color(28, 23, 16, 232)}));
            g.fillRoundRect(cardX, 2, cardW - 2, h - 4, 14, 14);
            // Subtle warm inner top reflection
            g.setColor(new Color(255, 240, 192, 48));
            g.drawLine(cardX + 14, 3, w - 16, 3);
            if (inCheck) {
                g.setColor(new Color(235, 86, 76, 210));
                g.setStroke(new BasicStroke(1.8f));
                g.drawRoundRect(cardX, 2, cardW - 3, h - 5, 14, 14);
            } else {
                RoyalGlowEffect.paintMetallicGoldBorder(g, cardX, 2, cardW - 2, h - 4, 14, 1.8f, true);
            }
            // Signature glowing gold crown indicator at left edge of active player card
            RoyalIcons.draw(g, RoyalIcons.Glyph.CROWN, 0, (h - 16) / 2, 15, inCheck ? ERROR_TEXT : PALE);
        } else {
            g.setPaint(new GradientPaint(cardX, 0, new Color(23, 21, 25, 222), cardX, h, new Color(15, 14, 17, 232)));
            g.fillRoundRect(cardX, 2, cardW - 2, h - 4, 14, 14);
            g.setColor(new Color(92, 74, 44, 130));
            g.drawRoundRect(cardX, 2, cardW - 3, h - 5, 14, 14);
        }
        g.dispose();
    }
}
