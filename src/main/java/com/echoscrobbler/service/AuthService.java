package com.echoscrobbler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AuthService {

    private static final File SESSION_FILE = new File(
        System.getProperty("user.home") + "/.config/echo-scrobbler/session.json"
    );

    private final String apiKey;
    private final String sharedSecret;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String sessionKey;
    private String username;

    public AuthService(String apiKey, String sharedSecret) {
        this.apiKey = apiKey;
        this.sharedSecret = sharedSecret;
        SESSION_FILE.getParentFile().mkdirs();
        loadSession();
    }

    public boolean isAuthenticated() {
        return sessionKey != null && !sessionKey.isEmpty();
    }

    public String getSessionKey() { return sessionKey; }
    public String getUsername()   { return username; }

    public String requestToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://ws.audioscrobbler.com/2.0/?method=auth.gettoken&api_key=" + apiKey + "&format=json"))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map body = mapper.readValue(response.body(), Map.class);
        return (String) body.get("token");
    }

    public String buildAuthUrl(String token) {
        return "http://www.last.fm/api/auth/?api_key=" + apiKey + "&token=" + token;
    }

    public boolean fetchSession(String token) throws Exception {
        String sig = md5("api_key" + apiKey + "methodauth.getsession" + "token" + token + sharedSecret);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://ws.audioscrobbler.com/2.0/?method=auth.getsession&api_key="
                + apiKey + "&token=" + token + "&api_sig=" + sig + "&format=json"))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map body = mapper.readValue(response.body(), Map.class);

        if (!body.containsKey("session")) return false;

        Map session = (Map) body.get("session");
        this.sessionKey = (String) session.get("key");
        this.username   = (String) session.get("name");
        saveSession();
        return true;
    }

    public void clearSession() {
        this.sessionKey = null;
        this.username = null;
        if (SESSION_FILE.exists()) SESSION_FILE.delete();
    }

    private void loadSession() {
        if (!SESSION_FILE.exists()) return;
        try {
            Map data = mapper.readValue(SESSION_FILE, Map.class);
            this.sessionKey = (String) data.get("sessionKey");
            this.username   = (String) data.get("username");
        } catch (Exception e) {
            System.out.println("Session load error: " + e.getMessage());
        }
    }

    private void saveSession() {
        try {
            mapper.writeValue(SESSION_FILE, Map.of("sessionKey", sessionKey, "username", username));
        } catch (Exception e) {
            System.out.println("Session save error: " + e.getMessage());
        }
    }

    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }
}