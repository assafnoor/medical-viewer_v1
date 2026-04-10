package com.medical.viewer.renderer;

import com.medical.viewer.model.DicomSeries;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.awt.image.BufferedImage;

public class VolumeRenderer {

    private int[][][] volume;
    private int depth, rows, cols;

    public void loadVolume(DicomSeries series) {
        this.volume = series.buildVolume();
        if (volume.length == 0)
            return;
        this.depth = volume.length;
        this.rows = volume[0].length;
        this.cols = volume[0][0].length;
    }

    public BufferedImage render3DProjection(double angleX, double angleY,
            boolean mip, int thresh) {
        if (volume == null || depth == 0)
            return null;
        int outW = 512, outH = 512;
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        double cx = depth / 2.0, cy = rows / 2.0, cz = cols / 2.0;
        double cosX = Math.cos(Math.toRadians(angleX));
        double sinX = Math.sin(Math.toRadians(angleX));
        double cosY = Math.cos(Math.toRadians(angleY));
        double sinY = Math.sin(Math.toRadians(angleY));
        double scale = Math.min(outW / (double) Math.max(cols, depth),
                outH / (double) Math.max(rows, depth)) * 0.8;
        int[] maxVal = new int[outW * outH];
        double[] depthBuf = new double[outW * outH];
        java.util.Arrays.fill(depthBuf, Double.MAX_VALUE);

        for (int z = 0; z < depth; z++)
            for (int y = 0; y < rows; y++)
                for (int x = 0; x < cols; x++) {
                    int val = volume[z][y][x];
                    if (val < thresh && !mip)
                        continue;
                    double dx = x - cz, dy = y - cy, dz = z - cx;
                    double rx = dx * cosY + dz * sinY, ry = dy;
                    double rz = -dx * sinY + dz * cosY;
                    double ry2 = ry * cosX - rz * sinX;
                    double rz2 = ry * sinX + rz * cosX;
                    int px = (int) (rx * scale + outW / 2.0);
                    int py = (int) (ry2 * scale + outH / 2.0);
                    if (px < 0 || px >= outW || py < 0 || py >= outH)
                        continue;
                    int idx = py * outW + px;
                    if (mip) {
                        if (val > maxVal[idx])
                            maxVal[idx] = val;
                    } else {
                        if (rz2 < depthBuf[idx]) {
                            depthBuf[idx] = rz2;
                            maxVal[idx] = val;
                        }
                    }
                }

        for (int py = 0; py < outH; py++)
            for (int px = 0; px < outW; px++) {
                int val = maxVal[py * outW + px];
                if (val > 0) {
                    double shade = mip ? 1.0
                            : Math.max(0.3,
                                    1.0 - depthBuf[py * outW + px] / (depth * 1.5));
                    int r = Math.min(255, (int) (val * shade));
                    int g = Math.min(255, (int) (val * shade * 0.85));
                    int b = Math.min(255, (int) (val * shade * 0.6));
                    out.setRGB(px, py, (r << 16) | (g << 8) | b);
                }
            }
        return out;
    }

    public BufferedImage getAxialSlice(int z) {
        if (volume == null || z < 0 || z >= depth)
            return null;
        BufferedImage out = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++) {
                int v = volume[z][y][x];
                out.setRGB(x, y, grey(v));
            }
        return out;
    }

    public BufferedImage getCoronalSlice(int yIdx) {
        if (volume == null || yIdx < 0 || yIdx >= rows)
            return null;
        BufferedImage out = new BufferedImage(cols, depth, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < depth; z++)
            for (int x = 0; x < cols; x++) {
                int v = volume[z][yIdx][x];
                out.setRGB(x, z, grey(v));
            }
        return out;
    }

    public BufferedImage getSagittalSlice(int xIdx) {
        if (volume == null || xIdx < 0 || xIdx >= cols)
            return null;
        BufferedImage out = new BufferedImage(rows, depth, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < depth; z++)
            for (int y = 0; y < rows; y++) {
                int v = volume[z][y][xIdx];
                out.setRGB(y, z, grey(v));
            }
        return out;
    }

    private int grey(int v) {
        return (v << 16) | (v << 8) | v;
    }

    public static void drawToCanvas(Canvas canvas, BufferedImage img) {
        if (img == null)
            return;
        double cw = canvas.getWidth(), ch = canvas.getHeight();
        double scale = Math.min(cw / img.getWidth(), ch / img.getHeight());
        int dw = (int) (img.getWidth() * scale), dh = (int) (img.getHeight() * scale);
        int ox = (int) ((cw - dw) / 2), oy = (int) ((ch - dh) / 2);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, cw, ch);
        gc.drawImage(bufferedToFX(img), ox, oy, dw, dh);
    }

    public static javafx.scene.image.WritableImage bufferedToFX(BufferedImage buf) {
        int w = buf.getWidth(), h = buf.getHeight();
        javafx.scene.image.WritableImage wi = new javafx.scene.image.WritableImage(w, h);
        javafx.scene.image.PixelWriter pw = wi.getPixelWriter();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                pw.setArgb(x, y, 0xFF000000 | (buf.getRGB(x, y) & 0xFFFFFF));
        return wi;
    }

    public boolean hasVolume() {
        return volume != null && depth > 0;
    }

    public int getDepth() {
        return depth;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
}
