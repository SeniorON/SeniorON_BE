package com.example.senioron.global.storage;

public record StoredObjectInfo(
        long contentLength,
        String contentType
) {
}