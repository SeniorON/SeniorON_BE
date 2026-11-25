package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.dto.google.request.GoogleLoginRequest;
import com.example.senioron.domain.socialaccount.dto.google.response.GoogleLoginResponse;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private final FirebaseIdTokenVerifier firebaseIdTokenVerifier;
    private final GoogleLoginTransactionService googleLoginTransactionService;

    public GoogleLoginResponse googleLogin(GoogleLoginRequest request) {
        VerifiedFirebaseUser firebaseUser =
                firebaseIdTokenVerifier.verify(request.getFirebaseIdToken());

        validateRequiredInfo(firebaseUser);

        return googleLoginTransactionService.login(request, firebaseUser);
    }

    private void validateRequiredInfo(VerifiedFirebaseUser firebaseUser) {
        if (firebaseUser == null
                || isBlank(firebaseUser.uid())
                || isBlank(firebaseUser.email())
                || isBlank(firebaseUser.name())) {
            throw new BusinessException(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
