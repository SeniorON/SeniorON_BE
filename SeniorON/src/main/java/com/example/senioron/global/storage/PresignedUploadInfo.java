package com.example.senioron.global.storage;

public record PresignedUploadInfo(
        String imageKey,
        String uploadUrl,
        long expiresInSeconds
) {
}