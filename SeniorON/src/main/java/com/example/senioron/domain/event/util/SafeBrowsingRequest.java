package com.example.senioron.domain.event.util;

import java.util.List;

public record SafeBrowsingRequest(Client client, ThreatInfo threatInfo) {
    public record Client(String clientId, String clientVersion) {}

    public record ThreatInfo(
            List<String> threatTypes,
            List<String> platformTypes,
            List<String> threatEntryTypes,
            List<ThreatEntry> threatEntries
    ) {}

    public record ThreatEntry(String url) {}
}
