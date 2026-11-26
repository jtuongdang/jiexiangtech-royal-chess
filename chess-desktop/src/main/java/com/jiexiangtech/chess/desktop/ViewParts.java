package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class ViewParts {
    private ViewParts() {}

    static JPanel row(Component... children) {
        JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (Component child : children) row.add(child);
        return row;
    }

    static JPanel rowRight(Component... children) {
        JPanel row = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (Component child : children) row.add(child);
        return row;
    }

    static JPanel stack(Component... children) {
        JPanel panel = transparent(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component child : children) {
            if (child instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(child);
        }
        return panel;
    }

    static void gap(JPanel panel, int size) { panel.add(Box.createVerticalStrut(size)); }

    static JPanel heading(String text, Component actions) {
        return heading(text, null, actions);
    }

    static JPanel heading(String text, String subtitle, Component actions) {
        JPanel header = transparent(new BorderLayout(14, 0));
        JPanel titles = transparent(new BorderLayout(0, 4));
        titles.add(title(text, 30), BorderLayout.NORTH);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = caption(subtitle);
            titles.add(sub, BorderLayout.SOUTH);
        }
        header.add(titles, BorderLayout.CENTER);
        if (actions != null) {
            JPanel wrap = transparent(new GridBagLayout());
            wrap.add(actions);
            header.add(wrap, BorderLayout.EAST);
        }
        return header;
    }

    static Kind promote(Component parent, AssetManager assets, I18n i18n, Side side) {
        return RoyalDialog.promote(parent, assets, i18n, side);
    }

    static boolean flip(Preferences prefs, Side side) {
        return switch (prefs.orientation()) {
            case BLACK -> true;
            case WHITE -> false;
            case AUTO -> side == Side.BLACK;
        };
    }

    static String clock(long millis) {
        long sec = millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
        return sec >= 3600 ? "%d:%02d:%02d".formatted(sec / 3600, sec / 60 % 60, sec % 60) :
                "%02d:%02d".formatted(sec / 60, sec % 60);
    }

    static <T> JComboBox<T> combo(T[] values, Function<T, String> labeler) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, value == null ? "" : labeler.apply((T) value), index, selected, focus);
                cell.setBorder(new EmptyBorder(5, 10, 5, 10));
                return cell;
            }
        });
        styleCombo(combo);
        return combo;
    }

    static JPanel padded() {
        JPanel panel = transparent(new BorderLayout(0, 18));
        panel.setBorder(new EmptyBorder(24, 28, 22, 28));
        return panel;
    }
}
