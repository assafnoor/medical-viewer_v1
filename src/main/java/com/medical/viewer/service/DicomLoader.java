package com.medical.viewer.service;

import com.medical.viewer.model.DicomSeries;
import com.medical.viewer.model.DicomSlice;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class DicomLoader {

    private final Consumer<String> progressCallback;

    public DicomLoader(Consumer<String> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public List<File> collectDicomFiles(File dir) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.exists()) return result;
        File[] files = dir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isDirectory()) result.addAll(collectDicomFiles(f));
            else if (isDicomFile(f)) result.add(f);
        }
        return result;
    }

    private boolean isDicomFile(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".dcm") || n.endsWith(".ima") || n.endsWith(".dicom") || !n.contains(".");
    }

    public DicomSeries loadSeries(List<File> files) throws Exception {
        DicomSeries series = new DicomSeries();
        ImageIO.scanForPlugins();
        int total = files.size(), loaded = 0;
        for (File f : files) {
            try {
                DicomSlice slice = loadSlice(f);
                if (slice != null) series.addSlice(slice);
                loaded++;
                if (progressCallback != null)
                    progressCallback.accept(String.format("Loading %d / %d: %s", loaded, total, f.getName()));
            } catch (Exception e) {
                System.err.println("Skip: " + f.getName() + " → " + e.getMessage());
            }
        }
        series.sort();
        if (!series.isEmpty()) {
            DicomSlice f = series.getSlice(0);
            series.setModality(f.getModality());
            series.setSeriesName(f.getStudyDescription().isEmpty()
                ? f.getModality() + " Study" : f.getStudyDescription());
        }
        return series;
    }

    private DicomSlice loadSlice(File file) throws Exception {
        DicomSlice slice = new DicomSlice(file);
        try (DicomInputStream dis = new DicomInputStream(file)) {
            Attributes attrs = dis.readDataset();
            slice.setPatientName(safe(attrs, Tag.PatientName));
            slice.setModality(safe(attrs, Tag.Modality));
            slice.setStudyDescription(safe(attrs, Tag.StudyDescription));
            slice.setInstanceNumber(attrs.getInt(Tag.InstanceNumber, 0));
            slice.setSliceLocation(attrs.getDouble(Tag.SliceLocation, 0.0));
            slice.setRows(attrs.getInt(Tag.Rows, 0));
            slice.setColumns(attrs.getInt(Tag.Columns, 0));
            slice.setWindowCenter(attrs.getDouble(Tag.WindowCenter, 40));
            slice.setWindowWidth(attrs.getDouble(Tag.WindowWidth, 400));
        }
        BufferedImage img = readDicomImage(file);
        if (img == null) img = ImageIO.read(file);
        if (img != null) {
            BufferedImage grey = toGrey(img);
            slice.setOriginalImage(grey);
            if (slice.getRows() == 0) slice.setRows(grey.getHeight());
            if (slice.getColumns() == 0) slice.setColumns(grey.getWidth());
        }
        return slice;
    }

    private BufferedImage readDicomImage(File file) {
        try {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("DICOM");
            if (!it.hasNext()) it = ImageIO.getImageReadersByFormatName("dcm");
            if (!it.hasNext()) return null;
            ImageReader reader = it.next();
            try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
                reader.setInput(iis);
                return reader.read(0, (DicomImageReadParam) reader.getDefaultReadParam());
            } finally { reader.dispose(); }
        } catch (Exception e) { return null; }
    }

    private BufferedImage toGrey(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(src, out);
        g.dispose();
        return out;
    }

    private String safe(Attributes a, int tag) {
        try { String s = a.getString(tag, ""); return s == null ? "" : s; }
        catch (Exception e) { return ""; }
    }
}
