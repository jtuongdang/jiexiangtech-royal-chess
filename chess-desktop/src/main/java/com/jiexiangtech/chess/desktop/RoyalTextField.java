package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Document;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalTextField extends JPanel {
    private final JTextField input;
    private final RoyalIcons.Glyph leadingIcon;
    private boolean focused;

    RoyalTextField(String placeholder, RoyalIcons.Glyph leadingIcon) {
        super(new BorderLayout(8, 0));
        this.leadingIcon = leadingIcon;
        setOpaque(false);
        setBorder(new EmptyBorder(0, leadingIcon == null ? 12 : 34, 0, 12));
        setPreferredSize(new Dimension(240, 38));

        input = new JTextField();
        input.setOpaque(false);
        input.setBorder(new EmptyBorder(6, 0, 6, 0));
        input.setForeground(IVORY);
        input.setCaretColor(PALE);
        input.setFont(sans(Font.PLAIN, 13.5f));
        if (placeholder != null) {
            input.putClientProperty("JTextField.placeholderText", placeholder);
            input.getAccessibleContext().setAccessibleName(placeholder);
        }
        input.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
            @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
        });
        add(input, BorderLayout.CENTER);
    }

    String getText() { return input.getText(); }
    void setText(String text) { input.setText(text); }
    Document getDocument() { return input.getDocument(); }
    JTextField input() { return input; }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(focused ? new Color(0x211E24) : new Color(0x18161B));
        g.fillRoundRect(1, 1, w - 2, h - 2, 11, 11);
        g.setColor(focused ? GOLD : LINE);
        g.drawRoundRect(1, 1, w - 3, h - 3, 11, 11);
        if (leadingIcon != null) {
            RoyalIcons.draw(g, leadingIcon, 11, (h - 15) / 2, 15, focused ? PALE : MUTED);
        }
        g.dispose();
    }
}
