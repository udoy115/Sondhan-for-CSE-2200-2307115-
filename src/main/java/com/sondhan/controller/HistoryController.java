package com.sondhan.controller;
import com.sondhan.Main;
import com.sondhan.model.SearchHistory;
import com.sondhan.service.DatabaseService;
import com.sondhan.service.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Topic 1: TableView, selection listeners, event handlers, SceneBuilder layout.
 * Topic 2: History loading on a background Task; DELETE also on background thread.
 * Topic 3: SELECT and DELETE from SQLite searches table.
 */
public class HistoryController {
    @FXML private Label userNameLabel;
    @FXML private TableView<SearchHistory>           historyTable;
    @FXML private TableColumn<SearchHistory, String> dateCol, typeCol, claimCol, verdictCol;
    @FXML private TableColumn<SearchHistory, Integer> confCol;
    @FXML private VBox   detailPanel;
    @FXML private Label  detailClaimLabel, detailVerdictLabel, detailSourcesLabel;
    @FXML private TextArea detailExplanationArea;
    @FXML private Label  emptyLabel;
    @FXML private ProgressIndicator loadingSpinner;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @FXML public void initialize() {
        userNameLabel.setText(SessionManager.isGuest()
            ? "History (Sign in to save)" : SessionManager.getCurrentUser().getName() + "'s History");

        // Topic 1: Set up TableView columns
        dateCol.setCellValueFactory(c -> {
            String d = c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt().format(FMT) : "-";
            return new javafx.beans.property.SimpleStringProperty(d);
        });
        typeCol.setCellValueFactory(new PropertyValueFactory<>("inputType"));
        claimCol.setCellValueFactory(new PropertyValueFactory<>("claim"));
        verdictCol.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        confCol.setCellValueFactory(new PropertyValueFactory<>("confidence"));

        verdictCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch(item){
                    case "TRUE"       -> "-fx-text-fill:#4ade80;-fx-font-weight:bold;";
                    case "FALSE"      -> "-fx-text-fill:#f87171;-fx-font-weight:bold;";
                    case "MISLEADING" -> "-fx-text-fill:#fbbf24;-fx-font-weight:bold;";
                    default           -> "-fx-text-fill:#94a3b8;";
                });
            }
        });

        claimCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.length() > 60 ? item.substring(0,57)+"..." : item);
            }
        });

        historyTable.getSelectionModel().selectedItemProperty().addListener(
            (obs,old,sel) -> { if (sel != null) showDetail(sel); });

        detailPanel.setVisible(false); detailPanel.setManaged(false);
        loadHistory();
    }

    // Topic 2: Load history on background Task
    private void loadHistory() {
        loadingSpinner.setVisible(true);
        if (SessionManager.isGuest()) {
            loadingSpinner.setVisible(false); emptyLabel.setVisible(true); return;
        }
        int uid = SessionManager.getCurrentUser().getId();
        Task<List<SearchHistory>> task = new Task<>() {
            @Override protected List<SearchHistory> call() throws Exception {
                // Topic 3: SQLite SELECT
                return DatabaseService.getInstance().getSearchHistory(uid);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            List<SearchHistory> list = task.getValue();
            if (list.isEmpty()) emptyLabel.setVisible(true);
            else { emptyLabel.setVisible(false); historyTable.setItems(FXCollections.observableArrayList(list)); }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            emptyLabel.setText("Failed: " + task.getException().getMessage());
            emptyLabel.setVisible(true);
        }));
        new Thread(task, "history-load").start();
    }

    private void showDetail(SearchHistory h) {
        detailClaimLabel.setText(h.getClaim() != null ? h.getClaim() : "-");
        detailVerdictLabel.setText(h.getVerdict() != null ? h.getVerdict() : "-");
        detailVerdictLabel.setStyle(switch(h.getVerdict() != null ? h.getVerdict() : ""){
            case "TRUE"       -> "-fx-text-fill:#4ade80;-fx-font-weight:bold;";
            case "FALSE"      -> "-fx-text-fill:#f87171;-fx-font-weight:bold;";
            case "MISLEADING" -> "-fx-text-fill:#fbbf24;-fx-font-weight:bold;";
            default           -> "-fx-text-fill:#94a3b8;";
        });
        detailExplanationArea.setText(h.getExplanation() != null ? h.getExplanation() : "-");
        detailExplanationArea.setWrapText(true);
        detailSourcesLabel.setText(h.getSourcesJson() != null ? h.getSourcesJson() : "[]");
        detailPanel.setVisible(true); detailPanel.setManaged(true);
    }

    @FXML private void handleDeleteSelected() {
        SearchHistory sel = historyTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Delete"); c.setContentText("Delete this fact-check from history?");
        c.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                // Topic 3: DELETE from SQLite on background thread (Topic 2)
                Task<Void> del = new Task<>() {
                    @Override protected Void call() throws Exception {
                        DatabaseService.getInstance().deleteSearch(sel.getId()); return null;
                    }
                };
                del.setOnSucceeded(e -> Platform.runLater(() -> {
                    historyTable.getItems().remove(sel);
                    detailPanel.setVisible(false); detailPanel.setManaged(false);
                }));
                new Thread(del,"delete-thread").start();
            }
        });
    }

    @FXML private void handleRefresh() { historyTable.getItems().clear(); loadHistory(); }
    @FXML private void handleBack()    { try { Main.navigateTo("home.fxml"); } catch(Exception ex){ex.printStackTrace();} }
}