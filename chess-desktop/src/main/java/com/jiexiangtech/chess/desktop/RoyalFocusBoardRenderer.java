package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;

/**
 * Royal Focus Mode 2.5D perspective chessboard renderer matching royal-focus-reference.png:
 * - True 3x3 homography perspective tabletop & cached warped marble texture
 * - Physical 3D front slab with metallic gold rails & sculpted royal crown + laurel emblem
 * - Beveled metallic gold molding & corner filigree
 * - Perspective coordinates (A-H, 1-8)
 * - Overhead golden spotlight, clipped reflections, contact/directional shadows, and depth-sorted 2.5D billboard pieces
 */
final class RoyalFocusBoardRenderer implements BoardRenderer {
    private final AssetManager assets;
    private final RoyalPieceRenderer pieceRenderer;
    private final PerspectiveTextureRenderer textureRenderer;
    private final ShadowRenderer shadowRenderer;
    private final ReflectionRenderer reflectionRenderer;
    private final CinematicLightingEngine lighting;

    RoyalFocusBoardRenderer(AssetManager assets, RoyalPieceRenderer pieceRenderer) {
        this.assets = assets;
        this.pieceRenderer = pieceRenderer;
        this.textureRenderer = new PerspectiveTextureRenderer(assets);
        this.shadowRenderer = new ShadowRenderer(assets);
        this.reflectionRenderer = new ReflectionRenderer(assets);
        this.lighting = new CinematicLightingEngine(assets);
    }

    @Override
    public int squareAt(int width, int height, boolean flipped, double px, double py, Position position, int pieceScale) {
        return PerspectiveCoordinateMapper.fit(width, height, flipped).squareAt(px, py, position, pieceScale);
    }

    @Override
    public void paint(Graphics2D g, RenderState s) {
        PerspectiveCoordinateMapper mapper = PerspectiveCoordinateMapper.fit(s.width(), s.height(), s.flipped());
        PerspectiveProjection proj = mapper.projection();
        Preferences.GraphicsQuality quality = s.preferences().graphicsQuality();

        // 1. Overhead golden studio spotlight behind & over the board
        lighting.paintBackdropLighting(g, s.width(), s.height(), quality, 1.0f);

        // 2. Soft tabletop trapezoid drop shadow beneath the 3D board slab
        shadowRenderer.paintBoardDropShadow(g, mapper);

        // 3. 3D Physical Front Slab & Ornate Metallic Gold Perspective Frame
        paint3DFrontSlab(g, mapper);
        paintPerspectiveFrame(g, mapper);

        // 4. Cached perspective-warped 64-square marble surface
        textureRenderer.paintWarpedBoard(g, mapper, s.theme(), quality);

        // 5. Inner gold fillet + subtle inner perimeter shadow
        Path2D.Double boardOutline = proj.quadPath(0, 0, 1, 1);
        g.setColor(new Color(0, 0, 0, 105));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(boardOutline);
        g.setPaint(new LinearGradientPaint(
                (float) mapper.outerTL().x, (float) mapper.outerTL().y,
                (float) mapper.outerBR().x, (float) mapper.outerBR().y,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0xF6DC93), new Color(0xA47A26), new Color(0xF0D17B)}));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(proj.quadPath(-0.005, -0.005, 1.005, 1.005));
        g.setStroke(new BasicStroke(1f));

        // 6. Perspective square highlights (last move, hover, selected bloom, check pulse, legal targets, hint)
        paintPerspectiveHighlights(g, mapper, s);

        // 7. Subtle glossy tabletop piece reflections (clipped strictly to the 8x8 playing surface)
        boolean allowReflections = quality == Preferences.GraphicsQuality.HIGH || quality == Preferences.GraphicsQuality.ULTRA;
        boolean directionalShadows = quality != Preferences.GraphicsQuality.LOW;
        double moveT = s.moveProgress();

        if (allowReflections) {
            Shape oldClip = g.getClip();
            g.setClip(boardOutline);
            for (var entry : PieceDepthSorter.backToFront(s.position(), s.flipped())) {
                if ((entry.square() == s.selected() && s.dragPoint() != null)
                        || (moveT < 1.0 && s.last() != null && entry.square() == s.last().to())) continue;
                Point2D.Double anchor = mapper.squareAnchor(entry.square());
                double tileW = mapper.squareWidth(entry.square());
                Dimension approx = estimatePieceBox(entry.piece(), tileW, s.preferences().pieceScale());
                reflectionRenderer.paintPieceReflection(g, entry.piece(), anchor.x, anchor.y, approx.width, approx.height, 0.0);
            }
            g.setClip(oldClip);
        }

        // 8. Fading captured piece (if animating capture)
        if (moveT < 1.0 && s.disappearing() != null && s.capturedSquare() >= 0) {
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0.0, 1.0 - moveT)));
            Point2D.Double capAnchor = mapper.squareAnchor(s.capturedSquare());
            pieceRenderer.drawPerspectivePiece(g, s.disappearing(), capAnchor.x, capAnchor.y,
                    mapper.squareWidth(s.capturedSquare()), s.preferences().pieceScale(), s.preferences().highQuality(),
                    isDark(s.capturedSquare()), 0.0, shadowRenderer, directionalShadows);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // 9. Depth-sorted upright 2.5D billboard pieces (back rank -> front rank)
        for (var entry : PieceDepthSorter.backToFront(s.position(), s.flipped())) {
            int sq = entry.square();
            if ((sq == s.selected() && s.dragPoint() != null)
                    || (moveT < 1.0 && s.last() != null && sq == s.last().to())) continue;
            Point2D.Double anchor = mapper.squareAnchor(sq);
            double tileW = mapper.squareWidth(sq);
            double selectLift = (sq == s.selected()) ? tileW * 0.055 : 0.0;
            pieceRenderer.drawPerspectivePiece(g, entry.piece(), anchor.x, anchor.y, tileW,
                    s.preferences().pieceScale(), s.preferences().highQuality(), isDark(sq),
                    selectLift, shadowRenderer, directionalShadows);
        }

        // 10. Perspective-interpolated moving piece animation (interpolates (u, v) + local depth scale + parabolic lift!)
        if (moveT < 1.0 && s.last() != null) {
            double eased = s.easedMoveProgress();
            double u0 = mapper.centerU(s.last().from()), v0 = mapper.anchorV(s.last().from());
            double u1 = mapper.centerU(s.last().to()), v1 = mapper.anchorV(s.last().to());
            double u = u0 + (u1 - u0) * eased;
            double v = v0 + (v1 - v0) * eased;
            Point2D.Double pos = proj.project(u, v);
            double tileW = mapper.squareWidthAt(v);
            double lift = Math.sin(Math.PI * moveT) * tileW * 0.32;
            pieceRenderer.drawPerspectivePiece(g, s.position().piece(s.last().to()), pos.x, pos.y, tileW,
                    s.preferences().pieceScale(), s.preferences().highQuality(), isDark(s.last().to()),
                    lift, shadowRenderer, directionalShadows);
        }

        // 11. Hint directional arrow over board
        if (s.hint() != null) {
            Point2D.Double a = mapper.squareCenter(s.hint().from());
            Point2D.Double b = mapper.squareCenter(s.hint().to());
            Classic2DBoardRenderer.drawArrow(g, a.x, a.y, b.x, b.y, mapper.squareWidth(s.hint().to()));
        }

        // 12. Elevated dragged piece
        if (s.dragPoint() != null && s.selected() >= 0) {
            Point2D.Double uv = proj.unproject(s.dragPoint().x, s.dragPoint().y);
            double clampedV = Math.max(0.0, Math.min(1.0, uv.y));
            double tileW = mapper.squareWidthAt(clampedV);
            pieceRenderer.drawPerspectivePiece(g, s.position().piece(s.selected()),
                    s.dragPoint().x, s.dragPoint().y + tileW * 0.20, tileW * 1.04,
                    s.preferences().pieceScale(), s.preferences().highQuality(), true,
                    tileW * 0.24, shadowRenderer, directionalShadows);
        }

        // 13. Perspective coordinates (A-H along front lip, 1-8 along left sloped edge)
        paintCoordinates(g, mapper);

        // 14. Checkmate golden sparks & in-board outcome overlay
        if (s.celebrating()) {
            Point2D.Double center = proj.project(0.5, 0.5);
            Classic2DBoardRenderer.paintSparks(g, center.x, center.y, mapper.outerBR().x - mapper.outerBL().x, s.celebrationProgress());
        }
        if (s.overlayTitle() != null) {
            Rectangle box = mapper.boardBoundingBox();
            Classic2DBoardRenderer.paintOutcomeOverlay(g, box.x, box.y, box.width, box.height,
                    s.overlayTitle(), s.overlaySubtitle(), s.overlayAccent(), s.overlayAlpha());
        }

        // 15. Soft cinematic corner vignette
        lighting.paintForegroundVignette(g, s.width(), s.height(), quality, 0.72f);
    }

    private void paint3DFrontSlab(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        Point2D.Double bl = mapper.outerBL(), br = mapper.outerBR();
        double slab = mapper.slabThickness();

        // Dark marble physical front vertical slab
        Path2D.Double front = new Path2D.Double();
        front.moveTo(bl.x, bl.y);
        front.lineTo(br.x, br.y);
        front.lineTo(br.x + 2, br.y + slab);
        front.lineTo(bl.x - 2, bl.y + slab);
        front.closePath();

        g.setPaint(new LinearGradientPaint((float) bl.x, (float) bl.y, (float) br.x, (float) (br.y + slab),
                new float[]{0f, 0.30f, 0.50f, 0.70f, 1f},
                new Color[]{new Color(0x16120D), new Color(0x281F15), new Color(0x36291A),
                        new Color(0x221B13), new Color(0x120F0C)}));
        g.fill(front);

        // Subtle dark marble gold veins across front slab
        g.setColor(new Color(212, 175, 55, 38));
        g.setStroke(new BasicStroke(1.1f));
        double w = br.x - bl.x;
        g.drawLine((int) (bl.x + w * 0.14), (int) (bl.y + slab * 0.25), (int) (bl.x + w * 0.22), (int) (bl.y + slab * 0.85));
        g.drawLine((int) (bl.x + w * 0.76), (int) (bl.y + slab * 0.20), (int) (bl.x + w * 0.84), (int) (bl.y + slab * 0.82));

        // Top & bottom metallic gold molding rails on the front slab
        g.setPaint(new LinearGradientPaint((float) bl.x, (float) bl.y, (float) br.x, (float) br.y,
                new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
                new Color[]{new Color(0xD9B04A), new Color(0xFFF0BC), new Color(0xD4AF37),
                        new Color(0xFBE5A2), new Color(0xB88B2B)}));
        g.setStroke(new BasicStroke(3.2f));
        g.drawLine((int) Math.round(bl.x), (int) Math.round(bl.y), (int) Math.round(br.x), (int) Math.round(br.y));

        g.setPaint(new LinearGradientPaint((float) bl.x, (float) (bl.y + slab), (float) br.x, (float) (br.y + slab),
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0x8A6620), new Color(0xE8C462), new Color(0x7B591B)}));
        g.setStroke(new BasicStroke(2.2f));
        g.drawLine((int) Math.round(bl.x - 2), (int) Math.round(bl.y + slab), (int) Math.round(br.x + 2), (int) Math.round(br.y + slab));

        // Vertical gold corner trim caps
        g.drawLine((int) Math.round(bl.x), (int) Math.round(bl.y), (int) Math.round(bl.x - 2), (int) Math.round(bl.y + slab));
        g.drawLine((int) Math.round(br.x), (int) Math.round(br.y), (int) Math.round(br.x + 2), (int) Math.round(br.y + slab));
        g.setStroke(new BasicStroke(1f));

        // Sculpted metallic gold Royal Crown & Laurel emblem centered on the front slab
        int midX = (int) Math.round((bl.x + br.x) * 0.5);
        int midY = (int) Math.round(bl.y + slab * 0.5);
        int crownSz = Math.max(15, (int) Math.round(slab * 0.78));
        RoyalIcons.draw(g, RoyalIcons.Glyph.CROWN, midX - crownSz / 2, midY - crownSz / 2, crownSz, new Color(0xF6D986));

        // Sculpted golden laurel sprigs flanking the crown
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int dir : new int[]{-1, 1}) {
            Path2D.Double stem = new Path2D.Double();
            stem.moveTo(midX + dir * (crownSz * 0.58), midY + crownSz * 0.34);
            stem.quadTo(midX + dir * (crownSz * 1.15), midY + crownSz * 0.14,
                    midX + dir * (crownSz * 1.48), midY - crownSz * 0.28);
            g.setColor(new Color(0xD8AE48));
            g.draw(stem);
            for (int k = 0; k < 3; k++) {
                double t = 0.25 + k * 0.28;
                double lx = midX + dir * (crownSz * (0.68 + t * 0.74));
                double ly = midY + crownSz * (0.28 - t * 0.52);
                g.setColor(new Color(0xF5D886));
                g.fill(new Ellipse2D.Double(lx - 2.6, ly - 1.4, 5.2, 2.8));
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintPerspectiveFrame(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        PerspectiveProjection proj = mapper.projection();
        double fl = PerspectiveCoordinateMapper.FRAME_LEFT;
        double fr = PerspectiveCoordinateMapper.FRAME_RIGHT;
        double ft = PerspectiveCoordinateMapper.FRAME_TOP;
        double fb = PerspectiveCoordinateMapper.FRAME_BOTTOM;

        // Soft golden edge bloom around the board top-plane trapezoid
        Path2D.Double outerPath = proj.quadPath(-fl, -ft, 1.0 + fr, 1.0 + fb);
        g.setColor(new Color(235, 190, 74, 34));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outerPath);
        g.setColor(new Color(246, 212, 114, 52));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outerPath);

        // Dark obsidian recessed coordinate frame deck
        g.setPaint(new GradientPaint(
                (float) mapper.outerTL().x, (float) mapper.outerTL().y, new Color(0x231B13),
                (float) mapper.outerBR().x, (float) mapper.outerBR().y, new Color(0x110E0B)));
        g.fill(outerPath);

        // Outer beveled metallic gold perimeter rim
        g.setPaint(new LinearGradientPaint(
                (float) mapper.outerTL().x, (float) mapper.outerTL().y,
                (float) mapper.outerBR().x, (float) mapper.outerBR().y,
                new float[]{0f, 0.28f, 0.50f, 0.76f, 1f},
                new Color[]{new Color(0xF9E3A0), new Color(0xC69834), new Color(0x7C5A1C),
                        new Color(0xD8AE48), new Color(0xFAE5A4)}));
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outerPath);

        // Inner fine bronze trim line
        g.setColor(new Color(214, 174, 72, 115));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(proj.quadPath(-fl * 0.72, -ft * 0.68, 1.0 + fr * 0.72, 1.0 + fb * 0.76));

        // Specular metallic gold rim highlights along left & right sloped frame rails and bottom corners (reference signature!)
        Point2D.Double leftMid = proj.project(-fl, 0.56);
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) leftMid.x, (float) leftMid.y, 48f, new Color(255, 224, 128, 92));
        Point2D.Double rightMid = proj.project(1.0 + fr, 0.42);
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) rightMid.x, (float) rightMid.y, 42f, new Color(255, 224, 128, 76));
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) mapper.outerBL().x + 14f, (float) mapper.outerBL().y - 4f,
                52f, new Color(255, 232, 142, 110));
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) mapper.outerBR().x - 14f, (float) mapper.outerBR().y - 4f,
                52f, new Color(255, 232, 142, 96));

        // Ornate metallic gold filigree brackets at all 4 perspective corners
        paintPerspectiveCorner(g, proj, -fl * 0.88, -ft * 0.84, 0.085, 0.068);
        paintPerspectiveCorner(g, proj, 1.0 + fr * 0.88, -ft * 0.84, -0.085, 0.068);
        paintPerspectiveCorner(g, proj, -fl * 0.88, 1.0 + fb * 0.86, 0.092, -0.075);
        paintPerspectiveCorner(g, proj, 1.0 + fr * 0.88, 1.0 + fb * 0.86, -0.092, -0.075);
        g.setStroke(new BasicStroke(1f));
    }

    private static void paintPerspectiveCorner(Graphics2D g, PerspectiveProjection proj,
                                               double cu, double cv, double du, double dv) {
        Point2D.Double pCorner = proj.project(cu, cv);
        Point2D.Double pArmU = proj.project(cu + du, cv);
        Point2D.Double pArmV = proj.project(cu, cv + dv);
        Point2D.Double pInner = proj.project(cu + du * 0.45, cv + dv * 0.45);
        Point2D.Double pScrollU = proj.project(cu + du * 0.72, cv + dv * 0.18);
        Point2D.Double pScrollV = proj.project(cu + du * 0.18, cv + dv * 0.72);

        g.setColor(new Color(0xF9E39E));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double bracket = new Path2D.Double();
        bracket.moveTo(pArmU.x, pArmU.y);
        bracket.lineTo(pCorner.x, pCorner.y);
        bracket.lineTo(pArmV.x, pArmV.y);
        g.draw(bracket);

        g.setColor(new Color(0xD8AE48));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double scroll = new Path2D.Double();
        scroll.moveTo(pScrollU.x, pScrollU.y);
        scroll.quadTo(pInner.x, pInner.y, pScrollV.x, pScrollV.y);
        g.draw(scroll);

        g.setColor(new Color(0xFFF3C6));
        g.fill(new Ellipse2D.Double(pInner.x - 2.2, pInner.y - 2.2, 4.4, 4.4));
    }

    private void paintPerspectiveHighlights(Graphics2D g, PerspectiveCoordinateMapper mapper, RenderState s) {
        PerspectiveProjection proj = mapper.projection();

        // Last move subtle golden wash
        if (s.last() != null) {
            fillSquare(g, mapper, s.last().from(), new Color(240, 200, 88, 68));
            fillSquare(g, mapper, s.last().to(), new Color(246, 210, 98, 92));
        }

        // Hover square subtle gold outline
        if (s.hovered() >= 0 && s.hovered() != s.selected() && s.playable()
                && (isOwn(s.position(), s.hovered()) || isCandidate(s.candidates(), s.hovered()))) {
            Path2D.Double hp = proj.squarePath(mapper.visualCol(s.hovered()), mapper.visualRow(s.hovered()));
            g.setColor(new Color(248, 220, 134, 38));
            g.fill(hp);
            g.setColor(new Color(248, 222, 138, 165));
            g.setStroke(new BasicStroke(1.8f));
            g.draw(hp);
        }

        // Selected square luminous golden bloom (exact reference e4 glow!)
        if (s.selected() >= 0) {
            Path2D.Double sp = proj.squarePath(mapper.visualCol(s.selected()), mapper.visualRow(s.selected()));
            Point2D.Double sc = mapper.squareCenter(s.selected());
            double sw = mapper.squareWidth(s.selected());
            g.setColor(new Color(255, 224, 112, Math.round(115 + 38 * s.pulse())));
            g.fill(sp);
            RoyalGlowEffect.paintAmbientSpotlight(g, (float) sc.x, (float) sc.y, (float) (sw * 0.85),
                    new Color(255, 232, 135, Math.round(145 + 45 * s.pulse())));
            g.setColor(new Color(255, 245, 190, Math.round(215 + 35 * s.pulse())));
            g.setStroke(new BasicStroke(2.8f));
            g.draw(sp);
        }

        // Check warning crimson radial glow
        if (s.check()) {
            int ksq = s.position().king(s.position().turn());
            Path2D.Double kp = proj.squarePath(mapper.visualCol(ksq), mapper.visualRow(ksq));
            Point2D.Double kc = mapper.squareCenter(ksq);
            double kw = mapper.squareWidth(ksq);
            g.setColor(new Color(220, 44, 36, Math.round(150 + 45 * s.pulse())));
            g.fill(kp);
            RoyalGlowEffect.paintAmbientSpotlight(g, (float) kc.x, (float) kc.y, (float) (kw * 0.78),
                    new Color(245, 55, 42, Math.round(165 + 45 * s.pulse())));
            g.setColor(new Color(255, 122, 108, 220));
            g.setStroke(new BasicStroke(2.4f));
            g.draw(kp);
        }

        // Legal move indicators (perspective emerald-gold target disc + soft square glow matching reference e5!)
        for (Move candidate : s.candidates()) {
            int to = candidate.to();
            boolean isCapture = s.position().captured(candidate) != null;
            Path2D.Double tp = proj.squarePath(mapper.visualCol(to), mapper.visualRow(to));
            Point2D.Double tc = mapper.squareCenter(to);
            double tw = mapper.squareWidth(to);
            double td = proj.localSquareDepth((mapper.visualRow(to) + 0.5) / 8.0);
            if (isCapture) {
                g.setColor(new Color(235, 185, 72, 62));
                g.fill(tp);
                g.setColor(new Color(248, 212, 102, Math.round(205 + 40 * s.pulse())));
                g.setStroke(new BasicStroke(2.8f));
                g.draw(new Ellipse2D.Double(tc.x - tw * 0.36, tc.y - td * 0.34, tw * 0.72, td * 0.68));
            } else {
                g.setColor(new Color(98, 146, 48, Math.round(86 + 26 * s.pulse())));
                g.fill(tp);
                g.setColor(new Color(142, 198, 72, Math.round(145 + 35 * s.pulse())));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(tp);
                double rx = Math.max(6.0, tw * 0.22);
                double ry = Math.max(4.5, td * 0.24);
                g.setColor(new Color(118, 178, 52, Math.round(185 + 35 * s.pulse())));
                g.fill(new Ellipse2D.Double(tc.x - rx, tc.y - ry, rx * 2, ry * 2));
                g.setColor(new Color(210, 240, 140, Math.round(165 + 45 * s.pulse())));
                g.setStroke(new BasicStroke(1.3f));
                g.draw(new Ellipse2D.Double(tc.x - rx, tc.y - ry, rx * 2, ry * 2));
            }
        }

        // Hint squares
        if (s.hint() != null) {
            fillSquare(g, mapper, s.hint().from(), new Color(112, 227, 156, 62));
            fillSquare(g, mapper, s.hint().to(), new Color(112, 227, 156, 86));
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintCoordinates(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        PerspectiveProjection proj = mapper.projection();
        // Files A-H along the foreshortened front border channel
        Font fileFont = RoyalStyle.sans(Font.BOLD, Math.max(11.5f, (float) (mapper.squareWidthAt(0.95) * 0.165)));
        g.setFont(fileFont);
        FontMetrics ffm = g.getFontMetrics(fileFont);
        g.setColor(new Color(0xE8CE88));
        for (int col = 0; col < 8; col++) {
            String file = String.valueOf((char) (mapper.flipped() ? 'H' - col : 'A' + col));
            Point2D.Double p = proj.project((col + 0.5) / 8.0, 1.0 + PerspectiveCoordinateMapper.FRAME_BOTTOM * 0.54);
            g.drawString(file, (float) (p.x - ffm.stringWidth(file) / 2.0), (float) (p.y + ffm.getAscent() * 0.36));
        }

        // Ranks 1-8 along the sloped left channel (scaled smoothly by depth!)
        for (int row = 0; row < 8; row++) {
            double v = (row + 0.5) / 8.0;
            String rank = String.valueOf((char) (mapper.flipped() ? '1' + row : '8' - row));
            float sz = (float) Math.max(9.5, mapper.squareWidthAt(v) * 0.155);
            Font rf = RoyalStyle.sans(Font.BOLD, sz);
            g.setFont(rf);
            FontMetrics rfm = g.getFontMetrics(rf);
            Point2D.Double p = proj.project(-PerspectiveCoordinateMapper.FRAME_LEFT * 0.48, v);
            g.drawString(rank, (float) (p.x - rfm.stringWidth(rank) / 2.0), (float) (p.y + rfm.getAscent() * 0.35));
        }
    }

    private void fillSquare(Graphics2D g, PerspectiveCoordinateMapper mapper, int sq, Color color) {
        g.setColor(color);
        g.fill(mapper.projection().squarePath(mapper.visualCol(sq), mapper.visualRow(sq)));
    }

    private Dimension estimatePieceBox(Piece piece, double localTileW, int pieceScalePercent) {
        Rectangle src = assets.pieceBounds(piece);
        double userScale = pieceScalePercent / 97.0;
        double targetH = localTileW * 1.16 * RoyalPieceRenderer.heightRatio(piece.kind()) * userScale;
        double maxW = localTileW * 0.88 * userScale;
        double scale = Math.min(targetH / src.height, maxW / (src.width * 1.18));
        return new Dimension(Math.max(4, (int) Math.round(src.width * scale * 1.18)),
                Math.max(4, (int) Math.round(src.height * scale)));
    }

    private static boolean isDark(int sq) { return (Square.file(sq) + Square.rank(sq)) % 2 == 0; }
    private static boolean isOwn(Position pos, int sq) { Piece p = pos.piece(sq); return p != null && p.side() == pos.turn(); }
    private static boolean isCandidate(java.util.List<Move> list, int sq) {
        for (Move m : list) if (m.to() == sq) return true;
        return false;
    }
}
