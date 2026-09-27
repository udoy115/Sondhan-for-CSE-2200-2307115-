package com.sondhan.controller;

import com.sondhan.Main;
import com.sondhan.model.ImageBacktrackResult;
import com.sondhan.model.ImageBacktrackResult.Match;
import com.sondhan.service.FactCheckerService;
import com.sondhan.service.ImageBacktrackService;
import com.sondhan.service.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.List;

/**
 * Fully standalone Image Backtracking screen.
 * Has its own drag-and-drop upload zone and Browse button.
 * No dependency on HomeController or any pre-selected image.
 */
public class ImageBacktrackController {

    @FXML private StackPane      dropZone;
    @FXML private VBox           dropHint;
    @FXML private ImageView      previewImage;
    @FXML private Button         clearBtn;
    @FXML private Label          fileNameLabel;
    @FXML private VBox           imageInfoBox;
    @FXML private Label          imageDimLabel;
    @FXML private Button         startBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label          statusLabel;

    // Results area
    @FXML private VBox           emptyState;
    @FXML private ScrollPane     resultsScroll;
    @FXML private Label          earliestSourceLabel;
    @FXML private Label          originFoundLabel;
    @FXML private Label          matchCountLabel;
    @FXML private TextArea       aiAnalysisArea;
    @FXML private VBox           matchesBox;
    @FXML private Label          noMatchLabel;

    private File selectedFile;

    @FXML
    public void initialize() {
        setupDragAndDrop();
    }

    // ── Drag and Drop ────────────────────────────────────────────────────────

    private void setupDragAndDrop() {
        dropZone.setOnDragOver(ev -> {
            if (ev.getDragboard().hasFiles()) ev.acceptTransferModes(TransferMode.COPY);
            ev.consume();
        });
        dropZone.setOnDragDropped(ev -> {
            List<File> files = ev.getDragboard().getFiles();
            if (!files.isEmpty()) loadImage(files.get(0));
            ev.setDropCompleted(true);
            ev.consume();
        });
        dropZone.setOnDragEntered(ev ->
            dropZone.setStyle("-fx-background-color: #F0FDF4; -fx-border-color: #117065; " +
                              "-fx-border-radius: 12; -fx-background-radius: 12; " +
                              "-fx-border-width: 2; -fx-border-style: dashed; -fx-cursor: hand;"));
        dropZone.setOnDragExited(ev ->
            dropZone.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #CBD5E1; " +
                              "-fx-border-radius: 12; -fx-background-radius: 12; " +
                              "-fx-border-width: 2; -fx-border-style: dashed; -fx-cursor: hand;"));
    }

    // ── Image Selection ──────────────────────────────────────────────────────

    @FXML
    private void handleBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select an Image to Backtrack");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"));
        File f = fc.showOpenDialog(Main.getPrimaryStage());
        if (f != null) loadImage(f);
    }

    private void loadImage(File f) {
        selectedFile = f;
        fileNameLabel.setText(f.getName());

        try (FileInputStream fis = new FileInputStream(f)) {
            Image img = new Image(fis);
            previewImage.setImage(img);
            previewImage.setVisible(true);
            dropHint.setVisible(false);
            dropHint.setManaged(false);

            // Show image info
            imageDimLabel.setText(String.format("%.0f × %.0f px  •  %.1f KB",
                img.getWidth(), img.getHeight(), f.length() / 1024.0));
            imageInfoBox.setVisible(true);
            imageInfoBox.setManaged(true);
        } catch (Exception e) {
            previewImage.setVisible(false);
        }

        clearBtn.setVisible(true);
        startBtn.setDisable(false);
        statusLabel.setText("Ready to backtrack.");

        // Reset results to empty state
        showEmptyState();
    }

    @FXML
    private void handleClear() {
        selectedFile = null;
        previewImage.setImage(null);
        previewImage.setVisible(false);
        dropHint.setVisible(true);
        dropHint.setManaged(true);
        fileNameLabel.setText("No file selected");
        clearBtn.setVisible(false);
        startBtn.setDisable(true);
        imageInfoBox.setVisible(false);
        imageInfoBox.setManaged(false);
        statusLabel.setText("");
        showEmptyState();
    }

    // ── Backtrack ────────────────────────────────────────────────────────────

    @FXML
    private void handleStartBacktrack() {
        if (selectedFile == null) return;

        setRunning(true);
        showEmptyState();

        String apiKey = SessionManager.hasApiKey() ? SessionManager.getApiKey() : null;
        File   imgFile = selectedFile;

        Task<ImageBacktrackResult> task = new Task<>() {
            @Override
            protected ImageBacktrackResult call() throws Exception {
                updateMessage("Uploading to Google Lens reverse search...");
                return ImageBacktrackService.backtrack(apiKey, imgFile);
            }
        };

        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            setRunning(false);
            displayResult(task.getValue());
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            setRunning(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Error: " + msg);
            // Show error in results area
            showResultsArea();
            aiAnalysisArea.setText("Backtracking failed:\n\n" + msg +
                "\n\nTip: Check your internet connection.");
            matchCountLabel.setText("0");
            earliestSourceLabel.setText("Error");
            originFoundLabel.setText("Could not complete the backtrack.");
            noMatchLabel.setVisible(true);
            noMatchLabel.setManaged(true);
            matchesBox.getChildren().clear();
        }));

        FactCheckerService.getExecutor().submit(task);
    }

    // ── Display Results ──────────────────────────────────────────────────────

    private void displayResult(ImageBacktrackResult result) {
        showResultsArea();

        // Origin card
        earliestSourceLabel.setText(result.getEarliestSource());
        originFoundLabel.setText(result.isOriginFound()
            ? "This image was found on " + result.getMatches().size() + " web page(s)."
            : "This image was not found in known web indexes — it may be original.");

        matchCountLabel.setText(String.valueOf(
            result.getMatches() == null ? 0 : result.getMatches().size()));

        // AI analysis
        aiAnalysisArea.setText(result.getSummary() != null && !result.getSummary().isBlank()
            ? result.getSummary()
            : "No AI analysis available.\nSet an Anthropic API key to enable AI-powered origin analysis.");

        // Matched pages
        matchesBox.getChildren().clear();
        if (result.getMatches() == null || result.getMatches().isEmpty()) {
            noMatchLabel.setVisible(true);
            noMatchLabel.setManaged(true);
        } else {
            noMatchLabel.setVisible(false);
            noMatchLabel.setManaged(false);
            for (int i = 0; i < result.getMatches().size(); i++) {
                matchesBox.getChildren().add(buildMatchCard(i + 1, result.getMatches().get(i)));
            }
        }

        statusLabel.setText(result.isOriginFound()
            ? "Found " + result.getMatches().size() + " matching page(s)"
            : "No matches found online");
    }

    private VBox buildMatchCard(int index, Match match) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8; " +
                      "-fx-border-color: #E2E8F0; -fx-border-radius: 8; " +
                      "-fx-border-width: 1; -fx-padding: 10 12;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label numLbl = new Label(index + ".");
        numLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 22;");

        Label srcBadge = new Label(match.source);
        srcBadge.setStyle("-fx-background-color: #E1F5FE; -fx-text-fill: #0288D1; " +
                          "-fx-background-radius: 4; -fx-padding: 1 6; -fx-font-size: 11px;");

        Label titleLbl = new Label(match.title);
        titleLbl.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px; -fx-font-weight: bold;");
        titleLbl.setWrapText(true);
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        titleRow.getChildren().addAll(numLbl, srcBadge, titleLbl);

        String displayUrl = match.url.length() > 90 ? match.url.substring(0, 90) + "…" : match.url;
        Hyperlink link = new Hyperlink(displayUrl);
        link.setStyle("-fx-text-fill: #117065; -fx-font-size: 12px; -fx-padding: 0 0 0 30;");
        link.setOnAction(ev -> {
            try { java.awt.Desktop.getDesktop().browse(new URI(match.url)); }
            catch (Exception ignored) {}
        });

        card.getChildren().addAll(titleRow, link);

        if (match.snippet != null && !match.snippet.isBlank()
                && !match.snippet.equals("Found via reverse image search")) {
            Label snippet = new Label(match.snippet);
            snippet.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px; -fx-padding: 0 0 0 30;");
            snippet.setWrapText(true);
            card.getChildren().add(snippet);
        }

        return card;
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private void setRunning(boolean on) {
        startBtn.setDisable(on);
        loadingSpinner.setVisible(on);
    }

    private void showEmptyState() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        resultsScroll.setVisible(false);
        resultsScroll.setManaged(false);
    }

    private void showResultsArea() {
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        resultsScroll.setVisible(true);
        resultsScroll.setManaged(true);
    }

    @FXML
    private void handleBack() {
        try { Main.navigateTo("home.fxml"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
