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
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;

/**
 * Controller for the Image Backtrack screen.
 * Receives the selected image file via a static setter before navigation.
 */
public class ImageBacktrackController {

    // Static handoff — set before navigating to this screen
    private static File pendingImageFile;
    public static void setPendingImage(File f) { pendingImageFile = f; }

    @FXML private ImageView       imagePreview;
    @FXML private Label           fileNameLabel;
    @FXML private Label           earliestSourceLabel;
    @FXML private Label           originFoundLabel;
    @FXML private TextArea        aiAnalysisArea;
    @FXML private VBox            matchesBox;
    @FXML private Label           matchCountLabel;
    @FXML private Label           noMatchLabel;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label           statusLabel;

    @FXML
    public void initialize() {
        File imageFile = pendingImageFile;
        if (imageFile == null) {
            statusLabel.setText("No image provided.");
            loadingSpinner.setVisible(false);
            return;
        }

        // Show image thumbnail
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            imagePreview.setImage(new Image(fis));
        } catch (Exception ignored) {}
        fileNameLabel.setText(imageFile.getName());

        // Set initial UI state
        loadingSpinner.setVisible(true);
        statusLabel.setText("Running reverse image search...");
        earliestSourceLabel.setText("Analysing...");
        aiAnalysisArea.setText("Please wait — contacting Google Lens and Claude AI...");

        // Run backtrack on background thread
        String apiKey = SessionManager.hasApiKey() ? SessionManager.getApiKey() : null;

        Task<ImageBacktrackResult> task = new Task<>() {
            @Override
            protected ImageBacktrackResult call() throws Exception {
                updateMessage("Uploading to Google Lens...");
                return ImageBacktrackService.backtrack(apiKey, imageFile);
            }
        };

        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            loadingSpinner.setVisible(false);
            displayResult(task.getValue());
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            loadingSpinner.setVisible(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Error: " + msg);
            earliestSourceLabel.setText("Error");
            aiAnalysisArea.setText("Backtracking failed:\n\n" + msg +
                "\n\nTip: Check your internet connection and ensure " +
                "the image file is accessible.");
        }));

        FactCheckerService.getExecutor().submit(task);
    }

    private void displayResult(ImageBacktrackResult result) {
        statusLabel.setText(result.isOriginFound() ?
            "Found " + result.getMatches().size() + " matching page(s)" :
            "No matches found online");

        // Earliest origin card
        earliestSourceLabel.setText(result.getEarliestSource());
        originFoundLabel.setText(result.isOriginFound()
            ? "This image was found on " + result.getMatches().size() + " web page(s)."
            : "This image does not appear in known web indexes — may be original.");

        // AI analysis
        aiAnalysisArea.setText(result.getSummary() != null
            ? result.getSummary()
            : "No AI analysis available. Set an Anthropic API key to enable AI-powered origin analysis.");

        // Matched pages list
        matchesBox.getChildren().clear();
        if (result.getMatches() == null || result.getMatches().isEmpty()) {
            noMatchLabel.setVisible(true);
            noMatchLabel.setManaged(true);
            matchCountLabel.setText("");
        } else {
            noMatchLabel.setVisible(false);
            noMatchLabel.setManaged(false);
            matchCountLabel.setText(result.getMatches().size() + " pages");

            for (int i = 0; i < result.getMatches().size(); i++) {
                Match m = result.getMatches().get(i);
                matchesBox.getChildren().add(buildMatchCard(i + 1, m));
            }
        }
    }

    private VBox buildMatchCard(int index, Match match) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8; " +
                      "-fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-border-width: 1; " +
                      "-fx-padding: 10 12;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label numLabel = new Label(index + ".");
        numLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 20;");

        Label sourceLabel = new Label(match.source);
        sourceLabel.setStyle("-fx-background-color: #E1F5FE; -fx-text-fill: #0288D1; " +
                             "-fx-background-radius: 4; -fx-padding: 1 6; -fx-font-size: 11px;");

        Label titleLabel = new Label(match.title);
        titleLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px; -fx-font-weight: bold;");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        titleRow.getChildren().addAll(numLabel, sourceLabel, titleLabel);

        Hyperlink link = new Hyperlink(match.url.length() > 80
            ? match.url.substring(0, 80) + "…"
            : match.url);
        link.setStyle("-fx-text-fill: #117065; -fx-font-size: 12px; -fx-padding: 0 0 0 28;");
        link.setOnAction(ev -> {
            try { java.awt.Desktop.getDesktop().browse(new URI(match.url)); }
            catch (Exception ignored) {}
        });

        card.getChildren().addAll(titleRow, link);

        if (match.snippet != null && !match.snippet.isBlank()
                && !match.snippet.equals("Found via reverse image search")) {
            Label snippet = new Label(match.snippet);
            snippet.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px; -fx-padding: 0 0 0 28;");
            snippet.setWrapText(true);
            card.getChildren().add(snippet);
        }

        return card;
    }

    @FXML
    private void handleBack() {
        try { Main.navigateTo("home.fxml"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
