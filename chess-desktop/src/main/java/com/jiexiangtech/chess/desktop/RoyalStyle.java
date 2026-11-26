package com.jiexiangtech.chess.desktop;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class RoyalStyle {
    static final Color BG = new Color(0x0B0B0D);
    static final Color SURFACE = new Color(0x141316);
    static final Color SURFACE_ELEVATED = new Color(0x1B191E);
    static final Color RAISED = new Color(0x232026);
    static final Color GOLD = new Color(0xD4AF37);
    static final Color GOLD_DEEP = new Color(0x9E7823);
    static final Color PALE = new Color(0xF2D78B);
    static final Color IVORY = new Color(0xF5F0E4);
    static final Color TEXT = new Color(0xF5F5F5);
    static final Color MUTED = new Color(0x9E988E);
    static final Color LINE = new Color(0x362E22);
    static final Color LINE_GOLD = new Color(0x6E552B);
    static final Color ERROR_TEXT = new Color(0xFF7777);
    static final Color SUCCESS = new Color(0x70E39C);
    static final Color DANGER_BG = new Color(0x3B171B);
    static final Color DANGER_HOVER = new Color(0x562026);
    static final Color DANGER_BORDER = new Color(0x8C363E);
    static final Color DANGER_TEXT = new Color(0xFFA0A0);
    static final Color WARN_CLOCK = new Color(0xFF7B6B);

    private RoyalStyle() {}

    static void install() {
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", RAISED);
        UIManager.put("PasswordField.background", RAISED);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", PALE);
        UIManager.put("PasswordField.caretForeground", PALE);
        UIManager.put("ComboBox.background", RAISED);
        UIManager.put("ComboBox.foreground", IVORY);
        UIManager.put("ComboBox.selectionBackground", new Color(0x3B2F18));
        UIManager.put("ComboBox.selectionForeground", PALE);
        UIManager.put("ComboBox.buttonBackground", SURFACE_ELEVATED);
        UIManager.put("ComboBox.buttonArrowColor", PALE);
        UIManager.put("Spinner.background", RAISED);
        UIManager.put("Spinner.foreground", IVORY);
        UIManager.put("Slider.thumbColor", GOLD);
        UIManager.put("Slider.hoverThumbColor", PALE);
        UIManager.put("Slider.pressedThumbColor", GOLD_DEEP);
        UIManager.put("Slider.trackValueColor", GOLD);
        UIManager.put("Slider.trackColor", new Color(0x2D2820));
        UIManager.put("Slider.focusedColor", new Color(212, 175, 55, 64));
        UIManager.put("Component.arc", 10);
        UIManager.put("Component.borderColor", LINE);
        UIManager.put("Component.focusColor", GOLD);
        UIManager.put("Component.accentColor", GOLD);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.thumb", new Color(0x5B4B33));
        UIManager.put("ScrollBar.hoverThumbColor", new Color(0x8A7045));
        UIManager.put("ScrollBar.track", new Color(0x111013));
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", new Color(0x40351C));
        UIManager.put("TableHeader.background", RAISED);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", IVORY);
        UIManager.put("OptionPane.messageFont", new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        UIManager.put("ToolTip.background", SURFACE_ELEVATED);
        UIManager.put("ToolTip.foreground", IVORY);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(LINE_GOLD));
    }

    static Font serif(int style, int size) {
        return new Font("Georgia", style, size);
    }

    static Font sans(int style, float size) {
        return new Font(Font.SANS_SERIF, style, Math.round(size)).deriveFont(size);
    }

    static Font mono(int style, float size) {
        return new Font(Font.MONOSPACED, style, Math.round(size)).deriveFont(size);
    }

    static JLabel title(String text, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(IVORY);
        label.setFont(serif(Font.PLAIN, size));
        return label;
    }

    static JLabel headingLabel(String text, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(IVORY);
        label.setFont(serif(Font.BOLD, size));
        return label;
    }

    static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(sans(Font.PLAIN, 12.5f));
        return label;
    }

    static JLabel eyebrow(String text) {
        JLabel label = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        label.setForeground(GOLD);
        label.setFont(sans(Font.BOLD, 11f));
        return label;
    }

    static JLabel label(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        return label;
    }

    static JLabel pill(String text, Color bg, Color border, Color fg) {
        JLabel badge = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(bg);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.setColor(border);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(fg);
        badge.setFont(sans(Font.BOLD, 11.5f));
        badge.setBorder(new EmptyBorder(4, 10, 4, 10));
        return badge;
    }

    static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JPanel divider() {
        JPanel line = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setPaint(new GradientPaint(0, 0, new Color(110, 85, 43, 30), getWidth() / 2f, 0,
                        new Color(212, 175, 55, 110), true));
                g.fillRect(0, 0, getWidth(), 1);
                g.dispose();
            }
        };
        line.setOpaque(false);
        line.setPreferredSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return line;
    }

    static JPanel card(LayoutManager layout) {
        return new RoyalCard(layout);
    }

    /** Preserved translucent card used on the approved Login screen. */
    static JPanel translucentCard(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(17, 16, 18, 237));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g.setColor(new Color(0x725536));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));
        return panel;
    }

    static RoyalButton button(String text, boolean primary, Runnable action) {
        return new RoyalButton(text, primary ? RoyalButton.Variant.PRIMARY : RoyalButton.Variant.SECONDARY, null, action);
    }

    static RoyalButton button(String text, RoyalButton.Variant variant, RoyalIcons.Glyph glyph, Runnable action) {
        return new RoyalButton(text, variant, glyph, action);
    }

    static JTextField field(boolean secret) {
        JTextField field = secret ? new JPasswordField(18) : new JTextField(18);
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 36));
        field.putClientProperty("JTextField.padding", new Insets(4, 10, 4, 10));
        field.putClientProperty("JComponent.roundRect", true);
        return field;
    }

    static JScrollPane scroll(java.awt.Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.getViewport().setBackground(BG);
        pane.getVerticalScrollBar().setUnitIncrement(18);
        return pane;
    }

    static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(RAISED);
        combo.setForeground(IVORY);
        combo.setFont(sans(Font.PLAIN, 13.5f));
        combo.setPreferredSize(new Dimension(Math.max(150, combo.getPreferredSize().width), 38));
        combo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
