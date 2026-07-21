package com.example.senioron.domain.event.util;

import java.util.List;

public record SafeBrowsingResponse(List<Match> matches) {
    public record Match(String threatType) {}
}
