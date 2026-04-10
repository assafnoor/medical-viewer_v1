package com.medical.viewer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/com/medical/viewer/main.fxml")
        );
        Scene scene = new Scene(loader.load(), 1440, 920);
        scene.getStylesheets().add(
            getClass().getResource("/css/style.css").toExternalForm()
        );
        stage.setTitle("🧠 Medical DICOM Viewer — 3D Reconstruction");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
