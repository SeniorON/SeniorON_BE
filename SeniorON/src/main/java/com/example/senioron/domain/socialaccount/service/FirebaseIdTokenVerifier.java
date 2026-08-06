package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.config.FirebaseConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseIdTokenVerifier {

    private final FirebaseConfig firebaseConfig;

    public VerifiedFirebaseUser verify(String firebaseIdToken) {
        if (!firebaseConfig.isInitialized()) {
            log.error("Firebase Admin SDK가 초기화되지 않았습니다.");
            throw new BusinessException(ErrorCode.FIREBASE_AUTH_UNAVAILABLE);
        }

        try {
            FirebaseToken firebaseToken =
                    FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);

            return new VerifiedFirebaseUser(
                    firebaseToken.getUid(),
                    firebaseToken.getEmail(),
                    firebaseToken.getName()
            );
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            log.error("Firebase ID Token 검증 실패", e);
            throw new BusinessException(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
        }
    }
}