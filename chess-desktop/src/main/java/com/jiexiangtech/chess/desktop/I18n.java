package com.jiexiangtech.chess.desktop;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

final class I18n {
    private final Locale locale;
    private final ResourceBundle text;

    I18n(String language) {
        locale = Locale.forLanguageTag(language);
        text = ResourceBundle.getBundle("i18n.Messages", locale, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    String get(String key, Object... args) {
        String pattern = text.getString(key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }

    Locale locale() { return locale; }
}
