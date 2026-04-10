package com.medical.viewer.controller;

import com.medical.viewer.model.DicomSeries;
import com.medical.viewer.model.DicomSlice;
import com.medical.viewer.renderer.VolumeRenderer;
import com.medical.viewer.service.DicomLoader;
import com.medical.viewer.service.ImageProcessor;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController implements Initializable {

    // ── Canvases ─────────────────────────────────────────────────────────────
    @FXML private Canvas canvasAxial;
    @FXML private Canvas canvas3D;
    @FXML private Canvas canvasCoronal;
    @FXML private Canvas canvasSagittal;

    // ── Lists / navigation ───────────────────────────────────────────────────
    @FXML private ListView<DicomSlice> listSlices;
    @FXML private Slider  sliderSliceNav;
    @FXML private Label   labelSliceNum;

    // ── Brightness / contrast / gamma ────────────────────────────────────────
    @FXML private Slider sliderBrightness, sliderContrast, sliderGamma;
    @FXML private Label  lblBrightness, lblContrast, lblGamma;

    // ── Standard filter toggle buttons ───────────────────────────────────────
    @FXML private ToggleButton btnFilterNone, btnFilterBlur, btnFilterSharpen;
    @FXML private ToggleButton btnFilterEdge, btnFilterSobel;
    @FXML private ToggleButton btnFilterHistEq, btnFilterInvert;

    // ── ✅ Band Pass Filter controls ─────────────────────────────────────────
    @FXML private ToggleButton btnFilterBandPass;
    @FXML private Slider sliderBPFLow, sliderBPFHigh, sliderBPFOrder;
    @FXML private Label  lblBPFLow, lblBPFHigh, lblBPFOrder, lblBPFBand;

    // ── ✅ Threshold Filter controls ─────────────────────────────────────────
    @FXML private ToggleButton btnThreshOff, btnThreshBinary, btnThreshInverted;
    @FXML private ToggleButton btnThreshMulti, btnThreshAdaptive, btnThreshOtsu;
    @FXML private Slider sliderThreshVal, sliderThreshLo, sliderThreshHi, sliderThreshBlock;
    @FXML private Label  lblThreshVal, lblThreshLo, lblThreshHi, lblThreshBlock, lblOtsuT;

    // ── Morphology ────────────────────────────────────────────────────────────
    @FXML private Spinner<Integer> spinnerMorphRadius;

    // ── 3D controls ──────────────────────────────────────────────────────────
    @FXML private Slider   sliderThreshold;
    @FXML private Label    labelThreshold;
    @FXML private CheckBox checkMIP;

    // ── Status ────────────────────────────────────────────────────────────────
    @FXML private Label    labelStatus, labelMeta, labelProgress;
    @FXML private TextArea textMeta;
    @FXML private ProgressBar progressLoad;
    @FXML private Button   btnLoadFolder, btnLoadFiles;

    // ── Services ──────────────────────────────────────────────────────────────
    private final ImageProcessor processor = new ImageProcessor();
    private final VolumeRenderer  renderer  = new VolumeRenderer();
    private final ExecutorService executor  = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dicom-worker"); t.setDaemon(true); return t;
    });

    private DicomSeries currentSeries;
    private int         currentSliceIndex = 0;

    // 3D rotation
    private double angleX = -20, angleY = 30;
    private double dragStartX, dragStartY, dragAngleX, dragAngleY;

    // ── Init ─────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {

        // Live label updates
        sliderBrightness.valueProperty().addListener((o,v,n) -> lblBrightness.setText(String.valueOf(n.intValue())));
        sliderContrast  .valueProperty().addListener((o,v,n) -> lblContrast.setText(String.format("%.2f",n)));
        sliderGamma     .valueProperty().addListener((o,v,n) -> lblGamma.setText(String.format("%.2f",n)));
        sliderThreshold .valueProperty().addListener((o,v,n) -> labelThreshold.setText(String.valueOf(n.intValue())));

        // BPF slider labels + band diagram
        sliderBPFLow .valueProperty().addListener((o,v,n) -> { lblBPFLow.setText(String.valueOf(n.intValue())); updateBPFBandLabel(); });
        sliderBPFHigh.valueProperty().addListener((o,v,n) -> { lblBPFHigh.setText(String.valueOf(n.intValue())); updateBPFBandLabel(); });
        sliderBPFOrder.valueProperty().addListener((o,v,n) -> lblBPFOrder.setText(String.valueOf(n.intValue())));

        // Threshold slider labels
        sliderThreshVal  .valueProperty().addListener((o,v,n) -> lblThreshVal.setText(String.valueOf(n.intValue())));
        sliderThreshLo   .valueProperty().addListener((o,v,n) -> lblThreshLo.setText(String.valueOf(n.intValue())));
        sliderThreshHi   .valueProperty().addListener((o,v,n) -> lblThreshHi.setText(String.valueOf(n.intValue())));
        sliderThreshBlock.valueProperty().addListener((o,v,n) -> lblThreshBlock.setText(String.valueOf(n.intValue())));

        // Slice list selection
        listSlices.getSelectionModel().selectedIndexProperty().addListener((o,ov,nv) -> {
            if (nv != null && nv.intValue() >= 0) { currentSliceIndex = nv.intValue(); updateSliceDisplay(); }
        });

        // Canvas auto-resize
        canvasAxial.sceneProperty().addListener((o,ov,nv) -> {
            if (nv != null) {
                bindCanvas(canvasAxial);
                bindCanvas(canvas3D);
                bindCanvas(canvasCoronal);
                bindCanvas(canvasSagittal);
            }
        });

        drawPlaceholder(canvasAxial,    "Load DICOM folder to begin");
        drawPlaceholder(canvas3D,       "3D Reconstruction\n(drag to rotate)");
        drawPlaceholder(canvasCoronal,  "Coronal view");
        drawPlaceholder(canvasSagittal, "Sagittal view");
    }

    private void bindCanvas(Canvas c) {
        javafx.scene.layout.VBox parent = (javafx.scene.layout.VBox) c.getParent();
        c.widthProperty().bind(parent.widthProperty().subtract(8));
        c.heightProperty().bind(parent.heightProperty().subtract(28));
    }

    // ── Band label diagram ────────────────────────────────────────────────────
    private void updateBPFBandLabel() {
        int lo = (int) sliderBPFLow.getValue();
        int hi = (int) sliderBPFHigh.getValue();
        int totalBars = 20;
        int loBar = Math.round(lo / 255.0f * totalBars);
        int hiBar = Math.round(hi / 255.0f * totalBars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i >= loBar && i <= hiBar) sb.append("▓");
            else sb.append("░");
        }
        lblBPFBand.setText("D_lo=" + lo + "  [" + sb + "]  D_hi=" + hi);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FILE LOADING
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onLoadFolder(ActionEvent e) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select DICOM Folder");
        File dir = dc.showDialog(btnLoadFolder.getScene().getWindow());
        if (dir == null) return;
        setLoadingState(true);
        executor.submit(() -> {
            try {
                DicomLoader loader = new DicomLoader(msg -> Platform.runLater(() -> labelProgress.setText(msg)));
                List<File> files = loader.collectDicomFiles(dir);
                if (files.isEmpty()) {
                    Platform.runLater(() -> { setLoadingState(false); showAlert("No DICOM Files", "No .dcm files found in:\n" + dir); });
                    return;
                }
                DicomSeries series = loader.loadSeries(files);
                Platform.runLater(() -> { setLoadingState(false); onSeriesLoaded(series); });
            } catch (Exception ex) {
                Platform.runLater(() -> { setLoadingState(false); showAlert("Load Error", ex.getMessage()); });
            }
        });
    }

    @FXML private void onLoadFiles(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select DICOM Files");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("DICOM", "*.dcm","*.DCM","*.ima","*.dicom"),
            new FileChooser.ExtensionFilter("All", "*.*"));
        List<File> files = fc.showOpenMultipleDialog(btnLoadFiles.getScene().getWindow());
        if (files == null || files.isEmpty()) return;
        setLoadingState(true);
        executor.submit(() -> {
            try {
                DicomLoader loader = new DicomLoader(msg -> Platform.runLater(() -> labelProgress.setText(msg)));
                DicomSeries series = loader.loadSeries(files);
                Platform.runLater(() -> { setLoadingState(false); onSeriesLoaded(series); });
            } catch (Exception ex) {
                Platform.runLater(() -> { setLoadingState(false); showAlert("Load Error", ex.getMessage()); });
            }
        });
    }

    private void onSeriesLoaded(DicomSeries series) {
        this.currentSeries = series;
        this.currentSliceIndex = 0;
        if (series.isEmpty()) { labelStatus.setText("No valid DICOM slices loaded."); return; }

        listSlices.setItems(FXCollections.observableArrayList(series.getSlices()));
        listSlices.getSelectionModel().selectFirst();
        textMeta.setText(buildMetadataText(series.getSlice(0)));
        labelMeta.setText(series.getMetadataInfo());
        sliderSliceNav.setMax(series.getSize()-1);
        sliderSliceNav.setValue(0);

        executor.submit(() -> {
            renderer.loadVolume(series);
            Platform.runLater(() -> {
                labelStatus.setText("✔ Loaded " + series.getSize() + " slices | " + series.getSeriesName());
                updateSliceDisplay();
                updateMPRViews();
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SLICE DISPLAY
    // ══════════════════════════════════════════════════════════════════════════

    private void updateSliceDisplay() {
        if (currentSeries == null || currentSeries.isEmpty()) return;
        currentSliceIndex = Math.max(0, Math.min(currentSliceIndex, currentSeries.getSize()-1));
        DicomSlice slice = currentSeries.getSlice(currentSliceIndex);
        if (slice.getOriginalImage() == null) { drawPlaceholder(canvasAxial, "No image data"); return; }

        BufferedImage img = applyCurrentProcessing(slice);
        VolumeRenderer.drawToCanvas(canvasAxial, img);
        labelSliceNum.setText((currentSliceIndex+1) + " / " + currentSeries.getSize());
        sliderSliceNav.setValue(currentSliceIndex);
        listSlices.getSelectionModel().select(currentSliceIndex);
        textMeta.setText(buildMetadataText(slice));
    }

    private BufferedImage applyCurrentProcessing(DicomSlice slice) {
        BufferedImage img = slice.getOriginalImage();

        // 1. Brightness / Contrast / Gamma
        double b = sliderBrightness.getValue(), c = sliderContrast.getValue(), g = sliderGamma.getValue();
        if (b != 0 || c != 1.0)  img = processor.adjustBrightnessContrast(img, b, c);
        if (g != 1.0)            img = processor.gammaCorrection(img, g);

        // 2. Active filter
        img = applySelectedFilter(img);

        slice.setProcessedImage(img);
        return img;
    }

    private BufferedImage applySelectedFilter(BufferedImage img) {
        if (isSelected(btnFilterBlur))      return processor.gaussianBlur(img);
        if (isSelected(btnFilterSharpen))   return processor.sharpen(img);
        if (isSelected(btnFilterEdge))      return processor.edgeDetection(img);
        if (isSelected(btnFilterSobel))     return processor.sobelEdge(img);
        if (isSelected(btnFilterHistEq))    return processor.histogramEqualize(img);
        if (isSelected(btnFilterInvert))    return processor.invert(img);

        // ✅ Band Pass Filter
        if (isSelected(btnFilterBandPass)) {
            int lo    = (int) sliderBPFLow.getValue();
            int hi    = (int) sliderBPFHigh.getValue();
            int order = (int) sliderBPFOrder.getValue();
            if (hi <= lo) hi = lo + 1;
            return processor.bandPassFilter(img, lo, hi, order);
        }

        // ✅ Threshold Filter — applied after BPF/other filters if mode != Off
        img = applyThreshold(img);

        return img;
    }

    /**
     * Applies the currently selected threshold mode.
     * Returns img unchanged if mode is "Off".
     */
    private BufferedImage applyThreshold(BufferedImage img) {
        if (isSelected(btnThreshBinary)) {
            int t = (int) sliderThreshVal.getValue();
            return processor.thresholdBinary(img, t);
        }
        if (isSelected(btnThreshInverted)) {
            int t = (int) sliderThreshVal.getValue();
            return processor.thresholdBinaryInverted(img, t);
        }
        if (isSelected(btnThreshMulti)) {
            int lo = (int) sliderThreshLo.getValue();
            int hi = (int) sliderThreshHi.getValue();
            if (hi <= lo) hi = lo + 1;
            return processor.thresholdMultiLevel(img, lo, hi);
        }
        if (isSelected(btnThreshAdaptive)) {
            int block = (int) sliderThreshBlock.getValue();
            int C     = (int) sliderThreshLo.getValue(); // reuse Lo slider as bias C
            return processor.thresholdAdaptive(img, block, C);
        }
        if (isSelected(btnThreshOtsu)) {
            return processor.thresholdOtsu(img);
        }
        return img; // "Off"
    }

    private boolean isSelected(ToggleButton btn) { return btn != null && btn.isSelected(); }

    /** Called when any threshold mode toggle button is pressed. */
    @FXML private void onThreshModeChanged(ActionEvent e) {
        // If Otsu selected, compute and show T value immediately
        if (isSelected(btnThreshOtsu) && currentSeries != null && !currentSeries.isEmpty()) {
            DicomSlice slice = currentSeries.getSlice(currentSliceIndex);
            if (slice.getOriginalImage() != null) {
                int otsuT = processor.computeOtsuThreshold(slice.getOriginalImage());
                lblOtsuT.setText(String.valueOf(otsuT));
            }
        } else {
            lblOtsuT.setText("—");
        }
        updateSliceDisplay();
    }

    /** Called when any threshold parameter slider changes. */
    @FXML private void onThreshParamChanged(InputEvent e) {
        boolean anyThreshActive = isSelected(btnThreshBinary)  || isSelected(btnThreshInverted)
                               || isSelected(btnThreshMulti)   || isSelected(btnThreshAdaptive)
                               || isSelected(btnThreshOtsu);
        if (anyThreshActive) updateSliceDisplay();
    }

    private void updateMPRViews() {
        if (!renderer.hasVolume()) return;
        BufferedImage cor = renderer.getCoronalSlice(renderer.getRows()/2);
        BufferedImage sag = renderer.getSagittalSlice(renderer.getCols()/2);
        if (cor != null) VolumeRenderer.drawToCanvas(canvasCoronal, cor);
        if (sag != null) VolumeRenderer.drawToCanvas(canvasSagittal, sag);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3D RENDERING
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onRender3D(ActionEvent e) {
        if (!renderer.hasVolume()) { showAlert("No Volume", "Load a DICOM series first."); return; }
        renderAndDisplay3D();
    }

    private void renderAndDisplay3D() {
        int thresh = (int)sliderThreshold.getValue(); boolean mip = checkMIP.isSelected();
        double ax = angleX, ay = angleY;
        labelStatus.setText("Rendering 3D…");
        executor.submit(() -> {
            BufferedImage img = renderer.render3DProjection(ax, ay, mip, thresh);
            Platform.runLater(() -> {
                if (img != null) VolumeRenderer.drawToCanvas(canvas3D, img);
                labelStatus.setText("✔ 3D render complete | drag to rotate");
            });
        });
    }

    @FXML private void on3DPress(MouseEvent e)  { dragStartX=e.getX(); dragStartY=e.getY(); dragAngleX=angleX; dragAngleY=angleY; }
    @FXML private void on3DDrag(MouseEvent e)   { angleY=dragAngleY+(e.getX()-dragStartX)*0.5; angleX=dragAngleX-(e.getY()-dragStartY)*0.5; renderAndDisplay3D(); }
    @FXML private void onRenderModeChanged(ActionEvent e) { if (renderer.hasVolume()) renderAndDisplay3D(); }

    // ══════════════════════════════════════════════════════════════════════════
    //  IMAGE PROCESSING EVENTS
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onImageParamChanged(InputEvent e) { updateSliceDisplay(); }
    @FXML private void onFilterChanged(ActionEvent e)    { updateSliceDisplay(); }

    /** ✅ Called when any BPF slider changes — re-renders only if BPF is active. */
    @FXML private void onBPFChanged(InputEvent e) {
        if (isSelected(btnFilterBandPass)) updateSliceDisplay();
    }

    /** ✅ Show FFT magnitude spectrum of current slice in a popup window. */
    @FXML private void onShowSpectrum(ActionEvent e) {
        if (currentSeries == null || currentSeries.isEmpty()) { showAlert("No Image", "Load a DICOM series first."); return; }
        DicomSlice slice = currentSeries.getSlice(currentSliceIndex);
        if (slice.getOriginalImage() == null) return;

        // Compute spectrum in background
        executor.submit(() -> {
            BufferedImage spec = processor.fftSpectrum(slice.getOriginalImage());
            Platform.runLater(() -> showSpectrumPopup(spec));
        });
        labelStatus.setText("Computing FFT spectrum…");
    }

    private void showSpectrumPopup(BufferedImage specImg) {
        if (specImg == null) return;
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.setTitle("FFT Magnitude Spectrum (log scale, DC at center)");
        Canvas c = new Canvas(specImg.getWidth(), specImg.getHeight());
        VolumeRenderer.drawToCanvas(c, specImg);
        javafx.scene.Scene sc = new javafx.scene.Scene(
            new javafx.scene.layout.StackPane(c), specImg.getWidth(), specImg.getHeight());
        sc.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        popup.setScene(sc);
        popup.show();
        labelStatus.setText("✔ FFT Spectrum displayed");
    }

    // Morphological
    @FXML private void onErode(ActionEvent e)        { applyMorphOp("erode"); }
    @FXML private void onDilate(ActionEvent e)       { applyMorphOp("dilate"); }
    @FXML private void onMorphOpen(ActionEvent e)    { applyMorphOp("open"); }
    @FXML private void onMorphClose(ActionEvent e)   { applyMorphOp("close"); }
    @FXML private void onMorphGradient(ActionEvent e){ applyMorphOp("gradient"); }

    private void applyMorphOp(String op) {
        if (currentSeries == null || currentSeries.isEmpty()) return;
        DicomSlice slice = currentSeries.getSlice(currentSliceIndex);
        BufferedImage img = slice.getDisplayImage();
        if (img == null) return;
        int r = spinnerMorphRadius.getValue();
        BufferedImage result = switch (op) {
            case "erode"    -> processor.erode(img, r);
            case "dilate"   -> processor.dilate(img, r);
            case "open"     -> processor.morphOpen(img, r);
            case "close"    -> processor.morphClose(img, r);
            case "gradient" -> processor.morphGradient(img, r);
            default         -> img;
        };
        slice.setProcessedImage(result);
        VolumeRenderer.drawToCanvas(canvasAxial, result);
        labelStatus.setText("Applied: " + op + " (r=" + r + ")");
    }

    @FXML private void onResetImage(ActionEvent e) {
        if (currentSeries == null || currentSeries.isEmpty()) return;
        currentSeries.getSlice(currentSliceIndex).setProcessedImage(null);
        sliderBrightness.setValue(0); sliderContrast.setValue(1.0); sliderGamma.setValue(1.0);
        btnFilterNone.setSelected(true);
        updateSliceDisplay();
        labelStatus.setText("Image reset to original.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onFirstSlice(ActionEvent e) { currentSliceIndex=0; updateSliceDisplay(); }
    @FXML private void onLastSlice(ActionEvent e)  { if(currentSeries!=null)currentSliceIndex=currentSeries.getSize()-1; updateSliceDisplay(); }
    @FXML private void onPrevSlice(ActionEvent e)  { if(currentSliceIndex>0)currentSliceIndex--; updateSliceDisplay(); }
    @FXML private void onNextSlice(ActionEvent e)  { if(currentSeries!=null&&currentSliceIndex<currentSeries.getSize()-1)currentSliceIndex++; updateSliceDisplay(); }

    @FXML private void onSliceNavChanged(MouseEvent e) {
        int idx=(int)sliderSliceNav.getValue();
        if(idx!=currentSliceIndex){currentSliceIndex=idx; updateSliceDisplay();}
    }
    @FXML private void onSliceSelected(MouseEvent e) {
        int idx=listSlices.getSelectionModel().getSelectedIndex();
        if(idx>=0&&idx!=currentSliceIndex){currentSliceIndex=idx;updateSliceDisplay();}
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onExport(ActionEvent e) {
        if(currentSeries==null||currentSeries.isEmpty())return;
        FileChooser fc=new FileChooser();
        fc.setTitle("Export current slice");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG","*.png"));
        fc.setInitialFileName("slice_"+(currentSliceIndex+1)+".png");
        File file=fc.showSaveDialog(canvasAxial.getScene().getWindow());
        if(file==null)return;
        try { ImageIO.write(currentSeries.getSlice(currentSliceIndex).getDisplayImage(),"PNG",file); labelStatus.setText("✔ Exported: "+file.getName()); }
        catch(Exception ex){ showAlert("Export Error",ex.getMessage()); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void drawPlaceholder(Canvas canvas, String text) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#0d1117")); gc.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
        gc.setFill(Color.web("#334155")); gc.setFont(javafx.scene.text.Font.font(13));
        gc.fillText(text, 20, canvas.getHeight()/2);
    }

    private void setLoadingState(boolean v) {
        progressLoad.setVisible(v); progressLoad.setProgress(v?-1:0);
        btnLoadFolder.setDisable(v); btnLoadFiles.setDisable(v);
        if(!v) labelProgress.setText("");
    }

    private String buildMetadataText(DicomSlice s) {
        return String.format("""
            Patient : %s
            Modality: %s
            Study   : %s
            File    : %s
            Size    : %d × %d px
            Instance: %d
            Location: %.2f mm
            Window  : C=%.0f W=%.0f
            """,
            na(s.getPatientName()), na(s.getModality()), na(s.getStudyDescription()),
            s.getFileName(), s.getColumns(), s.getRows(),
            s.getInstanceNumber(), s.getSliceLocation(),
            s.getWindowCenter(), s.getWindowWidth());
    }

    private String na(String s) { return (s==null||s.isBlank())?"N/A":s; }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
