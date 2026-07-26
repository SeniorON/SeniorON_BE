package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.config.FirebaseConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirebaseIdTokenVerifier {

    private final FirebaseConfig firebaseConfig;

    public VerifiedFirebaseUser verify(String firebaseIdToken) {
        if (!firebaseConfig.isInitialized()) {
            throw new BusinessException(ErrorCode.FIREBASE_AUTH_UNAVAILABLE);
        }

        try {
            FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
            return new VerifiedFirebaseUser(
                    firebaseToken.getUid(),
                    firebaseToken.getEmail(),
                    firebaseToken.getName()
            );
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
        }
    }
}
