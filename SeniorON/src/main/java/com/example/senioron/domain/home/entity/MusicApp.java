package com.example.senioron.domain.home.entity;

public enum MusicApp {

    MELON("com.iloen.melon"),
    SPOTIFY("com.spotify.music");

    private final String packageName;

    MusicApp(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }
}