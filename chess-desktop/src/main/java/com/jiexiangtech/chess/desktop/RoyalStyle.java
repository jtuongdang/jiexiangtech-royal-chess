package com.jiexiangtech.chess.desktop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class RoyalStyle {
    static final Color BG = new Color(0x0D0D0D), SURFACE = new Color(0x171717), RAISED = new Color(0x202020),
            GOLD = new Color(0xD4AF37), PALE = new Color(0xF2D78B), IVORY = new Color(0xF5F0E4),
            TEXT = new Color(0xF5F5F5), MUTED = new Color(0xA3A3A3), LINE = new Color(0x363025),
            ERROR_TEXT = new Color(0xFF7777), SUCCESS = new Color(0x70E39C);
    private RoyalStyle() {}

    static void install() {
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", RAISED);
        UIManager.put("PasswordField.background", RAISED);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("Component.arc", 10);
        UIManager.put("Component.focusColor", GOLD);
        UIManager.put("Component.accentColor", GOLD);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.thumb", new Color(0x63543B));
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", new Color(0x40351C));
        UIManager.put("TableHeader.background", RAISED);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", IVORY);
        UIManager.put("OptionPane.messageFont", new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    }

    static JLabel title(String text, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(IVORY);
        label.setFont(new Font("Georgia", Font.PLAIN, size));
        return label;
    }

    static JLabel label(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        return label;
    }

    static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JPanel card(java.awt.LayoutManager layout) { return panel(layout, SURFACE); }

    static JPanel translucentCard(java.awt.LayoutManager layout) {
        return panel(layout, new Color(17, 16, 18, 237));
    }

    private static JPanel panel(java.awt.LayoutManager layout, Color background) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(background);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g.setColor(background.getAlpha() == 255 ? LINE : new Color(0x725536));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));
        return panel;
    }

    static JButton button(String text, boolean primary, Runnable action) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                var model = getModel();
                Color fill = !isEnabled() ? SURFACE : primary
                        ? model.isPressed() ? GOLD.darker() : model.isRollover() ? PALE : GOLD
                        : model.isPressed() ? SURFACE : model.isRollover() ? new Color(0x30281B) : RAISED;
                g.setColor(fill);
                g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 11, 11);
                g.setColor(isFocusOwner() ? PALE : primary && isEnabled() ? GOLD : LINE);
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setForeground(primary ? BG : IVORY);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setMinimumSize(new Dimension(42, 34));
        button.addActionListener(event -> action.run());
        return button;
    }

    static JTextField field(boolean secret) {
        JTextField field = secret ? new JPasswordField(18) : new JTextField(18);
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 36));
        field.putClientProperty("JTextField.padding", new java.awt.Insets(4, 9, 4, 9));
        field.putClientProperty("JComponent.roundRect", true);
        return field;
    }

    static JScrollPane scroll(java.awt.Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(null);
        pane.getViewport().setBackground(BG);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }
}
