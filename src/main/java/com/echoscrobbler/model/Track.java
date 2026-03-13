package com.echoscrobbler.model;

public class Track {
    private final String artist;
    private final String title;
    private final String album;
    private final long durationSeconds;
    private final long startTimestamp;

    public Track(String artist, String title, String album, long durationSeconds) {
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.durationSeconds = durationSeconds;
        this.startTimestamp = System.currentTimeMillis() / 1000;
    }

    public String getArtist()          { return artist; }
    public String getTitle()           { return title; }
    public String getAlbum()           { return album; }
    public long getDurationSeconds()   { return durationSeconds; }
    public long getStartTimestamp()    { return startTimestamp; }
    
}