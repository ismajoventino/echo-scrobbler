package com.echoscrobbler;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class App extends Application {

    private Label nowPlayingLabel;
    private Label statusLabel;
    
    private LastFmClient lastFmClient;
    private String lastDetectedTrack = "";
    
    private long trackStartTime = 0;
    private long secondsListened = 0;
    private boolean scrobbleSent = false;
    private double scrobbleThreshold = 0.5;

    @Override
    public void start(Stage primaryStage) {
        lastFmClient = new LastFmClient();

        nowPlayingLabel = new Label("Waiting for music...");
        nowPlayingLabel.getStyleClass().add("song-label");
        
        statusLabel = new Label("Status: Idle");
        statusLabel.getStyleClass().add("status-label");

        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.getChildren().addAll(nowPlayingLabel, statusLabel);

        VBox root = new VBox();
        root.getStyleClass().add("main-container");
        root.getChildren().add(card);

        Scene scene = new Scene(root, 450, 350);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Echo Scrobbler");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 1-second interval for tracking logic
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateLogic()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateLogic() {
        try {
            ProcessBuilder builder = new ProcessBuilder("playerctl", "metadata", "--format", 
                "{{ artist }}|||{{ title }}|||{{ album }}");
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String rawLine = reader.readLine();

            if (rawLine != null && !rawLine.trim().isEmpty()) {
                String[] parts = rawLine.split("\\|\\|\\|");
                String artist = parts[0].trim();
                String title = parts[1].trim();
                String album = parts.length > 2 ? parts[2].trim() : "Unknown Album";

                // New track detection
                if (!rawLine.equals(lastDetectedTrack)) {
                    lastDetectedTrack = rawLine;
                    trackStartTime = System.currentTimeMillis() / 1000;
                    secondsListened = 0;
                    scrobbleSent = false;
                    
                    nowPlayingLabel.setText(artist + "\n" + title);
                    lastFmClient.updateNowPlaying(artist, title, album);
                    System.out.println("Now Playing: " + title);
                }

                secondsListened++;
                
                // Scrobble trigger (standard 90s target)
                int scrobbleTarget = 90; 

                if (!scrobbleSent && secondsListened >= scrobbleTarget) {
                    lastFmClient.scrobble(artist, title, album, trackStartTime);
                    scrobbleSent = true;
                    statusLabel.setText("Scrobbled!");
                    System.out.println("Scrobble sent: " + title);
                } else if (!scrobbleSent) {
                    statusLabel.setText("Listening: " + secondsListened + "s / Target: " + scrobbleTarget + "s");
                }

            } else {
                nowPlayingLabel.setText("No Media Detected");
                statusLabel.setText("Status: Idle");
            }
        } catch (Exception e) {
            
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}