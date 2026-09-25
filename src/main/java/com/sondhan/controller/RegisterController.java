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
 * Topic 1: FXML Controller for the Register screen.
 * Topic 2: Registration runs on a background Task.
 * Topic 3: Inserts new user into SQLite via DatabaseService.
 */
public class RegisterController {
    @FXML private TextField nameField, emailField;
    @FXML private PasswordField passwordField, confirmPasswordField;
    @FXML private Button registerBtn;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator loadingSpinner;

    @FXML public void initialize() { errorLabel.setVisible(false); loadingSpinner.setVisible(false); }

    @FXML private void handleRegister() {
        String name = nameField.getText().trim(), email = emailField.getText().trim();
        String pass = passwordField.getText(), conf = confirmPasswordField.getText();
        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) { showError("All fields required."); return; }
        if (!pass.equals(conf)) { showError("Passwords do not match."); return; }
        if (pass.length() < 6) { showError("Password must be at least 6 characters."); return; }
        registerBtn.setDisable(true); loadingSpinner.setVisible(true); errorLabel.setVisible(false);

        Task<User> task = new Task<>() {
            @Override protected User call() throws Exception {
                return DatabaseService.getInstance().registerUser(name, email, pass);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false);
            User u = task.getValue();
            if (u != null) { SessionManager.setCurrentUser(u); go("home.fxml"); }
            else { registerBtn.setDisable(false); showError("Registration failed."); }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            loadingSpinner.setVisible(false); registerBtn.setDisable(false);
            String msg = task.getException().getMessage();
            showError(msg != null && msg.contains("UNIQUE") ? "Email already registered." : "Error: " + msg);
        }));
        new Thread(task, "register-thread").start();
    }

    @FXML private void handleBackToLogin() { go("login.fxml"); }
    private void go(String f) { try { Main.navigateTo(f); } catch (Exception ex) { ex.printStackTrace(); } }
    private void showError(String m) { errorLabel.setText(m); errorLabel.setVisible(true); }
}