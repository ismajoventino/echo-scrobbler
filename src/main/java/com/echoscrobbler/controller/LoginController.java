package com.echoscrobbler.controller;

import com.echoscrobbler.service.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoginController {

    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    private AuthService authService;
    private String pendingToken;
    private ScheduledExecutorService poller;
    private Runnable onLoginSuccess;

    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    public void setOnLoginSuccess(Runnable onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }
    
    @FXML
    public void initialize() {
        System.out.println("LoginController initialized");
        System.out.println("loginButton: " + loginButton);
        loginButton.setOnAction(e -> handleLogin());
    }

    @FXML
    private void handleLogin() {
    	System.out.println("handleLogin called");
        loginButton.setDisable(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                pendingToken = authService.requestToken();
                String url = authService.buildAuthUrl(pendingToken);

                Desktop.getDesktop().browse(new URI(url));

                Platform.runLater(() -> statusLabel.setText("Authorize in your browser, then wait..."));

                startPolling();

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    loginButton.setDisable(false);
                });
            }
        });
    }

    private void startPolling() {
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            try {
                boolean success = authService.fetchSession(pendingToken);
                if (success) {
                    poller.shutdown();
                    Platform.runLater(() -> {
                        if (onLoginSuccess != null) onLoginSuccess.run();
                    });
                }
            } catch (Exception ignored) {}
        }, 3, 3, TimeUnit.SECONDS);
    }
}