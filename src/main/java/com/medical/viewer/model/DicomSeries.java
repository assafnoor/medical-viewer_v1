package com.medical.viewer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DicomSeries {

    private final List<DicomSlice> slices = new ArrayList<>();
    private String seriesName = "Unknown Series";
    private String modality = "";

    public void addSlice(DicomSlice slice) { slices.add(slice); }
    public void sort() { Collections.sort(slices); }
    public List<DicomSlice> getSlices() { return Collections.unmodifiableList(slices); }
    public int getSize() { return slices.size(); }
    public DicomSlice getSlice(int index) { return slices.get(index); }
    public boolean isEmpty() { return slices.isEmpty(); }
    public String getSeriesName() { return seriesName; }
    public void setSeriesName(String n) { this.seriesName = n; }
    public String getModality() { return modality; }
    public void setModality(String m) { this.modality = m; }

    public int[][][] buildVolume() {
        if (slices.isEmpty()) return new int[0][0][0];
        int depth = slices.size();
        int rows = slices.get(0).getRows();
        int cols = slices.get(0).getColumns();
        if (rows == 0 || cols == 0) return new int[0][0][0];
        int[][][] vol = new int[depth][rows][cols];
        for (int z = 0; z < depth; z++) {
            java.awt.image.BufferedImage img = slices.get(z).getDisplayImage();
            if (img == null) continue;
            for (int y = 0; y < Math.min(rows, img.getHeight()); y++)
                for (int x = 0; x < Math.min(cols, img.getWidth()); x++) {
                    int rgb = img.getRGB(x, y);
                    vol[z][y][x] = (rgb >> 16) & 0xFF;
                }
        }
        return vol;
    }

    public String getMetadataInfo() {
        if (slices.isEmpty()) return "No data";
        DicomSlice f = slices.get(0);
        return String.format("Patient: %s | Modality: %s | Study: %s | Slices: %d | Size: %dx%d",
            f.getPatientName(), f.getModality(), f.getStudyDescription(),
            slices.size(), f.getColumns(), f.getRows());
    }
}
