package com.example.senioron.domain.socialaccount.service;

public record VerifiedFirebaseUser(
        String uid,
        String email,
        String name,
        boolean emailVerified
) {

    public VerifiedFirebaseUser(String uid, String email, String name) {
        this(uid, email, name, false);
    }
}
