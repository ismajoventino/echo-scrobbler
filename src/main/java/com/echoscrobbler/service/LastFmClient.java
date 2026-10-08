package com.echoscrobbler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class LastFmClient {

    private final String apiKey;
    private final String sharedSecret;
    private final String sessionKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public LastFmClient(String sessionKey) {
        Dotenv dotenv = Dotenv.load();

        this.apiKey = dotenv.get("LASTFM_API_KEY");
        this.sharedSecret = dotenv.get("LASTFM_SHARED_SECRET");
        this.sessionKey = sessionKey;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    }

    public void updateNowPlaying(
        String artist, String track, String album
    ) {
        sendRequest(
            "track.updateNowPlaying", artist, track, album, null
        );
    }

    public boolean scrobble(
        String artist, String track, String album, long timestamp
    ) {
        return sendRequest(
            "track.scrobble",
            artist,
            track,
            album,
            String.valueOf(timestamp)
        );
    }

    private boolean sendRequest(
        String method,
        String artist,
        String track,
        String album,
        String timestamp
    ) {
        if (missing(apiKey) || missing(sharedSecret) || missing(sessionKey)) {
            System.out.println("Last.fm credentials missing");
            return false;
        }

        try {
            Map<String, String> params = new TreeMap<>();

            params.put("method", method);
            params.put("artist", artist);
            params.put("track", track);
            params.put("api_key", apiKey);
            params.put("sk", sessionKey);

            if (!missing(album)) {
                params.put("album", album);
            }

            if (timestamp != null) {
                params.put("timestamp", timestamp);
            }

            String signature = generateSignature(params);
            params.put("api_sig", signature);
            params.put("format", "json");

            String formBody = params.entrySet().stream()
                .map(entry ->
                    entry.getKey() + "=" + URLEncoder.encode(
                        entry.getValue(), StandardCharsets.UTF_8
                    )
                )
                .collect(Collectors.joining("&"));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ws.audioscrobbler.com/2.0/"))
                .timeout(Duration.ofSeconds(15))
                .header(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
            );

            System.out.println(
                "API [" + method + "] response: " + response.body()
            );

            if (response.statusCode() != 200) {
                System.out.println(
                    "Last.fm HTTP error: " + response.statusCode()
                );
                return false;
            }

            JsonNode root = mapper.readTree(response.body());

            if (root == null || root.has("error")) {
                System.out.println("Last.fm returned an error");
                return false;
            }

            if ("track.scrobble".equals(method)) {
                JsonNode counts = root.path("scrobbles").path("@attr");

                return counts.path("accepted").asInt(-1) == 1
                    && counts.path("ignored").asInt(-1) == 0;
            }

            return root.path("nowplaying")
                .path("ignoredMessage")
                .path("code")
                .asInt(-1) == 0;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.out.println(
                "API [" + method + "] not confirmed: " + e.getMessage()
            );
            return false;
        }
    }

    private boolean missing(String value) {
        return value == null || value.isBlank();
    }

    private String generateSignature(
        Map<String, String> params
    ) throws Exception {
        StringBuilder source = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            source.append(entry.getKey()).append(entry.getValue());
        }

        source.append(sharedSecret);

        byte[] hash = MessageDigest.getInstance("MD5").digest(
            source.toString().getBytes(StandardCharsets.UTF_8)
        );

        StringBuilder result = new StringBuilder();

        for (byte value : hash) {
            result.append(String.format("%02x", value & 0xff));
        }

        return result.toString();
    }
}