package com.echoscrobbler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.echoscrobbler.model.ScrobbleRecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class LastFmService {

    private static final String BASE = "http://ws.audioscrobbler.com/2.0/";

    private final String apiKey;
    private final String sessionKey;
    private final String username;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public LastFmService(String apiKey, String sessionKey, String username) {
        this.apiKey = apiKey;
        this.sessionKey = sessionKey;
        this.username = username;
    }

    public List<ScrobbleRecord> getRecentTracks(int limit) throws Exception {
        String url = BASE + "?method=user.getrecenttracks&user=" + username
            + "&api_key=" + apiKey + "&format=json&limit=" + limit;
        JsonNode root = get(url).path("recenttracks").path("track");

        List<ScrobbleRecord> list = new ArrayList<>();
        for (JsonNode t : root) {
            boolean nowPlaying = t.path("@attr").path("nowplaying").asText().equals("true");
            if (nowPlaying) continue;
            ScrobbleRecord r = new ScrobbleRecord(
                t.path("artist").path("#text").asText(),
                t.path("name").asText(),
                t.path("album").path("#text").asText(),
                t.path("date").path("uts").asLong()
            );
            r.imageUrl = largestImage(t);
            list.add(r);
        }
        return list;
    }

    public ScrobbleRecord getNowPlaying() throws Exception {
        String url = BASE + "?method=user.getrecenttracks&user=" + username
            + "&api_key=" + apiKey + "&format=json&limit=1";
        JsonNode tracks = get(url).path("recenttracks").path("track");

        for (JsonNode t : tracks) {
            if (t.path("@attr").path("nowplaying").asText().equals("true")) {
                ScrobbleRecord r = new ScrobbleRecord(
                    t.path("artist").path("#text").asText(),
                    t.path("name").asText(),
                    t.path("album").path("#text").asText(),
                    System.currentTimeMillis() / 1000
                );
                r.imageUrl = largestImage(t);
                return r;
            }
        }
        return null;
    }

    public JsonNode getUserInfo() throws Exception {
        String url = BASE + "?method=user.getinfo&user=" + username
            + "&api_key=" + apiKey + "&format=json";
        return get(url).path("user");
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(res.body());
    }

    private String largestImage(JsonNode track) {
        JsonNode images = track.path("image");
        String url = "";
        for (JsonNode img : images) {
            String size = img.path("size").asText();
            if (size.equals("extralarge") || size.equals("large")) {
                url = img.path("#text").asText();
            }
        }
        return url;
    }
}