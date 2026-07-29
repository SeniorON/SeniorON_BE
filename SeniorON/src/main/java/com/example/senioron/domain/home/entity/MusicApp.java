package com.example.senioron.domain.home.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MusicApp {

    MELON(
            "멜론",
            "melon",
            "com.iloen.melon"
    ),

    GENIE(
            "지니뮤직",
            "genie",
            "com.ktmusic.geniemusic"
    ),

    YOUTUBE_MUSIC(
            "유튜브 뮤직",
            "youtube_music",
            "com.google.android.apps.youtube.music"
    ),

    SPOTIFY(
            "스포티파이",
            "spotify",
            "com.spotify.music"
    ),

    FLO(
            "플로",
            "flo",
            "skplanet.musicmate"
    ),

    VIBE(
            "바이브",
            "vibe",
            "com.naver.vibe"
    ),

    BUGS(
            "벅스",
            "bugs",
            "com.neowiz.android.bugs"
    ),

    SAMSUNG_MUSIC(
            "삼성 뮤직",
            "samsung_music",
            "com.sec.android.app.music"
    ),

    KAKAO_MUSIC(
            "카카오뮤직",
            "kakao_music",
            "com.kakao.music"
    );

    private final String displayName;
    private final String icon;
    private final String packageName;
}