import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

class PrepareBranding {
    private static final Path ASSETS = Path.of("chess-desktop/src/main/resources/assets");
    private static final int[] ICON_SIZES = {16, 32, 64, 128, 256, 512, 1024};
    private static final String[] ICNS_TYPES = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10"};

    public static void main(String[] args) throws Exception {
        Path companyPath = Path.of(args.length == 3 ? args[0] : "branding/logo.png");
        Path gamePath = Path.of(args.length == 3 ? args[1] : "branding/ PM-2.png");
        Path splashPath = Path.of(args.length == 3 ? args[2] : "branding/bannerPM-3.png");
        BufferedImage company = read(companyPath, true), game = read(gamePath, true), splash = read(splashPath, false);
        BufferedImage companyLogo = cropAlpha(company, 1);
        BufferedImage companyMark = cropAlpha(company.getSubimage(0, 0, company.getWidth(), company.getHeight() * 63 / 100), 10);
        BufferedImage gameLogo = cropAlpha(game, 1);
        BufferedImage king = cropAlpha(game.getSubimage(0, 0, game.getWidth(), game.getHeight() * 55 / 100), 10);
        companyLogo = fit(companyLogo, 1000);
        companyMark = fit(companyMark, 720);
        gameLogo = fit(gameLogo, 1200);
        write("branding/company-logo.png", companyLogo);
        write("branding/company-emblem.png", companyMark);
        write("branding/game-logo.png", gameLogo);
        write("backgrounds/splash-background.png", splash);
        BufferedImage login = backdrop(splash, false), dashboard = backdrop(splash, true);
        write("backgrounds/login-background.png", login);
        write("backgrounds/dashboard-background.png", dashboard);

        BufferedImage icon = icon(king);
        write("branding/app-icon.png", icon);
        byte[][] frames = new byte[ICON_SIZES.length][];
        for (int i = 0; i < frames.length; i++) {
            int size = ICON_SIZES[i];
            BufferedImage version = size == 1024 ? icon : scaleIcon(icon, size);
            write("icons/app-icon-" + size + ".png", version);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(version, "PNG", bytes);
            frames[i] = bytes.toByteArray();
        }
        icns(frames);
        ico(frames);
        manifest(companyPath, gamePath, splashPath, company, game, splash, companyLogo, companyMark, gameLogo, login, dashboard);
    }

    private static BufferedImage read(Path source, boolean alpha) throws Exception {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null || image.getColorModel().hasAlpha() != alpha) {
            throw new IllegalArgumentException("Unexpected image or alpha channel: " + source);
        }
        return image;
    }

    private static BufferedImage cropAlpha(BufferedImage original, int threshold) {
        int left = original.getWidth(), right = -1, top = original.getHeight(), bottom = -1;
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                if ((original.getRGB(x, y) >>> 24) < threshold) continue;
                left = Math.min(x, left);
                right = Math.max(x, right);
                top = Math.min(y, top);
                bottom = Math.max(y, bottom);
            }
        }
        if (right < left) throw new IllegalArgumentException("Empty image");
        return original.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private static BufferedImage fit(BufferedImage source, int max) {
        double ratio = Math.min(1, (double) max / Math.max(source.getWidth(), source.getHeight()));
        BufferedImage result = new BufferedImage((int) Math.round(source.getWidth() * ratio),
                (int) Math.round(source.getHeight() * ratio), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(result);
        g.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
        g.dispose();
        return result;
    }

    private static BufferedImage backdrop(BufferedImage source, boolean dashboard) {
        int w = dashboard ? 1440 : 1600, h = dashboard ? 540 : 900;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        g.setPaint(new RadialGradientPaint(w * .32f, h * .5f, w * .72f, new float[]{0, .65f, 1},
                new Color[]{new Color(0x493218), new Color(0x151215), new Color(0x101014)}));
        g.fillRect(0, 0, w, h);

        // The side crops contain only the original chess art. The center wordmarks stay exclusive to the splash.
        int band = dashboard ? w * 45 / 100 : w * 37 / 100;
        g.drawImage(sideBand(source, band, h, false, dashboard), w - band, 0, null);
        if (!dashboard) g.drawImage(sideBand(source, w * 31 / 100, h, true, false), 0, 0, null);
        g.setPaint(new GradientPaint(0, 0, new Color(8, 8, 10, 10), 0, h, new Color(8, 8, 10, 150)));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static BufferedImage sideBand(BufferedImage source, int width, int height, boolean left, boolean dashboard) {
        int sourceWidth = source.getWidth(), sourceHeight = source.getHeight();
        int cropWidth = sourceWidth * 24 / 100;
        int cropHeight = Math.min(sourceHeight, cropWidth * height / width);
        int top = left ? sourceHeight - cropHeight
                : dashboard ? sourceHeight * 23 / 100 : sourceHeight * 61 / 100 - cropHeight / 2;
        top = Math.max(0, Math.min(sourceHeight - cropHeight, top));
        int startX = left ? 0 : sourceWidth - cropWidth;
        var band = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(band);
        g.drawImage(source, 0, 0, width, height, startX, top, startX + cropWidth, top + cropHeight, null);
        g.setComposite(AlphaComposite.DstIn);
        Color clear = new Color(0, 0, 0, 0);
        g.setPaint(left ? new GradientPaint(width * .37f, 0, Color.BLACK, width, 0, clear)
                : new GradientPaint(0, 0, clear, width * .58f, 0, Color.BLACK));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return band;
    }

    private static BufferedImage icon(BufferedImage mark) {
        var icon = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        var g = graphics(icon);
        var plate = new RoundRectangle2D.Double(50, 50, 924, 924, 208, 208);
        g.setPaint(new RadialGradientPaint(530, 370, 660, new float[]{0, 1},
                new Color[]{new Color(0x31324A), new Color(0x0C0C11)}));
        g.fill(plate);
        g.setStroke(new BasicStroke(11));
        g.setPaint(new GradientPaint(150, 180, new Color(0xF6D481), 910, 925, new Color(0x594117)));
        g.draw(plate);
        BufferedImage fitted = fit(mark, 820);
        double ratio = Math.min(820.0 / fitted.getWidth(), 805.0 / fitted.getHeight());
        int w = (int) Math.round(fitted.getWidth() * ratio), h = (int) Math.round(fitted.getHeight() * ratio);
        g.drawImage(fitted, (1024 - w) / 2, (1024 - h) / 2, w, h, null);
        g.dispose();
        return icon;
    }

    private static BufferedImage scaleIcon(BufferedImage original, int size) {
        BufferedImage current = original;
        for (int step = original.getWidth() / 2; step >= size; step /= 2) current = fit(current, step);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((current.getRGB(x, y) >>> 24) <= 2) current.setRGB(x, y, 0);
            }
        }
        return current;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    private static void write(String name, BufferedImage image) throws Exception {
        Path path = ASSETS.resolve(name);
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static void icns(byte[][] frames) throws Exception {
        try (var out = new DataOutputStream(Files.newOutputStream(ASSETS.resolve("icons/app-icon.icns")))) {
            int total = 8;
            for (byte[] frame : frames) total += frame.length + 8;
            out.writeBytes("icns");
            out.writeInt(total);
            for (int i = 0; i < frames.length; i++) {
                out.writeBytes(ICNS_TYPES[i]);
                out.writeInt(frames[i].length + 8);
                out.write(frames[i]);
            }
        }
    }

    private static void ico(byte[][] frames) throws Exception {
        try (var out = new DataOutputStream(Files.newOutputStream(ASSETS.resolve("icons/app-icon.ico")))) {
            int count = 5;
            out.writeShort(0);
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeShort(Short.reverseBytes((short) count));
            int offset = 6 + 16 * count;
            for (int i = 0; i < count; i++) {
                out.writeByte(ICON_SIZES[i] == 256 ? 0 : ICON_SIZES[i]);
                out.writeByte(ICON_SIZES[i] == 256 ? 0 : ICON_SIZES[i]);
                out.writeShort(0);
                out.writeShort(Short.reverseBytes((short) 1));
                out.writeShort(Short.reverseBytes((short) 32));
                out.writeInt(Integer.reverseBytes(frames[i].length));
                out.writeInt(Integer.reverseBytes(offset));
                offset += frames[i].length;
            }
            for (int i = 0; i < count; i++) out.write(frames[i]);
        }
    }

    private static String size(BufferedImage image) { return image.getWidth() + " × " + image.getHeight(); }

    private static void manifest(Path company, Path game, Path splash, BufferedImage c, BufferedImage g, BufferedImage s,
                                 BufferedImage companyLogo, BufferedImage companyMark, BufferedImage gameLogo,
                                 BufferedImage login, BufferedImage dashboard) throws Exception {
        Path doc = Path.of("docs/ASSET_MANIFEST.md");
        String manifest = Files.readString(doc).split("\n## Corporate branding", 2)[0];
        String section = """

                ## Corporate branding

                All three supplied PNGs were opened and identified by their visible content.
                Original filenames and source files are preserved. Target paths are relative to assets.

                | Source | Role and output | Original → output | Check |
                | --- | --- | --- | --- |
                | `%s` | `branding/company-logo.png` | %s → %s | Transparent bilingual wordmark and emblem |
                | `%s` | `branding/company-emblem.png` | %s → %s | Transparent symbol cropped above wordmark |
                | `%s` | `branding/game-logo.png` | %s → %s | Transparent full black-and-gold game logo |
                | `%s` | `backgrounds/splash-background.png` | %s → %s | Opaque artwork with embedded branding and copyright |
                | `%s` | `backgrounds/login-background.png` | %s → %s | Side crops blended with a dark background |
                | `%s` | `backgrounds/dashboard-background.png` | %s → %s | Right-hand chess artwork and tapered backdrop |
                | `%s` | `branding/app-icon.png`, `icons/app-icon-*.png` | %s → 16, 32, 64, 128, 256, 512, 1024 square | King and gold curve cropped above lettering |
                | Derived PNGs | `icons/app-icon.icns`, `icons/app-icon.ico` | 7 macOS frames, 5 Windows frames | Native icon containers with alpha |

                The four cue sounds in assets/sounds are generated 22.05 kHz mono WAVs.
                Typography uses system fonts. Royal Slate applies a cool tint to the existing marble tiles.
                """.formatted(company, size(c), size(companyLogo), company, size(c), size(companyMark),
                game, size(g), size(gameLogo), splash, size(s), size(s), splash, size(s), size(login),
                splash, size(s), size(dashboard), game, size(g));
        Files.writeString(doc, manifest.stripTrailing() + "\n" + section);
    }
}
