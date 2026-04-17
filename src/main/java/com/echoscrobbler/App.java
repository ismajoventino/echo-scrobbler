package com.echoscrobbler;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.echoscrobbler.controller.DashboardController;
import com.echoscrobbler.controller.LoginController;
import com.echoscrobbler.model.Track;
import com.echoscrobbler.service.AuthService;
import com.echoscrobbler.service.LastFmClient;
import com.echoscrobbler.service.LastFmService;
import com.echoscrobbler.service.ScrobbleTimer;

import io.github.cdimascio.dotenv.Dotenv;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    private LastFmClient lastFmClient;
    private Track currentTrack;
    private final ScrobbleTimer scrobbleTimer = new ScrobbleTimer();
    private AuthService authService;
    private TrayIcon trayIcon;

    @Override
    public void start(Stage primaryStage) {
        Dotenv dotenv = Dotenv.load();
        authService = new AuthService(dotenv.get("LASTFM_API_KEY"), dotenv.get("LASTFM_SHARED_SECRET"));
        lastFmClient = new LastFmClient(authService.getSessionKey());

        Platform.setImplicitExit(false);

        setupTray(primaryStage);

        if (authService.isAuthenticated()) {
            showDashboard(primaryStage);
        } else {
            showLogin(primaryStage);
        }
    }

    private void setupTray(Stage primaryStage) {
        if (!SystemTray.isSupported()) {
            System.out.println("System tray not supported");
            return;
        }

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(java.awt.Color.decode("#e53935"));
        g.fillOval(1, 1, 14, 14);
        g.dispose();

        MenuItem openItem = new MenuItem("Abrir Echo Scrobbler");
        openItem.addActionListener(e -> Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
        }));

        MenuItem exitItem = new MenuItem("Sair");
        exitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            scrobbleTimer.shutdown();
            Platform.exit();
        });

        PopupMenu popup = new PopupMenu();
        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "Echo Scrobbler", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
        }));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.out.println("Tray error: " + e.getMessage());
        }

        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            primaryStage.hide();
        });
    }

    private void showLogin(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
            VBox root = loader.load();

            LoginController controller = loader.getController();
            controller.setAuthService(authService);
            controller.setOnLoginSuccess(() -> showDashboard(stage));

            Scene scene = new Scene(root, 480, 580);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
        }
    }

    private void showDashboard(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dashboard.fxml"));
            VBox root = loader.load();

            Dotenv dotenv = Dotenv.load();
            lastFmClient = new LastFmClient(authService.getSessionKey());
            LastFmService lastFmService = new LastFmService(
                dotenv.get("LASTFM_API_KEY"),
                authService.getSessionKey(),
                authService.getUsername()
            );

            DashboardController controller = loader.getController();
            controller.init(lastFmService, authService);

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateLogic()));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            stage.setTitle("Echo Scrobbler");
            stage.setScene(scene);
            stage.setWidth(480);
            stage.setHeight(680);
            stage.setResizable(false);
            stage.show();
        } catch (Exception e) {
            System.out.println("Dashboard error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateLogic() {
        try {
            ProcessBuilder builder = new ProcessBuilder("playerctl", "metadata", "--format",
                "{{ status }}|||{{ artist }}|||{{ title }}|||{{ album }}|||{{ mpris:length }}");
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String rawLine = reader.readLine();

            if (rawLine != null && !rawLine.trim().isEmpty()) {
                String[] parts = rawLine.split("\\|\\|\\|");
                String status = parts[0].trim();
                String artist = parts[1].trim();
                String title  = parts[2].trim();
                String album  = parts.length > 3 ? parts[3].trim() : "";
                long durationSeconds = 0;
                if (parts.length > 4 && !parts[4].trim().isEmpty()) {
                    long raw = Long.parseLong(parts[4].trim());
                    if (raw > 0 && raw < 3_600_000_000L) {
                        durationSeconds = raw / 1_000_000;
                    }
                }

                if (status.equals("Paused")) {
                    scrobbleTimer.cancel();
                    return;
                }

                String trackId = artist + "|||" + title;
                if (currentTrack == null || !trackId.equals(currentTrack.getArtist() + "|||" + currentTrack.getTitle())) {
                    currentTrack = new Track(artist, title, album, durationSeconds);
                    lastFmClient.updateNowPlaying(artist, title, album);

                    Track trackRef = currentTrack;
                    scrobbleTimer.start(currentTrack, () -> {
                        boolean success = lastFmClient.scrobble(
                            trackRef.getArtist(), trackRef.getTitle(),
                            trackRef.getAlbum(), trackRef.getStartTimestamp()
                        );
                        System.out.println("Scrobble [" + trackRef.getTitle() + "] success: " + success);
                    });
                }

            } else {
                scrobbleTimer.cancel();
                currentTrack = null;
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        scrobbleTimer.shutdown();
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        launch(args);
    }
}
