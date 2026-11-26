package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalNavigationPanel extends JPanel {
    record Item(String id, String label, RoyalIcons.Glyph glyph, Runnable action) {}

    private static final int EXPANDED_WIDTH = 220;
    private static final int COMPACT_WIDTH = 66;

    RoyalNavigationPanel(BrandingManager branding, List<Item> items, String selectedId, String playerName,
                         String aboutLabel, String signOutLabel, String toggleTooltip,
                         boolean compact, Runnable onToggleCompact, Runnable onAbout, Runnable onSignOut) {
        super(new BorderLayout(0, 14));
        setOpaque(true);
        setBackground(new Color(0x111013));
        setPreferredSize(new Dimension(compact ? COMPACT_WIDTH : EXPANDED_WIDTH, 480));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x2C251B)),
                new EmptyBorder(16, compact ? 9 : 14, 14, compact ? 9 : 14)));

        // Top brand + collapse/expand control
        JPanel top = transparent(new BorderLayout(0, 10));
        if (compact) {
            JPanel logoWrap = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
            logoWrap.add(branding.logo(BrandingManager.EMBLEM, 38, 30, BrandConstants.PRODUCT));
            top.add(logoWrap, BorderLayout.NORTH);
            JPanel toggleWrap = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
            toggleWrap.add(new RoyalIconButton(RoyalIcons.Glyph.EXPAND, toggleTooltip, 16, 36, onToggleCompact));
            top.add(toggleWrap, BorderLayout.SOUTH);
        } else {
            JPanel headerRow = transparent(new BorderLayout(4, 0));
            headerRow.add(branding.logo(BrandingManager.GAME, 144, 78, BrandConstants.PRODUCT), BorderLayout.CENTER);
            JPanel btnWrap = transparent(new BorderLayout());
            btnWrap.add(new RoyalIconButton(RoyalIcons.Glyph.COLLAPSE, toggleTooltip, 15, 30, onToggleCompact), BorderLayout.NORTH);
            headerRow.add(btnWrap, BorderLayout.EAST);
            top.add(headerRow, BorderLayout.CENTER);
            top.add(divider(), BorderLayout.SOUTH);
        }
        add(top, BorderLayout.NORTH);

        // Navigation menu items
        JPanel menu = transparent(new GridLayout(0, 1, 0, 5));
        for (Item item : items) {
            menu.add(new NavButton(item, item.id().equals(selectedId), compact));
        }
        JPanel navWrap = transparent(new BorderLayout());
        navWrap.add(menu, BorderLayout.NORTH);
        add(navWrap, BorderLayout.CENTER);

        // Bottom player badge + About / Sign out
        JPanel bottom = transparent(new BorderLayout(0, 8));
        bottom.add(divider(), BorderLayout.NORTH);
        if (compact) {
            JPanel compactFooter = transparent(new GridLayout(0, 1, 0, 6));
            compactFooter.setBorder(new EmptyBorder(6, 0, 0, 0));
            compactFooter.add(centered(new RoyalIconButton(RoyalIcons.Glyph.ABOUT, aboutLabel, 16, 36, onAbout)));
            var signOutBtn = new RoyalIconButton(RoyalIcons.Glyph.SIGN_OUT, signOutLabel, 16, 36, onSignOut);
            signOutBtn.setDanger(false);
            compactFooter.add(centered(signOutBtn));
            bottom.add(compactFooter, BorderLayout.CENTER);
        } else {
            JPanel profileBadge = new RoyalPanel(new BorderLayout(8, 0), new Color(0x19171D), new Color(0x141317),
                    new Color(0x30281D), 12);
            profileBadge.setBorder(new EmptyBorder(8, 10, 8, 10));
            JLabel crown = new JLabel(RoyalIcons.icon(RoyalIcons.Glyph.CROWN, 16, GOLD));
            JLabel name = new JLabel(playerName);
            name.setForeground(PALE);
            name.setFont(sans(Font.BOLD, 13f));
            name.setToolTipText(playerName);
            profileBadge.add(crown, BorderLayout.WEST);
            profileBadge.add(name, BorderLayout.CENTER);

            JPanel actions = transparent(new GridLayout(0, 1, 0, 5));
            var aboutBtn = new RoyalButton(aboutLabel, RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.ABOUT, onAbout);
            aboutBtn.setHorizontalAlignment(SwingConstants.LEFT);
            aboutBtn.setBorder(new EmptyBorder(7, 12, 7, 12));
            aboutBtn.setFont(sans(Font.BOLD, 12.5f));
            aboutBtn.setMinimumSize(new Dimension(42, 32));
            var outBtn = new RoyalButton(signOutLabel, RoyalButton.Variant.SECONDARY, RoyalIcons.Glyph.SIGN_OUT, onSignOut);
            outBtn.setHorizontalAlignment(SwingConstants.LEFT);
            outBtn.setBorder(new EmptyBorder(7, 12, 7, 12));
            outBtn.setFont(sans(Font.BOLD, 12.5f));
            outBtn.setMinimumSize(new Dimension(42, 32));
            actions.add(aboutBtn);
            actions.add(outBtn);

            JPanel footerStack = transparent(new BorderLayout(0, 8));
            footerStack.setBorder(new EmptyBorder(6, 0, 0, 0));
            footerStack.add(profileBadge, BorderLayout.NORTH);
            footerStack.add(actions, BorderLayout.SOUTH);
            bottom.add(footerStack, BorderLayout.CENTER);
        }
        add(bottom, BorderLayout.SOUTH);
    }

    private static JPanel centered(Component c) {
        JPanel wrap = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrap.add(c);
        return wrap;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setPaint(new GradientPaint(0, 0, new Color(0x131216), 0, getHeight(), new Color(0x0D0C0F)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    private static final class NavButton extends JButton {
        private final RoyalIcons.Glyph glyph;
        private final boolean selected;
        private final boolean compact;

        NavButton(Item item, boolean selected, boolean compact) {
            super(compact ? "" : item.label());
            this.glyph = item.glyph();
            this.selected = selected;
            this.compact = compact;
            setToolTipText(item.label());
            getAccessibleContext().setAccessibleName(item.label());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
            setIconTextGap(11);
            setBorder(compact ? new EmptyBorder(8, 0, 8, 0) : new EmptyBorder(9, 14, 9, 12));
            setFont(sans(selected ? Font.BOLD : Font.PLAIN, 13.5f));
            setForeground(selected ? PALE : new Color(0xCFC9BE));
            setPreferredSize(new Dimension(compact ? 46 : 182, 38));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(RoyalIcons.icon(glyph, 17, selected ? GOLD : MUTED));
            addActionListener(e -> item.action().run());
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean hover = getModel().isRollover();
            if (selected) {
                g.setPaint(new GradientPaint(0, 0, new Color(212, 175, 55, 42), w, 0, new Color(212, 175, 55, 10)));
                g.fillRoundRect(0, 0, w, h, 10, 10);
                g.setColor(new Color(212, 175, 55, 85));
                g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
                // Metallic gold active indicator bar on the left
                g.setPaint(new GradientPaint(0, 6, PALE, 0, h - 6, GOLD));
                g.fillRoundRect(2, 7, 3, h - 14, 3, 3);
            } else if (hover) {
                g.setColor(new Color(255, 245, 220, 14));
                g.fillRoundRect(0, 0, w, h, 10, 10);
            }
            setIcon(RoyalIcons.icon(glyph, 17, selected ? GOLD : hover ? PALE : MUTED));
            setForeground(selected ? PALE : hover ? IVORY : new Color(0xC8C2B8));
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
