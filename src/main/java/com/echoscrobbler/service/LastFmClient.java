package com.echoscrobbler.service;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class LastFmClient {
    
    private final String apiKey;
    private final String sharedSecret;
    private final String sessionKey;
    private final HttpClient httpClient;

    public LastFmClient(String sessionKey) {
        Dotenv dotenv = Dotenv.load();
        this.apiKey = dotenv.get("LASTFM_API_KEY");
        this.sharedSecret = dotenv.get("LASTFM_SHARED_SECRET");
        this.sessionKey = sessionKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void updateNowPlaying(String artist, String track, String album) {
        sendRequest("track.updateNowPlaying", artist, track, album, null);
    }

    public boolean scrobble(String artist, String track, String album, long timestamp) {
        return sendRequest("track.scrobble", artist, track, album, String.valueOf(timestamp));
    }

    private boolean sendRequest(String method, String artist, String track, String album, String timestamp) {
        if (this.apiKey == null || this.sessionKey == null) return false;

        try {
            Map<String, String> params = new TreeMap<>();
            params.put("method", method);
            params.put("artist", artist);
            params.put("track", track);
            params.put("api_key", this.apiKey);
            params.put("sk", this.sessionKey);
            
            if (album != null && !album.isEmpty()) params.put("album", album);
            if (timestamp != null) params.put("timestamp", timestamp);

            params.put("api_sig", generateSignature(params));
            params.put("format", "json");

            String formBody = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ws.audioscrobbler.com/2.0/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("API [" + method + "] response: " + response.body());
            return response.body().contains("\"status\":\"ok\"");

        } catch (Exception e) {
            System.out.println("API Error: " + e.getMessage());
            return false;
        }
    }

    private String generateSignature(Map<String, String> params) throws Exception {
        StringBuilder sigBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sigBuilder.append(entry.getKey()).append(entry.getValue());
        }
        sigBuilder.append(this.sharedSecret);
        
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(sigBuilder.toString().getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}