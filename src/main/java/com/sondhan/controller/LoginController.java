package com.sondhan.controller;
import com.sondhan.Main;
import com.sondhan.model.User;
import com.sondhan.service.DatabaseService;
import com.sondhan.service.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Topic 1: FXML Controller for the Login screen (SceneBuilder-compatible).
 * Topic 2: Login runs on a background Task so the UI stays responsive.
 * Topic 3: Delegates credential check to DatabaseService (SQLite SELECT).
 */
public class LoginController {
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginBtn;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML public void initialize() {
        errorLabel.setVisible(false);
        loadingSpinner.setVisible(false);
    }

    @FXML private void handleLogin() {
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();
        if (email.isEmpty() || pass.isEmpty()) { showError("Please fill in all fields."); return; }
        loginBtn.setDisable(true); loadingSpinner.setVisible(true); errorLabel.setVisible(false);

        // Topic 2: JavaFX Task wraps DB work on a background thread
        Task<User> task = new Task<>() {
            @Override protected User call() throws Exception {
                // Topic 3: SQLite SELECT on background thread
                return DatabaseService.getInstance().loginUser(email, pass);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            User u = task.getValue();
            if (u != null) { SessionManager.setCurrentUser(u); go("home.fxml"); }
            else { loginBtn.setDisable(false); showError("Invalid email or password."); }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false); loginBtn.setDisable(false);
            showError("Error: " + task.getException().getMessage());
        }));
        new Thread(task, "login-thread").start();
    }

    @FXML private void handleRegister() { go("register.fxml"); }

    @FXML private void handleGuestMode() {
        SessionManager.setCurrentUser(null); // null = guest
        go("home.fxml");
    }

    private void go(String fxml) { try { Main.navigateTo(fxml); } catch (Exception ex) { ex.printStackTrace(); } }
    private void showError(String m) { errorLabel.setText(m); errorLabel.setVisible(true); }
}