package com.echoscrobbler;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.echoscrobbler.controller.DashboardController;
import com.echoscrobbler.controller.LoginController;
import com.echoscrobbler.model.Track;
import com.echoscrobbler.service.AuthService;
import com.echoscrobbler.service.LastFmClient;
import com.echoscrobbler.service.LastFmService;
import com.echoscrobbler.service.ScrobbleTimer;

import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    private final ScrobbleTimer scrobbleTimer = new ScrobbleTimer();

    private final ScheduledExecutorService monitor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "echo-player-monitor");
            thread.setDaemon(true);
            return thread;
        });

    private final ExecutorService apiWorker =
        Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "echo-lastfm-worker");
            thread.setDaemon(true);
            return thread;
        });

    private LastFmClient lastFmClient;
    private AuthService authService;
    private DashboardController dashboardController;
    private Track currentTrack;
    private TrayIcon trayIcon;
    private boolean trackStarted;

    private boolean monitoringStarted;
    private volatile boolean stopping;

    @Override
    public void start(Stage primaryStage) {
        Dotenv dotenv = Dotenv.load();

        authService = new AuthService(
            dotenv.get("LASTFM_API_KEY"),
            dotenv.get("LASTFM_SHARED_SECRET")
        );

        // closing the window exits unless a tray icon is available.
        Platform.setImplicitExit(true);
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

        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(
                16,
                16,
                java.awt.image.BufferedImage.TYPE_INT_ARGB
            );

        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
            java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON
        );
        graphics.setColor(java.awt.Color.decode("#e53935"));
        graphics.fillOval(1, 1, 14, 14);
        graphics.dispose();

        MenuItem openItem = new MenuItem("Abrir Echo Scrobbler");
        openItem.addActionListener(e -> openWindow(primaryStage));

        MenuItem exitItem = new MenuItem("Sair");
        exitItem.addActionListener(e ->
            Platform.runLater(Platform::exit)
        );

        PopupMenu popup = new PopupMenu();
        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "Echo Scrobbler", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> openWindow(primaryStage));

        try {
            SystemTray.getSystemTray().add(trayIcon);

            Platform.setImplicitExit(false);

            primaryStage.setOnCloseRequest(e -> {
                e.consume();
                primaryStage.hide();
            });
        } catch (AWTException | RuntimeException e) {
            trayIcon = null;
            System.out.println("Tray error: " + e.getMessage());
        }
    }

    private void openWindow(Stage stage) {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        });
    }

    private void showLogin(Stage stage) {
        try {
            FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/login.fxml"));
            VBox root = loader.load();

            LoginController controller = loader.getController();
            controller.setAuthService(authService);
            controller.setOnLoginSuccess(() ->
                Platform.runLater(() -> showDashboard(stage))
            );

            Scene scene = new Scene(root, 480, 580);
            scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDashboard(Stage stage) {
        try {
            FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/dashboard.fxml"));
            VBox root = loader.load();

            Dotenv dotenv = Dotenv.load();
            lastFmClient = new LastFmClient(authService.getSessionKey());

            LastFmService lastFmService = new LastFmService(
                dotenv.get("LASTFM_API_KEY"),
                authService.getSessionKey(),
                authService.getUsername()
            );

            if (dashboardController != null) {
                dashboardController.shutdown();
            }

            dashboardController = loader.getController();
            dashboardController.init(lastFmService, authService);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
            );

            stage.setTitle("Echo Scrobbler");
            stage.setScene(scene);
            stage.setWidth(480);
            stage.setHeight(680);
            stage.setResizable(false);
            stage.show();

            if (!monitoringStarted) {
                monitoringStarted = true;

                monitor.scheduleWithFixedDelay(
                    this::updateLogic,
                    0,
                    1,
                    TimeUnit.SECONDS
                );
            }
        } catch (Exception e) {
            System.out.println("Dashboard error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateLogic() {
        if (stopping) {
            return;
        }

        Process process = null;

        try {
            process = new ProcessBuilder(
                "playerctl",
                "metadata",
                "--format",
                "{{ status }}|||{{ artist }}|||{{ title }}|||"
                    + "{{ album }}|||{{ mpris:length }}|||{{ mpris:artUrl }}"
            )
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                scrobbleTimer.pause();
                dashboardController.showUnavailable("Player not responding");
                return;
            }

            if (process.exitValue() != 0) {
                scrobbleTimer.pause();
                dashboardController.showUnavailable("Player unavailable");
                return;
            }

            String rawLine;

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    process.getInputStream(),
                    StandardCharsets.UTF_8
                )
            )) {
                rawLine = reader.readLine();
            }

            if (rawLine == null || rawLine.isBlank()) {
                scrobbleTimer.pause();
                dashboardController.showUnavailable("Waiting for player");
                return;
            }

            String[] parts = rawLine.split("\\|\\|\\|", -1);

            if (parts.length < 3) {
                scrobbleTimer.pause();
                dashboardController.showUnavailable("Track data unavailable");
                return;
            }

            String status = parts[0].trim();
            String artist = parts[1].trim();
            String title = parts[2].trim();
            String album = parts.length > 3 ? parts[3].trim() : "";
            String artUrl = parts.length > 5 ? parts[5].trim() : "";

            if ("Stopped".equals(status)) {
                clearTrack();
                trackStarted = false;
                dashboardController.showStopped();
                return;
            }

            boolean playing = "Playing".equals(status);
            boolean paused = "Paused".equals(status);

            if (!playing && !paused) {
                scrobbleTimer.pause();
                dashboardController.showUnavailable("Waiting for player");
                return;
            }

            if (artist.isBlank() || title.isBlank()) {
                scrobbleTimer.pause();
                dashboardController.showUnavailable("Track data unavailable");
                return;
            }

            long duration = parts.length > 4
                ? parseDuration(parts[4])
                : 0;

            boolean sameTrack = currentTrack != null
                && artist.equals(currentTrack.getArtist())
                && title.equals(currentTrack.getTitle())
                && album.equals(currentTrack.getAlbum());

            if (!sameTrack || (playing && !trackStarted)) {
                scrobbleTimer.cancel();
                currentTrack = new Track(artist, title, album, duration);
                trackStarted = false;
            }

            Track detectedTrack = currentTrack;
            DashboardController view = dashboardController;

            view.showPlayback(detectedTrack, artUrl, playing);

            if (paused) {
                scrobbleTimer.pause();
                return;
            }

            if (trackStarted) {
                scrobbleTimer.resume();
                return;
            }

            trackStarted = true;
            LastFmClient client = lastFmClient;

            submitApi(() -> client.updateNowPlaying(
                detectedTrack.getArtist(),
                detectedTrack.getTitle(),
                detectedTrack.getAlbum()
            ));

            scrobbleTimer.start(detectedTrack, () -> {
                view.markSending(detectedTrack);

                submitApi(() -> {
                    boolean accepted = client.scrobble(
                        detectedTrack.getArtist(),
                        detectedTrack.getTitle(),
                        detectedTrack.getAlbum(),
                        detectedTrack.getStartTimestamp()
                    );

                    System.out.println(
                        "Scrobble [" + detectedTrack.getTitle()
                            + "] accepted: " + accepted
                    );

                    view.markResult(detectedTrack, accepted);
                });
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scrobbleTimer.pause();
        } catch (Exception e) {
            scrobbleTimer.pause();
            dashboardController.showUnavailable("Player read error");
            System.out.println("Monitor error: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private long parseDuration(String value) {
        try {
            long microseconds = Long.parseLong(value.trim());

            if (microseconds <= 0 || microseconds == Long.MAX_VALUE) {
                return 0;
            }

            return Math.max(1, microseconds / 1_000_000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void clearTrack() {
        scrobbleTimer.cancel();
        currentTrack = null;
    }

    private void submitApi(Runnable action) {
        if (stopping) {
            return;
        }

        try {
            apiWorker.execute(action);
        } catch (RejectedExecutionException e) {
            if (!stopping) {
                System.out.println("API worker unavailable");
            }
        }
    }

    @Override
    public void stop() {
        stopping = true;

        monitor.shutdownNow();
        scrobbleTimer.shutdown();
        apiWorker.shutdownNow();

        if (dashboardController != null) {
            dashboardController.shutdown();
        }

        TrayIcon icon = trayIcon;

        if (icon != null) {
            EventQueue.invokeLater(() ->
                SystemTray.getSystemTray().remove(icon)
            );
        }
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        launch(args);
    }
}