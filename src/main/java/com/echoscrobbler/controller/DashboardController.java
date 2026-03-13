package com.echoscrobbler.controller;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.echoscrobbler.model.ScrobbleRecord;
import com.echoscrobbler.service.AuthService;
import com.echoscrobbler.service.LastFmService;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class DashboardController {

    @FXML private Label usernameLabel;
    @FXML private HBox profileLink;
    @FXML private ImageView albumArtImage;
    @FXML private VBox artworkPlaceholder;
    @FXML private Label npTitle;
    @FXML private Label npArtist;
    @FXML private HBox statusBadge;
    @FXML private Label statusText;
    @FXML private VBox scrobbleList;

    private LastFmService lastFmService;
    private AuthService authService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void init(LastFmService lastFmService, AuthService authService) {
        this.lastFmService = lastFmService;
        this.authService = authService;
        loadAll();
        startPolling();
    }

    @FXML
    public void initialize() {
     
    }

    // ─── Load ─────────────────────────────────────────────────────────

    private void loadAll() {
        scheduler.execute(() -> {
            loadUsername();
            loadNowPlaying();
            loadRecentScrobbles();
        });
    }

    private void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            loadNowPlaying();
            loadRecentScrobbles();
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void loadUsername() {
        try {
            String name = authService.getUsername();
            Platform.runLater(() -> usernameLabel.setText(name != null ? name : "—"));
        } catch (Exception e) {
            System.out.println("Username error: " + e.getMessage());
        }
    }

    private void loadNowPlaying() {
        try {
            ScrobbleRecord np = lastFmService.getNowPlaying();
            // Always get last track for the artwork
            List<ScrobbleRecord> recent = lastFmService.getRecentTracks(1);
            ScrobbleRecord display = np != null ? np : (recent.isEmpty() ? null : recent.get(0));

            Platform.runLater(() -> {
                if (display != null) {
                    npTitle.setText(display.title);
                    npArtist.setText(display.artist);
                    loadArtwork(display.imageUrl);
                } else {
                    npTitle.setText("Nothing playing");
                    npArtist.setText("—");
                    albumArtImage.setVisible(false);
                    artworkPlaceholder.setVisible(true);
                }
                setScrobblingState(np != null);
            });
        } catch (Exception e) {
            System.out.println("NowPlaying error: " + e.getMessage());
        }
    }

    private void loadArtwork(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Image img = new Image(imageUrl, 120, 120, false, true, true);
            albumArtImage.setImage(img);

            Rectangle clip = new Rectangle(120, 120);
            clip.setArcWidth(14);
            clip.setArcHeight(14);
            albumArtImage.setClip(clip);

            albumArtImage.setVisible(true);
            artworkPlaceholder.setVisible(false);
        } else {
            albumArtImage.setVisible(false);
            artworkPlaceholder.setVisible(true);
        }
    }

    private void loadRecentScrobbles() {
        try {
            List<ScrobbleRecord> tracks = lastFmService.getRecentTracks(5);
            Platform.runLater(() -> {
                scrobbleList.getChildren().clear();
                for (ScrobbleRecord t : tracks) {
                    scrobbleList.getChildren().add(buildScrobbleRow(t));
                }
            });
        } catch (Exception e) {
            System.out.println("RecentTracks error: " + e.getMessage());
        }
    }

    // ─── State ────────────────────────────────────────────────────────

    private void setScrobblingState(boolean scrobbling) {
        if (scrobbling) {
            statusBadge.getStyleClass().removeAll("status-idle");
            if (!statusBadge.getStyleClass().contains("status-scrobbling"))
                statusBadge.getStyleClass().add("status-scrobbling");
            statusText.setText("Scrobbling now");
         
        } else {
            statusBadge.getStyleClass().removeAll("status-scrobbling");
            if (!statusBadge.getStyleClass().contains("status-idle"))
                statusBadge.getStyleClass().add("status-idle");
            statusText.setText("Last played");
            
        }
    }

    // ─── UI Builder ───────────────────────────────────────────────────

    private HBox buildScrobbleRow(ScrobbleRecord t) {
        HBox row = new HBox(14);
        row.getStyleClass().add("scrobble-row");
        row.setAlignment(Pos.CENTER_LEFT);

        // Thumb
        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("scrobble-thumb");
        thumb.setMinSize(34, 34);
        thumb.setMaxSize(34, 34);

        if (t.imageUrl != null && !t.imageUrl.isEmpty()) {
            ImageView iv = new ImageView(new Image(t.imageUrl, 34, 34, false, true, true));
            iv.setFitWidth(34);
            iv.setFitHeight(34);
            Rectangle clip = new Rectangle(34, 34);
            clip.setArcWidth(6);
            clip.setArcHeight(6);
            iv.setClip(clip);
            thumb.getChildren().add(iv);
        }

        // Info
        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(t.title);
        title.getStyleClass().add("scrobble-row-title");
        Label artist = new Label(t.artist);
        artist.getStyleClass().add("scrobble-row-artist");
        info.getChildren().addAll(title, artist);

        // Time
        Label time = new Label(formatTimestamp(t.timestamp));
        time.getStyleClass().add("scrobble-row-time");

        // Check
        Label check = new Label("✓");
        check.getStyleClass().add("scrobble-check");

        row.getChildren().addAll(thumb, info, time, check);
        return row;
    }

    // ─── Handlers ─────────────────────────────────────────────────────

    @FXML
    private void openProfile() {
        String username = authService.getUsername();
        if (username == null || username.isEmpty()) return;
        try {
            Desktop.getDesktop().browse(new URI("https://www.last.fm/user/" + username));
        } catch (Exception e) {
            System.out.println("Could not open browser: " + e.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private String formatTimestamp(long uts) {
        if (uts <= 0) return "";
        long diffMin = (System.currentTimeMillis() / 1000 - uts) / 60;
        if (diffMin < 1) return "now";
        if (diffMin < 60) return diffMin + "m ago";
        if (diffMin < 1440) return (diffMin / 60) + "h ago";
        return LocalDate.ofInstant(Instant.ofEpochSecond(uts), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMM"));
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}