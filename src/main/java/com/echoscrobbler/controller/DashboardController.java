package com.echoscrobbler.controller;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.echoscrobbler.model.ScrobbleRecord;
import com.echoscrobbler.model.Track;
import com.echoscrobbler.service.AuthService;
import com.echoscrobbler.service.LastFmService;

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

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "echo-history");
            thread.setDaemon(true);
            return thread;
        });

    private enum Submission {
        WAITING, SENDING, ACCEPTED, UNCONFIRMED
    }

    // These fields are accessed only on the JavaFX thread.
    private Track displayedTrack;
    private Submission submission = Submission.WAITING;
    private String playback = "Waiting for player";
    private String artworkUrl = "";

    private List<ScrobbleRecord> displayedHistory = List.of();
    private final List<Label> timeLabels = new ArrayList<>();

    private final Map<String, Image> images =
        new LinkedHashMap<>(32, 0.75f, true);

    private volatile boolean closed;

    @FXML
    public void initialize() {
        Rectangle clip = new Rectangle(120, 120);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        albumArtImage.setClip(clip);

        albumArtImage.setVisible(false);
        artworkPlaceholder.setVisible(true);
        statusText.setText("Waiting for player");
    }

    public void init(
        LastFmService lastFmService, AuthService authService
    ) {
        this.lastFmService = lastFmService;
        this.authService = authService;

        String username = authService.getUsername();
        usernameLabel.setText(username != null ? username : "—");

        scheduler.scheduleWithFixedDelay(
            this::loadRecentScrobbles,
            0,
            15,
            TimeUnit.SECONDS
        );
    }

    public void showPlayback(
        Track track, String artUrl, boolean playing
    ) {
        onUi(() -> {
            if (displayedTrack != track) {
                displayedTrack = track;
                submission = Submission.WAITING;

                npTitle.setText(track.getTitle());
                npArtist.setText(track.getArtist());
            }

            playback = playing ? "Playing" : "Paused";
            loadArtwork(artUrl);
            renderStatus();
        });
    }

    public void showUnavailable(String message) {
        onUi(() -> {
            playback = message;
            renderStatus();
        });
    }

    public void showStopped() {
        onUi(() -> {
            displayedTrack = null;
            submission = Submission.WAITING;
            playback = "Nothing playing";

            npTitle.setText("Nothing playing");
            npArtist.setText("—");
            loadArtwork("");
            renderStatus();
        });
    }

    public void markSending(Track track) {
        onUi(() -> {
            if (displayedTrack != track) {
                return;
            }

            submission = Submission.SENDING;
            renderStatus();
        });
    }

    public void markResult(Track track, boolean accepted) {
        onUi(() -> {
            // An old request must not change the current track's status.
            if (displayedTrack == track) {
                submission = accepted
                    ? Submission.ACCEPTED
                    : Submission.UNCONFIRMED;

                renderStatus();
            }
        });

        if (accepted) {
            refreshHistory();
        }
    }

    private void renderStatus() {
        boolean playing = "Playing".equals(playback);
        boolean paused = "Paused".equals(playback);

        String message;

        if (!playing && !paused) {
            message = playback;
        } else {
            String submissionText = switch (submission) {
                case SENDING -> "Sending…";
                case ACCEPTED -> "Scrobbled ✓";
                case UNCONFIRMED -> "Send not confirmed";
                case WAITING -> "";
            };

            if (paused) {
                message = submissionText.isEmpty()
                    ? "Paused"
                    : "Paused · " + submissionText;
            } else if (!submissionText.isEmpty()) {
                message = submissionText;
            } else if (displayedTrack != null
                && displayedTrack.getDurationSeconds() > 0
                && displayedTrack.getDurationSeconds() <= 30) {
                message = "Track too short";
            } else {
                message = "Listening";
            }
        }

        statusText.setText(message);

        boolean active = playing
            && submission != Submission.UNCONFIRMED;

        String css = active ? "status-scrobbling" : "status-idle";
        String other = active ? "status-idle" : "status-scrobbling";

        statusBadge.getStyleClass().removeAll(other);

        if (!statusBadge.getStyleClass().contains(css)) {
            statusBadge.getStyleClass().add(css);
        }
    }

    private void loadArtwork(String url) {
        String nextUrl = url == null ? "" : url.trim();

        if (Objects.equals(artworkUrl, nextUrl)) {
            return;
        }

        artworkUrl = nextUrl;
        albumArtImage.setImage(null);
        albumArtImage.setVisible(false);
        artworkPlaceholder.setVisible(true);

        if (nextUrl.isEmpty()) {
            return;
        }

        try {
            Image image = cachedImage(nextUrl, 120);
            albumArtImage.setImage(image);

            Runnable updateVisibility = () -> {
                if (albumArtImage.getImage() != image) {
                    return;
                }

                boolean ready = !image.isError()
                    && image.getProgress() >= 1;

                albumArtImage.setVisible(ready);
                artworkPlaceholder.setVisible(!ready);
            };

            image.progressProperty().addListener(
                (observable, oldValue, newValue) -> updateVisibility.run()
            );
            image.errorProperty().addListener(
                (observable, oldValue, newValue) -> updateVisibility.run()
            );

            updateVisibility.run();
        } catch (IllegalArgumentException e) {
            System.out.println("Artwork unavailable: " + e.getMessage());
        }
    }

    private Image cachedImage(String url, int size) {
        String key = size + "|" + url;
        Image image = images.get(key);

        if (image == null || image.isError()) {
            image = new Image(url, size, size, false, true, true);
            images.put(key, image);

            if (images.size() > 32) {
                images.remove(images.keySet().iterator().next());
            }
        }

        return image;
    }

    private void refreshHistory() {
        if (closed) {
            return;
        }

        try {
            scheduler.execute(this::loadRecentScrobbles);
        } catch (RejectedExecutionException e) {
            // The window/application is shutting down.
        }
    }

    private void loadRecentScrobbles() {
        if (closed) {
            return;
        }

        try {
            List<ScrobbleRecord> tracks =
                lastFmService.getRecentTracks(5);

            onUi(() -> {
                if (!sameHistory(tracks)) {
                    scrobbleList.getChildren().clear();
                    timeLabels.clear();

                    for (ScrobbleRecord track : tracks) {
                        scrobbleList.getChildren().add(
                            buildScrobbleRow(track)
                        );
                    }

                    displayedHistory = new ArrayList<>(tracks);
                }

                for (int i = 0; i < displayedHistory.size(); i++) {
                    timeLabels.get(i).setText(
                        formatTimestamp(displayedHistory.get(i).timestamp)
                    );
                }
            });
        } catch (Exception e) {
            System.out.println("History error: " + e.getMessage());
        }
    }

    private boolean sameHistory(List<ScrobbleRecord> tracks) {
        if (tracks.size() != displayedHistory.size()) {
            return false;
        }

        for (int i = 0; i < tracks.size(); i++) {
            ScrobbleRecord a = tracks.get(i);
            ScrobbleRecord b = displayedHistory.get(i);

            if (a.timestamp != b.timestamp
                || !Objects.equals(a.title, b.title)
                || !Objects.equals(a.artist, b.artist)
                || !Objects.equals(a.imageUrl, b.imageUrl)) {
                return false;
            }
        }

        return true;
    }

    private HBox buildScrobbleRow(ScrobbleRecord track) {
        HBox row = new HBox(14);
        row.getStyleClass().add("scrobble-row");
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("scrobble-thumb");
        thumb.setMinSize(34, 34);
        thumb.setMaxSize(34, 34);

        if (track.imageUrl != null && !track.imageUrl.isBlank()) {
            try {
                ImageView image = new ImageView(
                    cachedImage(track.imageUrl, 34)
                );

                image.setFitWidth(34);
                image.setFitHeight(34);

                Rectangle clip = new Rectangle(34, 34);
                clip.setArcWidth(6);
                clip.setArcHeight(6);
                image.setClip(clip);

                thumb.getChildren().add(image);
            } catch (IllegalArgumentException e) {
                System.out.println("History artwork unavailable");
            }
        }

        VBox info = new VBox(2);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label title = new Label(track.title);
        title.getStyleClass().add("scrobble-row-title");

        Label artist = new Label(track.artist);
        artist.getStyleClass().add("scrobble-row-artist");

        info.getChildren().addAll(title, artist);

        Label time = new Label(formatTimestamp(track.timestamp));
        time.getStyleClass().add("scrobble-row-time");
        time.setMinWidth(Label.USE_PREF_SIZE);
        timeLabels.add(time);

        Label check = new Label("✓");
        check.getStyleClass().add("scrobble-check");

        row.getChildren().addAll(thumb, info, time, check);
        return row;
    }

    @FXML
    private void openProfile() {
        String username = authService.getUsername();

        if (username == null || username.isBlank()) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                Desktop.getDesktop().browse(
                    new URI("https", "www.last.fm", "/user/" + username, null)
                );
            } catch (Exception e) {
                System.out.println(
                    "Could not open browser: " + e.getMessage()
                );
            }
        }, "echo-open-profile");

        thread.setDaemon(true);
        thread.start();
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }

        long minutes = Math.max(
            0, (System.currentTimeMillis() / 1000 - timestamp) / 60
        );

        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 1440) return (minutes / 60) + "h ago";

        return LocalDate.ofInstant(
            Instant.ofEpochSecond(timestamp),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("dd MMM"));
    }

    private void onUi(Runnable action) {
        if (closed) {
            return;
        }

        Platform.runLater(() -> {
            if (!closed) {
                action.run();
            }
        });
    }

    public void shutdown() {
        closed = true;
        scheduler.shutdownNow();
    }
}