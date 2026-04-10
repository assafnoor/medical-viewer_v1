package com.medical.viewer.model;

import java.awt.image.BufferedImage;
import java.io.File;

public class DicomSlice implements Comparable<DicomSlice> {

    private final File file;
    private BufferedImage originalImage;
    private BufferedImage processedImage;

    private double sliceLocation = 0.0;
    private int instanceNumber = 0;
    private String patientName = "";
    private String modality = "";
    private String studyDescription = "";
    private int rows;
    private int columns;
    private double windowCenter = 0;
    private double windowWidth = 1000;

    public DicomSlice(File file) { this.file = file; }

    @Override
    public int compareTo(DicomSlice other) {
        if (this.instanceNumber != other.instanceNumber)
            return Integer.compare(this.instanceNumber, other.instanceNumber);
        return Double.compare(this.sliceLocation, other.sliceLocation);
    }

    public File getFile() { return file; }
    public BufferedImage getOriginalImage() { return originalImage; }
    public void setOriginalImage(BufferedImage img) { this.originalImage = img; }
    public BufferedImage getProcessedImage() { return processedImage; }
    public void setProcessedImage(BufferedImage img) { this.processedImage = img; }
    public BufferedImage getDisplayImage() { return processedImage != null ? processedImage : originalImage; }
    public double getSliceLocation() { return sliceLocation; }
    public void setSliceLocation(double v) { this.sliceLocation = v; }
    public int getInstanceNumber() { return instanceNumber; }
    public void setInstanceNumber(int v) { this.instanceNumber = v; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String v) { this.patientName = v; }
    public String getModality() { return modality; }
    public void setModality(String v) { this.modality = v; }
    public String getStudyDescription() { return studyDescription; }
    public void setStudyDescription(String v) { this.studyDescription = v; }
    public int getRows() { return rows; }
    public void setRows(int v) { this.rows = v; }
    public int getColumns() { return columns; }
    public void setColumns(int v) { this.columns = v; }
    public double getWindowCenter() { return windowCenter; }
    public void setWindowCenter(double v) { this.windowCenter = v; }
    public double getWindowWidth() { return windowWidth; }
    public void setWindowWidth(double v) { this.windowWidth = v; }
    public String getFileName() { return file.getName(); }

    @Override
    public String toString() {
        return String.format("[%03d] %s", instanceNumber, file.getName());
    }
}
