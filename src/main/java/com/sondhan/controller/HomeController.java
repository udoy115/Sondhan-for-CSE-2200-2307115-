package com.sondhan.controller;
import com.sondhan.Main;
import com.sondhan.model.FactCheckResult;
import com.sondhan.service.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.List;

/**
 * Topic 1: Main controller demonstrating layouts, controls, event handling, SceneBuilder FXML.
 * Topic 2: All API/IO runs via JavaFX Task on shared ExecutorService - Platform.runLater() for FX thread.
 * Topic 3: Saves results to SQLite history via DatabaseService.
 * Topic 4: Builds JSON for Claude API; parses response via FactCheckerService.
 */
public class HomeController {
    @FXML private Label  userNameLabel, apiKeyStatusLabel;
    @FXML private TabPane inputTabPane;
    @FXML private Tab    imageTab;
    @FXML private StackPane dropZone;
    @FXML private Label  dropLabel, fileNameLabel;
    @FXML private ImageView previewImage;
    @FXML private Button clearImageBtn, checkBtn;
    @FXML private TextArea textInputArea;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label  loadingLabel;
    @FXML private VBox   emptyState, resultPanel;
    @FXML private Label  claimLabel, verdictLabel, confidencePctLabel, preloadedBadge;
    @FXML private ProgressBar confidenceBar;
    @FXML private TextArea explanationArea;
    @FXML private VBox   sourcesBox, summaryBox;
    @FXML private Label  summaryLabel;

    private File selectedImageFile;

    @FXML public void initialize() {
        userNameLabel.setText(SessionManager.isGuest() ? "Guest Mode"
            : "Hi, " + SessionManager.getCurrentUser().getName());
        updateApiKeyStatus();
        resultPanel.setVisible(false);  resultPanel.setManaged(false);
        emptyState.setVisible(true);    emptyState.setManaged(true);
        loadingSpinner.setVisible(false);
        loadingLabel.setVisible(false);
        clearImageBtn.setVisible(false);
        preloadedBadge.setVisible(false); preloadedBadge.setManaged(false);
        summaryBox.setVisible(false);     summaryBox.setManaged(false);
        setupDragAndDrop();

        // Topic 2: Init preloaded DB on background ExecutorService thread
        Task<Void> preloadTask = new Task<>() {
            @Override protected Void call() {
                PreloadedDatabase.getInstance().initialize(
                    System.getProperty("user.dir") + File.separator + "preloaded");
                return null;
            }
        };
        FactCheckerService.getExecutor().submit(preloadTask);
    }

    private void setupDragAndDrop() {
        dropZone.setOnDragOver(ev -> {
            if (ev.getDragboard().hasFiles()) ev.acceptTransferModes(TransferMode.COPY);
            ev.consume();
        });
        dropZone.setOnDragDropped(ev -> {
            List<File> files = ev.getDragboard().getFiles();
            if (!files.isEmpty()) setImage(files.get(0));
            ev.setDropCompleted(true); ev.consume();
        });
        dropZone.setOnDragEntered(ev -> dropZone.setStyle("-fx-border-color:#7c3aed;-fx-background-color:rgba(124,58,237,0.1);"));
        dropZone.setOnDragExited(ev -> dropZone.setStyle(""));
    }

    @FXML private void handleBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select an Image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images","*.jpg","*.jpeg","*.png","*.gif","*.webp"));
        File f = fc.showOpenDialog(Main.getPrimaryStage());
        if (f != null) setImage(f);
    }

    private void setImage(File f) {
        selectedImageFile = f;
        fileNameLabel.setText(f.getName());
        try (FileInputStream fis = new FileInputStream(f)) {
            previewImage.setImage(new Image(fis));
            previewImage.setVisible(true);
        } catch (Exception ignored) { previewImage.setVisible(false); }
        dropLabel.setVisible(false);
        clearImageBtn.setVisible(true);
    }

    @FXML private void handleClearImage() {
        selectedImageFile = null;
        fileNameLabel.setText("No file selected");
        previewImage.setImage(null); previewImage.setVisible(false);
        dropLabel.setVisible(true); clearImageBtn.setVisible(false);
    }

    @FXML private void handleApiKey() {
        TextInputDialog dlg = new TextInputDialog(SessionManager.getApiKey());
        dlg.setTitle("Anthropic API Key");
        dlg.setHeaderText("Enter your Anthropic API key");
        dlg.setContentText("Key (sk-ant-...):");
        dlg.getDialogPane().setPrefWidth(480);
        dlg.showAndWait().ifPresent(key -> { SessionManager.setApiKey(key); updateApiKeyStatus(); });
    }

    private void updateApiKeyStatus() {
        if (SessionManager.hasApiKey()) {
            apiKeyStatusLabel.setText("API Key Set");
            apiKeyStatusLabel.setStyle("-fx-text-fill:#4ade80;");
        } else {
            apiKeyStatusLabel.setText("No API Key");
            apiKeyStatusLabel.setStyle("-fx-text-fill:#f59e0b;");
        }
    }

    @FXML private void handleCheck() {
        boolean isImg = inputTabPane.getSelectionModel().getSelectedItem() == imageTab;
        if (isImg && selectedImageFile == null) { alert("No Image","Please select an image first."); return; }
        if (!isImg && textInputArea.getText().trim().isEmpty()) { alert("No Claim","Please enter a claim to fact-check."); return; }
        setLoading(true); hideResult();
        if (isImg) runImageCheck();
        else runTextCheck(textInputArea.getText().trim());
    }

    // Topic 2: Image check Task - runs on ExecutorService
    private void runImageCheck() {
        File img = selectedImageFile;
        Task<FactCheckResult> task = new Task<>() {
            @Override protected FactCheckResult call() throws Exception {
                updateMessage("Checking preloaded database...");
                FactCheckResult pre = PreloadedDatabase.getInstance().match(img);
                if (pre != null) { Thread.sleep(1500); return pre; }
                updateMessage("Calling Claude AI (image analysis)...");
                if (!SessionManager.hasApiKey())
                    throw new RuntimeException("No API key set. Click the API Key button.");
                return FactCheckerService.checkImageClaim(SessionManager.getApiKey(), img);
            }
        };
        wireTask(task, "image", img.getName());
    }

    // Topic 2: Text check Task - runs on ExecutorService
    private void runTextCheck(String claim) {
        Task<FactCheckResult> task = new Task<>() {
            @Override protected FactCheckResult call() throws Exception {
                updateMessage("Checking preloaded database...");
                FactCheckResult pre = PreloadedDatabase.getInstance().matchText(claim);
                if (pre != null) { Thread.sleep(1500); return pre; }

                updateMessage("Calling Claude AI...");
                if (!SessionManager.hasApiKey())
                    throw new RuntimeException("No API key set. Click the API Key button.");
                return FactCheckerService.checkTextClaim(SessionManager.getApiKey(), claim);
            }
        };
        wireTask(task, "text", claim);
    }

    // Topic 2: Bind Task message to UI label; callbacks on FX thread via Platform.runLater()
    private void wireTask(Task<FactCheckResult> task, String type, String orig) {
        loadingLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingLabel.textProperty().unbind();
            setLoading(false);
            displayResult(task.getValue(), type, orig);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            loadingLabel.textProperty().unbind();
            setLoading(false);
            alert("Error", task.getException().getMessage());
        }));
        FactCheckerService.getExecutor().submit(task);
    }

    // Topic 1: Populate result panel controls
    private void displayResult(FactCheckResult r, String type, String orig) {
        claimLabel.setText(r.getClaim());
        verdictLabel.setText(r.getVerdict());
        verdictLabel.getStyleClass().removeAll("verdict-true","verdict-false","verdict-misleading","verdict-unverified");
        verdictLabel.getStyleClass().add(switch (r.getVerdict()) {
            case "TRUE"       -> "verdict-true";
            case "FALSE"      -> "verdict-false";
            case "MISLEADING" -> "verdict-misleading";
            default           -> "verdict-unverified";
        });
        confidenceBar.setProgress(r.getConfidence() / 100.0);
        confidencePctLabel.setText(r.getConfidence() + "%");
        explanationArea.setText(r.getExplanation());
        preloadedBadge.setVisible(r.isPreloaded()); preloadedBadge.setManaged(r.isPreloaded());

        sourcesBox.getChildren().clear();
        if (r.getSources() != null) {
            for (FactCheckResult.Source src : r.getSources()) {
                Hyperlink lnk = new Hyperlink(">" + src.title);
                lnk.setStyle("-fx-text-fill:#818cf8;-fx-font-size:13px;");
                lnk.setOnAction(ev -> {
                    try { java.awt.Desktop.getDesktop().browse(new URI(src.url)); }
                    catch (Exception ignored) {}
                });
                sourcesBox.getChildren().add(lnk);
            }
        }

        if (r.isPreloaded() && r.getSummary() != null) {
            summaryLabel.setText(String.join("\n", r.getSummary()));
            summaryBox.setVisible(true); summaryBox.setManaged(true);
        } else {
            genSummary(r);
        }

        // Topic 3: Save to SQLite on background thread
        if (!SessionManager.isGuest()) saveToDb(r, type, orig);
        showResult();
    }

    private void genSummary(FactCheckResult r) {
        summaryLabel.setText("Generating summary...");
        summaryBox.setVisible(true); summaryBox.setManaged(true);
        Task<String> st = new Task<>() {
            @Override protected String call() throws Exception {
                if (!SessionManager.hasApiKey()) return fallbackSummary(r);
                try { return FactCheckerService.generateSummary(SessionManager.getApiKey(),
                    r.getClaim(), r.getVerdict(), r.getExplanation(), r.getSources()); }
                catch (Exception e) { return fallbackSummary(r); }
            }
        };
        st.setOnSucceeded(e -> Platform.runLater(() -> summaryLabel.setText(st.getValue())));
        st.setOnFailed(e    -> Platform.runLater(() -> summaryLabel.setText(fallbackSummary(r))));
        FactCheckerService.getExecutor().submit(st);
    }

    private String fallbackSummary(FactCheckResult r) {
        return "1. Claim: \"" + r.getClaim() + "\".\n2. Verdict: " + r.getVerdict() +
            ".\n3. " + r.getExplanation() + "\n4. Confidence: " + r.getConfidence() + "%.";
    }

    // Topic 3: INSERT into SQLite searches table on background thread
    private void saveToDb(FactCheckResult r, String type, String orig) {
        int uid = SessionManager.getCurrentUser().getId();
        JSONArray arr = new JSONArray();
        if (r.getSources() != null) for (FactCheckResult.Source s : r.getSources())
            arr.put(new JSONObject().put("title",s.title).put("url",s.url));
        String srcJson = arr.toString();
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception {
                DatabaseService.getInstance().saveSearch(uid, type, orig,
                    r.getClaim(), r.getVerdict(), r.getConfidence(),
                    r.getExplanation(), srcJson, r.isPreloaded());
                return null;
            }
        };
        FactCheckerService.getExecutor().submit(t);
    }

    @FXML private void handleHistory() { go("history.fxml"); }
    @FXML private void handleLogout()  { SessionManager.setCurrentUser(null); go("login.fxml"); }

    private void setLoading(boolean on) {
        checkBtn.setDisable(on); loadingSpinner.setVisible(on); loadingLabel.setVisible(on);
        if (!on) loadingLabel.textProperty().unbind();
    }
    private void showResult() {
        emptyState.setVisible(false); emptyState.setManaged(false);
        resultPanel.setVisible(true); resultPanel.setManaged(true);
    }
    private void hideResult() {
        resultPanel.setVisible(false); resultPanel.setManaged(false);
        emptyState.setVisible(true);   emptyState.setManaged(true);
    }
    @FXML private void handleClearResult() {
        hideResult();
        textInputArea.clear();
        handleClearImage();
    }
    private void alert(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(t); a.setContentText(m); a.showAndWait();
    }
    private void go(String f) { try { Main.navigateTo(f); } catch (Exception ex) { ex.printStackTrace(); } }
}