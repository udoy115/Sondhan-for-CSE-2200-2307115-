package com.sondhan;
import com.sondhan.service.DatabaseService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.Objects;

public class Main extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        try {
            DatabaseService.getInstance().initialize();
        } catch (Exception e) {
            System.err.println("[DB Error] " + e.getMessage());
            e.printStackTrace();
        }
        try {
            navigateTo("login.fxml", 900, 650);
        } catch (Exception e) {
            System.err.println("[FXML Error loading login.fxml] " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        stage.setTitle("Sondhan - AI Fact Checker");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.show();
    }

    public static void navigateTo(String fxml, double w, double h) throws Exception {
        java.net.URL url = Main.class.getResource("/com/sondhan/" + fxml);
        if (url == null) throw new RuntimeException("FXML not found: /com/sondhan/" + fxml);
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        Scene scene = new Scene(root, w, h);
        java.net.URL cssUrl = Main.class.getResource("/com/sondhan/styles.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        primaryStage.setScene(scene);
    }

    public static void navigateTo(String fxml) throws Exception {
        double w = primaryStage.getScene() != null ? primaryStage.getScene().getWidth()  : 900;
        double h = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : 650;
        navigateTo(fxml, w, h);
    }

    public static Stage getPrimaryStage() { return primaryStage; }

    @Override
    public void stop() {
        DatabaseService.getInstance().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}