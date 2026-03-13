package com.echoscrobbler.model;

public class ScrobbleRecord {
    public String artist;
    public String title;
    public String album;
    public long timestamp;
    public int attempts;
    public String imageUrl;
    public boolean nowPlaying;

    public ScrobbleRecord() {}

    public ScrobbleRecord(String artist, String title, String album, long timestamp) {
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.timestamp = timestamp;
        this.attempts = 0;
    }
}