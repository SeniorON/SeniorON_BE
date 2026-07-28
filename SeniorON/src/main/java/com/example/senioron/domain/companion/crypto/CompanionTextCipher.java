package com.example.senioron.domain.companion.crypto;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class CompanionTextCipher {

    private static final String VERSION = "v1";
    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final CompanionEncryptionProperties properties;

    private final SecureRandom secureRandom =
            new SecureRandom();

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw encryptionException();
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher =
                    Cipher.getInstance(TRANSFORMATION);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    loadSecretKey(),
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            byte[] encrypted =
                    cipher.doFinal(
                            plaintext.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            Base64.Encoder encoder =
                    Base64.getUrlEncoder()
                            .withoutPadding();

            return VERSION
                    + ":"
                    + encoder.encodeToString(iv)
                    + ":"
                    + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw encryptionException();
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null) {
            throw encryptionException();
        }

        String[] parts =
                storedValue.split(":", -1);

        if (parts.length != 3) {
            throw encryptionException();
        }

        if (!VERSION.equals(parts[0])) {
            throw encryptionException();
        }

        try {
            Base64.Decoder decoder =
                    Base64.getUrlDecoder();

            byte[] iv = decoder.decode(parts[1]);
            byte[] encrypted = decoder.decode(parts[2]);

            if (iv.length != IV_LENGTH_BYTES) {
                throw encryptionException();
            }

            Cipher cipher =
                    Cipher.getInstance(TRANSFORMATION);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    loadSecretKey(),
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            byte[] decrypted =
                    cipher.doFinal(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );
        } catch (
                GeneralSecurityException
                | IllegalArgumentException exception
        ) {
            throw encryptionException();
        }
    }

    private SecretKey loadSecretKey() {
        String configuredKey =
                properties.getKey();

        if (configuredKey == null
                || configuredKey.isBlank()) {
            throw encryptionException();
        }

        try {
            byte[] decoded =
                    Base64.getDecoder()
                            .decode(configuredKey);

            if (decoded.length != KEY_LENGTH_BYTES) {
                throw encryptionException();
            }

            return new SecretKeySpec(
                    decoded,
                    "AES"
            );
        } catch (IllegalArgumentException exception) {
            throw encryptionException();
        }
    }

    private BusinessException encryptionException() {
        return new BusinessException(
                ErrorCode.COMPANION_ENCRYPTION_FAILED
        );
    }
}
