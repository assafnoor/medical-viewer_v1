package com.medical.viewer.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;

/**
 * Image processing operations for DICOM slices.
 *
 * Includes:
 * - Brightness / Contrast / Gamma
 * - Gaussian Blur, Sharpen, Edge Detection (Laplacian & Sobel)
 * - Histogram Equalization, Invert
 * - Morphological: Erode, Dilate, Open, Close, Gradient
 * - ✅ Threshold Filter (Binary, Inverted, Multi-level, Adaptive, Otsu)
 * - ✅ Band Pass Filter (FFT-based, Butterworth or ideal)
 */
public class ImageProcessor {

    // ══════════════════════════════════════════════════════════════════════════
    // BRIGHTNESS / CONTRAST / GAMMA
    // ══════════════════════════════════════════════════════════════════════════

    public BufferedImage adjustBrightnessContrast(BufferedImage src,
            double brightness,
            double contrast) {
        RescaleOp op = new RescaleOp((float) contrast, (float) brightness, null);
        return op.filter(deepCopy(src), null);
    }

    public BufferedImage gammaCorrection(BufferedImage src, double gamma) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++)
            lut[i] = clamp((int) (Math.pow(i / 255.0, gamma) * 255), 0, 255);
        return applyLUT(src, lut);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONVOLUTION FILTERS
    // ══════════════════════════════════════════════════════════════════════════

    public BufferedImage gaussianBlur(BufferedImage src) {
        float[] k = { 1 / 16f, 2 / 16f, 1 / 16f, 2 / 16f, 4 / 16f, 2 / 16f, 1 / 16f, 2 / 16f, 1 / 16f };
        return applyKernel(src, k, 3);
    }

    public BufferedImage sharpen(BufferedImage src) {
        float[] k = { 0, -1, 0, -1, 5, -1, 0, -1, 0 };
        return applyKernel(src, k, 3);
    }

    public BufferedImage edgeDetection(BufferedImage src) {
        float[] k = { -1, -1, -1, -1, 8, -1, -1, -1, -1 };
        return applyKernel(ensureGrey(src), k, 3);
    }

    public BufferedImage sobelEdge(BufferedImage src) {
        BufferedImage grey = ensureGrey(src);
        int w = grey.getWidth(), h = grey.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float[] kx = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };
        float[] ky = { -1, -2, -1, 0, 0, 0, 1, 2, 1 };
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                float gx = 0, gy = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int px = getGrey(grey, x + dx, y + dy);
                        int ki = (dy + 1) * 3 + (dx + 1);
                        gx += px * kx[ki];
                        gy += px * ky[ki];
                    }
                int mag = clamp((int) Math.sqrt(gx * gx + gy * gy), 0, 255);
                out.setRGB(x, y, rgb(mag, mag, mag));
            }
        }
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HISTOGRAM & INVERT
    // ══════════════════════════════════════════════════════════════════════════

    public BufferedImage histogramEqualize(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight(), total = w * h;
        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[getGrey(src, x, y)]++;
        int[] cdf = new int[256];
        cdf[0] = hist[0];
        for (int i = 1; i < 256; i++)
            cdf[i] = cdf[i - 1] + hist[i];
        int cdfMin = 0;
        for (int i = 0; i < 256; i++) {
            if (cdf[i] > 0) {
                cdfMin = cdf[i];
                break;
            }
        }
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++)
            lut[i] = clamp((int) Math.round(((double) (cdf[i] - cdfMin) / (total - cdfMin)) * 255), 0, 255);
        return applyLUT(src, lut);
    }

    public BufferedImage invert(BufferedImage src) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++)
            lut[i] = 255 - i;
        return applyLUT(src, lut);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MORPHOLOGICAL OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    public BufferedImage dilate(BufferedImage src, int r) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int max = 0;
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++) {
                        int v = getGrey(src, clamp(x + dx, 0, w - 1), clamp(y + dy, 0, h - 1));
                        if (v > max)
                            max = v;
                    }
                out.setRGB(x, y, rgb(max, max, max));
            }
        return out;
    }

    public BufferedImage erode(BufferedImage src, int r) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int min = 255;
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++) {
                        int v = getGrey(src, clamp(x + dx, 0, w - 1), clamp(y + dy, 0, h - 1));
                        if (v < min)
                            min = v;
                    }
                out.setRGB(x, y, rgb(min, min, min));
            }
        return out;
    }

    public BufferedImage morphOpen(BufferedImage src, int r) {
        return dilate(erode(src, r), r);
    }

    public BufferedImage morphClose(BufferedImage src, int r) {
        return erode(dilate(src, r), r);
    }

    public BufferedImage morphGradient(BufferedImage src, int r) {
        BufferedImage d = dilate(src, r), e = erode(src, r);
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = clamp(getGrey(d, x, y) - getGrey(e, x, y), 0, 255);
                out.setRGB(x, y, rgb(v, v, v));
            }
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ✅ THRESHOLD FILTER (5 modes)
    // ══════════════════════════════════════════════════════════════════════════
    //
    // MODE 1 — BINARY
    // pixel >= T → 255 (white)
    // pixel < T → 0 (black)
    // Use: segment bright structures (bone, calcification)
    //
    // MODE 2 — BINARY INVERTED
    // pixel >= T → 0 (black)
    // pixel < T → 255 (white)
    // Use: isolate dark structures (air cavities, vessels)
    //
    // MODE 3 — MULTI-LEVEL (window)
    // pixel in [T_lo, T_hi] → maps linearly to 0–255
    // outside range → clipped to 0 or 255
    // Use: DICOM window/level — show only a specific tissue range
    //
    // MODE 4 — ADAPTIVE (local mean)
    // T(x,y) = mean of neighbourhood - C
    // pixel >= T(x,y) → 255, else 0
    // Use: uneven illumination, normalise across the slice
    //
    // MODE 5 — OTSU (automatic)
    // Finds T automatically by maximising inter-class variance
    // Use: no manual tuning needed; good for bimodal histograms
    //
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Binary threshold.
     * pixel >= threshold → 255, else 0.
     */
    public BufferedImage thresholdBinary(BufferedImage src, int threshold) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = getGrey(src, x, y) >= threshold ? 255 : 0;
                out.setRGB(x, y, rgb(v, v, v));
            }
        return out;
    }

    /**
     * Inverted binary threshold.
     * pixel >= threshold → 0, else 255.
     */
    public BufferedImage thresholdBinaryInverted(BufferedImage src, int threshold) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = getGrey(src, x, y) >= threshold ? 0 : 255;
                out.setRGB(x, y, rgb(v, v, v));
            }
        return out;
    }

    /**
     * Multi-level (window) threshold — like DICOM window/level.
     * Maps [lo, hi] linearly to [0, 255]; clips outside.
     *
     * @param lo Lower bound (anything below → 0)
     * @param hi Upper bound (anything above → 255)
     */
    public BufferedImage thresholdMultiLevel(BufferedImage src, int lo, int hi) {
        if (hi <= lo)
            hi = lo + 1;
        int w = src.getWidth(), h = src.getHeight();
        double range = hi - lo;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int grey = getGrey(src, x, y);
                int v;
                if (grey <= lo)
                    v = 0;
                else if (grey >= hi)
                    v = 255;
                else
                    v = (int) ((grey - lo) / range * 255);
                out.setRGB(x, y, rgb(v, v, v));
            }
        return out;
    }

    /**
     * Adaptive (local mean) threshold.
     * T(x,y) = mean of (blockSize × blockSize) neighbourhood − C
     * pixel >= T(x,y) → 255, else 0.
     *
     * @param blockSize Neighbourhood size (odd number, e.g. 15, 31)
     * @param C         Constant subtracted from mean (bias, e.g. 5)
     */
    public BufferedImage thresholdAdaptive(BufferedImage src, int blockSize, int C) {
        int w = src.getWidth(), h = src.getHeight();
        // Force odd block size
        if (blockSize % 2 == 0)
            blockSize++;
        int half = blockSize / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Compute local mean in neighbourhood
                long sum = 0;
                int count = 0;
                for (int dy = -half; dy <= half; dy++)
                    for (int dx = -half; dx <= half; dx++) {
                        int nx = clamp(x + dx, 0, w - 1);
                        int ny = clamp(y + dy, 0, h - 1);
                        sum += getGrey(src, nx, ny);
                        count++;
                    }
                int localMean = (int) (sum / count) - C;
                int v = getGrey(src, x, y) >= localMean ? 255 : 0;
                out.setRGB(x, y, rgb(v, v, v));
            }
        }
        return out;
    }

    /**
     * Otsu automatic threshold.
     * Finds the optimal threshold by maximising inter-class variance.
     * Returns a binary image — no manual threshold needed.
     *
     * @return Binary image using automatically computed threshold
     */
    public BufferedImage thresholdOtsu(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int total = w * h;

        // Build histogram
        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[getGrey(src, x, y)]++;

        // Compute total mean
        double sum = 0;
        for (int i = 0; i < 256; i++)
            sum += i * hist[i];

        double sumB = 0, wB = 0;
        double maxVariance = 0;
        int optimalT = 0;

        for (int t = 0; t < 256; t++) {
            wB += hist[t]; // weight background
            if (wB == 0)
                continue;
            double wF = total - wB; // weight foreground
            if (wF == 0)
                break;

            sumB += t * hist[t];
            double mB = sumB / wB; // mean background
            double mF = (sum - sumB) / wF; // mean foreground

            // Inter-class variance
            double variance = wB * wF * (mB - mF) * (mB - mF);
            if (variance > maxVariance) {
                maxVariance = variance;
                optimalT = t;
            }
        }

        // Apply the found threshold
        return thresholdBinary(src, optimalT);
    }

    /**
     * Returns the Otsu threshold value (0–255) without applying it.
     * Useful to display the computed value in the UI.
     */
    public int computeOtsuThreshold(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight(), total = w * h;
        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[getGrey(src, x, y)]++;
        double sum = 0;
        for (int i = 0; i < 256; i++)
            sum += i * hist[i];
        double sumB = 0, wB = 0, maxVar = 0;
        int optT = 0;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0 || wB == total)
                continue;
            double wF = total - wB;
            sumB += t * hist[t];
            double mB = sumB / wB, mF = (sum - sumB) / wF;
            double v = wB * wF * (mB - mF) * (mB - mF);
            if (v > maxVar) {
                maxVar = v;
                optT = t;
            }
        }
        return optT;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ✅ BAND PASS FILTER (FFT-based, Butterworth window)
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Theory:
    // 1. Convert image to greyscale double array.
    // 2. Pad to next power-of-2 dimensions for efficient FFT.
    // 3. Apply 2-D DFT (row-wise then column-wise 1-D FFT).
    // 4. Shift DC to center (fftshift).
    // 5. Multiply by Butterworth band-pass mask:
    // H(u,v) = 1 / (1 + (D_lo/D)^2n) × (1 - 1/(1+(D/D_hi)^2n))
    // where D = distance from center, D_lo = low cutoff, D_hi = high cutoff,
    // n = filter order (controls roll-off steepness).
    // 6. Unshift, apply 2-D IDFT, crop, normalise → BufferedImage.
    //
    // Parameters:
    // lowCutoff (0–255): radius below which frequencies are suppressed
    // (removes DC & very low-freq background)
    // highCutoff (0–255): radius above which frequencies are suppressed
    // (removes high-freq noise)
    // order (1–5) : Butterworth filter order (sharpness of transition)
    //
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * FFT-based Butterworth Band Pass Filter.
     *
     * @param src        Input image (any type, internally converted to greyscale)
     * @param lowCutoff  Low frequency cutoff (0–255 pixel-radius in freq. domain)
     * @param highCutoff High frequency cutoff (0–255 pixel-radius in freq. domain)
     * @param order      Butterworth order 1–5 (higher = sharper roll-off)
     * @return Filtered greyscale image
     */
    public BufferedImage bandPassFilter(BufferedImage src,
            double lowCutoff,
            double highCutoff,
            int order) {
        int W = src.getWidth(), H = src.getHeight();

        // ── 1. Extract greyscale pixel values ────────────────────────────────
        double[][] pixels = new double[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                pixels[y][x] = getGrey(src, x, y);

        // ── 2. Pad to power-of-2 size ─────────────────────────────────────────
        int fftW = nextPow2(W), fftH = nextPow2(H);

        // Re and Im parts (real image → Im = 0)
        double[][] re = new double[fftH][fftW];
        double[][] im = new double[fftH][fftW];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                re[y][x] = pixels[y][x];

        // ── 3. Forward 2-D FFT ────────────────────────────────────────────────
        fft2D(re, im, false);

        // ── 4. FFT-shift: move DC component to center ─────────────────────────
        double[][] shiftRe = fftShift(re, fftH, fftW);
        double[][] shiftIm = fftShift(im, fftH, fftW);

        // ── 5. Build Butterworth band-pass mask and multiply ──────────────────
        // Scale cutoffs from [0,255] → actual radius in the padded freq domain.
        // Max possible radius = half of diagonal.
        double maxR = Math.sqrt((double) fftW * fftW + (double) fftH * fftH) / 2.0;
        double D_lo = lowCutoff / 255.0 * maxR;
        double D_hi = highCutoff / 255.0 * maxR;
        // Ensure D_hi > D_lo (safety)
        if (D_hi <= D_lo)
            D_hi = D_lo + 1.0;

        double cx = fftW / 2.0, cy = fftH / 2.0;

        for (int v = 0; v < fftH; v++) {
            for (int u = 0; u < fftW; u++) {
                double D = Math.sqrt((u - cx) * (u - cx) + (v - cy) * (v - cy));
                // Avoid division by zero at DC
                if (D == 0)
                    D = 1e-10;

                // Butterworth high-pass part (passes frequencies > D_lo)
                double Hhi = 1.0 / (1.0 + Math.pow(D_lo / D, 2.0 * order));
                // Butterworth low-pass part (passes frequencies < D_hi)
                double Hlo = 1.0 / (1.0 + Math.pow(D / D_hi, 2.0 * order));

                double Hmask = Hhi * Hlo; // combined band-pass

                shiftRe[v][u] *= Hmask;
                shiftIm[v][u] *= Hmask;
            }
        }

        // ── 6. Inverse FFT-shift ──────────────────────────────────────────────
        double[][] filtRe = ifftShift(shiftRe, fftH, fftW);
        double[][] filtIm = ifftShift(shiftIm, fftH, fftW);

        // ── 7. Inverse 2-D FFT ────────────────────────────────────────────────
        fft2D(filtRe, filtIm, true);

        // ── 8. Crop back to original size, normalise, build BufferedImage ─────
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                if (filtRe[y][x] < minV)
                    minV = filtRe[y][x];
                if (filtRe[y][x] > maxV)
                    maxV = filtRe[y][x];
            }

        double range = (maxV - minV);
        if (range < 1e-10)
            range = 1.0;

        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int g = clamp((int) (((filtRe[y][x] - minV) / range) * 255), 0, 255);
                out.setRGB(x, y, rgb(g, g, g));
            }
        }
        return out;
    }

    /**
     * Returns a visualisation of the FFT magnitude spectrum (log scale).
     * Useful to see which frequencies are present in the image.
     *
     * @param src Input image
     * @return Magnitude spectrum image (DC at center, log-scaled)
     */
    public BufferedImage fftSpectrum(BufferedImage src) {
        int W = src.getWidth(), H = src.getHeight();
        int fftW = nextPow2(W), fftH = nextPow2(H);

        double[][] re = new double[fftH][fftW];
        double[][] im = new double[fftH][fftW];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                re[y][x] = getGrey(src, x, y);

        fft2D(re, im, false);

        double[][] mag = fftShift(re, fftH, fftW); // will overwrite with magnitude
        double[][] imShift = fftShift(im, fftH, fftW);

        // Compute log-magnitude
        double maxLog = -Double.MAX_VALUE;
        double[][] logMag = new double[fftH][fftW];
        for (int v = 0; v < fftH; v++)
            for (int u = 0; u < fftW; u++) {
                double r = mag[v][u], i = imShift[v][u];
                logMag[v][u] = Math.log(1 + Math.sqrt(r * r + i * i));
                if (logMag[v][u] > maxLog)
                    maxLog = logMag[v][u];
            }

        // Render spectrum cropped to original size
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int offX = (fftW - W) / 2, offY = (fftH - H) / 2;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int g = clamp((int) (logMag[y + offY][x + offX] / maxLog * 255), 0, 255);
                out.setRGB(x, y, rgb(g, g, g));
            }
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE — 1-D Cooley–Tukey FFT (recursive, in-place on complex pairs)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 2-D FFT via row-column decomposition.
     * 
     * @param re      Real part [row][col] — modified in-place
     * @param im      Imaginary part — modified in-place
     * @param inverse true for IFFT
     */
    private void fft2D(double[][] re, double[][] im, boolean inverse) {
        int rows = re.length, cols = re[0].length;

        // FFT each row
        double[] rowRe = new double[cols], rowIm = new double[cols];
        for (int y = 0; y < rows; y++) {
            System.arraycopy(re[y], 0, rowRe, 0, cols);
            System.arraycopy(im[y], 0, rowIm, 0, cols);
            fft1D(rowRe, rowIm, inverse);
            System.arraycopy(rowRe, 0, re[y], 0, cols);
            System.arraycopy(rowIm, 0, im[y], 0, cols);
        }

        // FFT each column
        double[] colRe = new double[rows], colIm = new double[rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                colRe[y] = re[y][x];
                colIm[y] = im[y][x];
            }
            fft1D(colRe, colIm, inverse);
            for (int y = 0; y < rows; y++) {
                re[y][x] = colRe[y];
                im[y][x] = colIm[y];
            }
        }
    }

    /**
     * 1-D Cooley–Tukey FFT (iterative, in-place).
     * n must be a power of 2.
     */
    private void fft1D(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        // Bit-reversal permutation
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1)
                j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        // Butterfly
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curRe = 1, curIm = 0;
                for (int j = 0; j < len / 2; j++) {
                    double uRe = re[i + j], uIm = im[i + j];
                    double vRe = re[i + j + len / 2] * curRe - im[i + j + len / 2] * curIm;
                    double vIm = re[i + j + len / 2] * curIm + im[i + j + len / 2] * curRe;
                    re[i + j] = uRe + vRe;
                    im[i + j] = uIm + vIm;
                    re[i + j + len / 2] = uRe - vRe;
                    im[i + j + len / 2] = uIm - vIm;
                    double newCurRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = newCurRe;
                }
            }
        }
        // Normalise inverse
        if (inverse)
            for (int i = 0; i < n; i++) {
                re[i] /= n;
                im[i] /= n;
            }
    }

    /**
     * Circular shift so that DC (frequency 0) moves to the center of the array.
     */
    private double[][] fftShift(double[][] arr, int rows, int cols) {
        double[][] out = new double[rows][cols];
        int hr = rows / 2, hc = cols / 2;
        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++)
                out[(y + hr) % rows][(x + hc) % cols] = arr[y][x];
        return out;
    }

    /** Inverse fftShift — moves DC back to corner (needed before IFFT). */
    private double[][] ifftShift(double[][] arr, int rows, int cols) {
        double[][] out = new double[rows][cols];
        int hr = (rows + 1) / 2, hc = (cols + 1) / 2;
        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++)
                out[(y + hr) % rows][(x + hc) % cols] = arr[y][x];
        return out;
    }

    /** Next power of 2 ≥ n. */
    private int nextPow2(int n) {
        int p = 1;
        while (p < n)
            p <<= 1;
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private BufferedImage applyKernel(BufferedImage src, float[] kernel, int size) {
        BufferedImage rgb = ensureRGB(src);
        return new ConvolveOp(new Kernel(size, size, kernel),
                ConvolveOp.EDGE_NO_OP, null).filter(rgb, null);
    }

    private BufferedImage applyLUT(BufferedImage src, int[] lut) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int g = lut[getGrey(src, x, y)];
                out.setRGB(x, y, rgb(g, g, g));
            }
        return out;
    }

    private int getGrey(BufferedImage img, int x, int y) {
        int c = img.getRGB(x, y);
        // ✅ parentheses required: + has higher precedence than & in Java
        return (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF)) / 3;
    }

    private BufferedImage ensureGrey(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private BufferedImage ensureRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB)
            return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private BufferedImage deepCopy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int rgb(int r, int g, int b) {
        return (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255);
    }
}
