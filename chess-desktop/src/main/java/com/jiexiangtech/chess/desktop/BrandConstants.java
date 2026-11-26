package com.jiexiangtech.chess.desktop;

final class BrandConstants {
    static final String PRODUCT = "ChessMaster – Royal Edition";
    static final String COMPANY = "JIEXIANGTECH";
    static final String COMPANY_CHINESE = "杰祥科技";
    static final String COPYRIGHT = "© 2026 " + COMPANY + ". All rights reserved.";
    static final String REPOSITORY = "jiexiangtech-royal-chess";

    private BrandConstants() {}

    static String version() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version == null ? "1.0.0" : version;
    }
}
