package com.jiexiangtech.chess.desktop;

import java.awt.Polygon;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * Exact 3x3 projective homography mapping canonical board coordinates (u, v) in [0, 1]^2
 * onto a screen-space perspective trapezoid (TL, TR, BR, BL) and its exact inverse.
 */
final class PerspectiveProjection {
    private final double[] h;    // 3x3 forward homography (row-major)
    private final double[] hInv; // 3x3 inverse homography (row-major)

    PerspectiveProjection(Point2D tl, Point2D tr, Point2D br, Point2D bl) {
        this.h = solveUnitSquareToQuad(
                tl.getX(), tl.getY(),
                tr.getX(), tr.getY(),
                br.getX(), br.getY(),
                bl.getX(), bl.getY());
        this.hInv = invert3x3(h);
    }

    Point2D.Double project(double u, double v) {
        double w = h[6] * u + h[7] * v + h[8];
        if (Math.abs(w) < 1e-9) w = 1e-9;
        return new Point2D.Double(
                (h[0] * u + h[1] * v + h[2]) / w,
                (h[3] * u + h[4] * v + h[5]) / w);
    }

    Point2D.Double unproject(double sx, double sy) {
        double w = hInv[6] * sx + hInv[7] * sy + hInv[8];
        if (Math.abs(w) < 1e-9) w = 1e-9;
        return new Point2D.Double(
                (hInv[0] * sx + hInv[1] * sy + hInv[2]) / w,
                (hInv[3] * sx + hInv[4] * sy + hInv[5]) / w);
    }

    /** Fast allocation-free inverse projection for inner texture warping loops. */
    void unprojectInto(double sx, double sy, double[] outUv) {
        double w = hInv[6] * sx + hInv[7] * sy + hInv[8];
        double invW = 1.0 / (Math.abs(w) < 1e-9 ? 1e-9 : w);
        outUv[0] = (hInv[0] * sx + hInv[1] * sy + hInv[2]) * invW;
        outUv[1] = (hInv[3] * sx + hInv[4] * sy + hInv[5]) * invW;
    }

    double localSquareWidth(double v) {
        Point2D.Double a = project(0.4375, v);
        Point2D.Double b = project(0.5625, v);
        return Math.hypot(b.x - a.x, b.y - a.y);
    }

    double localSquareDepth(double v) {
        Point2D.Double a = project(0.5, Math.max(0.0, v - 0.0625));
        Point2D.Double b = project(0.5, Math.min(1.0, v + 0.0625));
        return Math.hypot(b.x - a.x, b.y - a.y);
    }

    Polygon quad(double u0, double v0, double u1, double v1) {
        Point2D.Double p0 = project(u0, v0);
        Point2D.Double p1 = project(u1, v0);
        Point2D.Double p2 = project(u1, v1);
        Point2D.Double p3 = project(u0, v1);
        return new Polygon(
                new int[]{(int) Math.round(p0.x), (int) Math.round(p1.x), (int) Math.round(p2.x), (int) Math.round(p3.x)},
                new int[]{(int) Math.round(p0.y), (int) Math.round(p1.y), (int) Math.round(p2.y), (int) Math.round(p3.y)},
                4);
    }

    Path2D.Double quadPath(double u0, double v0, double u1, double v1) {
        Point2D.Double p0 = project(u0, v0);
        Point2D.Double p1 = project(u1, v0);
        Point2D.Double p2 = project(u1, v1);
        Point2D.Double p3 = project(u0, v1);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p0.x, p0.y);
        path.lineTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(p3.x, p3.y);
        path.closePath();
        return path;
    }

    Polygon squarePolygon(int col, int row) {
        return quad(col / 8.0, row / 8.0, (col + 1) / 8.0, (row + 1) / 8.0);
    }

    Path2D.Double squarePath(int col, int row) {
        return quadPath(col / 8.0, row / 8.0, (col + 1) / 8.0, (row + 1) / 8.0);
    }

    /** Closed-form projective mapping from unit square (0,0)-(1,0)-(1,1)-(0,1) to arbitrary quad. */
    private static double[] solveUnitSquareToQuad(double x0, double y0, double x1, double y1,
                                                  double x2, double y2, double x3, double y3) {
        double dx1 = x1 - x2, dy1 = y1 - y2;
        double dx2 = x3 - x2, dy2 = y3 - y2;
        double sx = x0 - x1 + x2 - x3;
        double sy = y0 - y1 + y2 - y3;
        double det = dx1 * dy2 - dy1 * dx2;
        if (Math.abs(det) < 1e-12) det = 1e-12;
        double g = (sx * dy2 - sy * dx2) / det;
        double h = (dx1 * sy - dy1 * sx) / det;
        double a = x1 - x0 + g * x1;
        double b = x3 - x0 + h * x3;
        double c = x0;
        double d = y1 - y0 + g * y1;
        double e = y3 - y0 + h * y3;
        double f = y0;
        return new double[]{a, b, c, d, e, f, g, h, 1.0};
    }

    private static double[] invert3x3(double[] m) {
        double c00 = m[4] * m[8] - m[5] * m[7];
        double c01 = -(m[3] * m[8] - m[5] * m[6]);
        double c02 = m[3] * m[7] - m[4] * m[6];
        double c10 = -(m[1] * m[8] - m[2] * m[7]);
        double c11 = m[0] * m[8] - m[2] * m[6];
        double c12 = -(m[0] * m[7] - m[1] * m[6]);
        double c20 = m[1] * m[5] - m[2] * m[4];
        double c21 = -(m[0] * m[5] - m[2] * m[3]);
        double c22 = m[0] * m[4] - m[1] * m[3];
        double det = m[0] * c00 + m[1] * c01 + m[2] * c02;
        if (Math.abs(det) < 1e-12) det = 1e-12;
        double inv = 1.0 / det;
        return new double[]{
                c00 * inv, c10 * inv, c20 * inv,
                c01 * inv, c11 * inv, c21 * inv,
                c02 * inv, c12 * inv, c22 * inv
        };
    }
}
