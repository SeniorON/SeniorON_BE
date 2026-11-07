package com.example.senioron.domain.socialaccount.service;

public record VerifiedFirebaseUser(
        String uid,
        String email,
        String name
) {
}
