package com.example.senioron.domain.companion.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CompanionTextCipherTest {

    private static final String KOREAN_PLAINTEXT =
            "안녕하세요. 오늘 기분은 어떠세요?";

    @Test
    void encryptingSamePlaintextTwiceProducesDifferentCiphertexts() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String first =
                cipher.encrypt(KOREAN_PLAINTEXT);

        String second =
                cipher.encrypt(KOREAN_PLAINTEXT);

        assertThat(first)
                .isNotEqualTo(second);
    }

    @Test
    void decryptsBothCiphertextsToOriginalPlaintext() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String first =
                cipher.encrypt(KOREAN_PLAINTEXT);

        String second =
                cipher.encrypt(KOREAN_PLAINTEXT);

        assertThat(cipher.decrypt(first))
                .isEqualTo(KOREAN_PLAINTEXT);

        assertThat(cipher.decrypt(second))
                .isEqualTo(KOREAN_PLAINTEXT);
    }

    @Test
    void encryptedValueUsesVersionedStorageFormat() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String encrypted =
                cipher.encrypt(KOREAN_PLAINTEXT);

        String[] parts =
                encrypted.split(":", -1);

        assertThat(parts)
                .hasSize(3);

        assertThat(parts[0])
                .isEqualTo("v1");

        assertThat(parts[1])
                .isNotBlank();

        assertThat(parts[2])
                .isNotBlank();
    }

    @Test
    void encryptedValueDoesNotContainPlaintext() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String encrypted =
                cipher.encrypt(KOREAN_PLAINTEXT);

        assertThat(encrypted)
                .doesNotContain(KOREAN_PLAINTEXT);
    }

    @Test
    void encryptsAndDecryptsKoreanText() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String encrypted =
                cipher.encrypt(KOREAN_PLAINTEXT);

        String decrypted =
                cipher.decrypt(encrypted);

        assertThat(decrypted)
                .isEqualTo(KOREAN_PLAINTEXT);
    }

    @Test
    void encryptsAndDecryptsEmptyString() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String encrypted =
                cipher.encrypt("");

        String decrypted =
                cipher.decrypt(encrypted);

        assertThat(decrypted)
                .isEmpty();
    }

    @Test
    void rejectsUnsupportedCiphertextVersion() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String encrypted =
                cipher.encrypt(KOREAN_PLAINTEXT);

        String unsupportedVersion =
                encrypted.replaceFirst(
                        "^v1:",
                        "v2:"
                );

        assertEncryptionFailed(() ->
                cipher.decrypt(unsupportedVersion)
        );
    }

    @Test
    void rejectsMalformedCiphertextFormat() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        assertEncryptionFailed(() ->
                cipher.decrypt(
                        "v1:invalid-format"
                )
        );
    }

    @Test
    void rejectsCiphertextWithInvalidIvLength() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String invalidIv =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                new byte[8]
                        );

        String invalidValue =
                "v1:"
                        + invalidIv
                        + ":"
                        + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                new byte[16]
                        );

        assertEncryptionFailed(() ->
                cipher.decrypt(invalidValue)
        );
    }

    @Test
    void rejectsTamperedCiphertext() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        String encrypted =
                cipher.encrypt(KOREAN_PLAINTEXT);

        String tampered =
                tamperCiphertext(encrypted);

        assertEncryptionFailed(() ->
                cipher.decrypt(tampered)
        );
    }

    @Test
    void rejectsCiphertextEncryptedWithDifferentKey() {
        CompanionTextCipher encryptingCipher =
                createCipher((byte) 1);

        CompanionTextCipher decryptingCipher =
                createCipher((byte) 2);

        String encrypted =
                encryptingCipher.encrypt(
                        KOREAN_PLAINTEXT
                );

        assertEncryptionFailed(() ->
                decryptingCipher.decrypt(encrypted)
        );
    }

    @Test
    void rejectsEncryptionWhenKeyIsMissing() {
        CompanionEncryptionProperties properties =
                new CompanionEncryptionProperties();

        properties.setKey("");

        CompanionTextCipher cipher =
                new CompanionTextCipher(properties);

        assertEncryptionFailed(() ->
                cipher.encrypt(KOREAN_PLAINTEXT)
        );
    }

    @Test
    void rejectsDecryptionWhenKeyIsMissing() {
        CompanionTextCipher validCipher =
                createCipher((byte) 1);

        String encrypted =
                validCipher.encrypt(
                        KOREAN_PLAINTEXT
                );

        CompanionEncryptionProperties properties =
                new CompanionEncryptionProperties();

        properties.setKey("");

        CompanionTextCipher cipherWithoutKey =
                new CompanionTextCipher(properties);

        assertEncryptionFailed(() ->
                cipherWithoutKey.decrypt(encrypted)
        );
    }

    @Test
    void rejectsKeyThatIsNotValidBase64() {
        CompanionEncryptionProperties properties =
                new CompanionEncryptionProperties();

        properties.setKey(
                "this-is-not-valid-base64@@@"
        );

        CompanionTextCipher cipher =
                new CompanionTextCipher(properties);

        assertEncryptionFailed(() ->
                cipher.encrypt(KOREAN_PLAINTEXT)
        );
    }

    @Test
    void rejectsKeyShorterThanThirtyTwoBytes() {
        CompanionTextCipher cipher =
                createCipherWithLength(
                        16,
                        (byte) 1
                );

        assertEncryptionFailed(() ->
                cipher.encrypt(KOREAN_PLAINTEXT)
        );
    }

    @Test
    void rejectsKeyLongerThanThirtyTwoBytes() {
        CompanionTextCipher cipher =
                createCipherWithLength(
                        33,
                        (byte) 1
                );

        assertEncryptionFailed(() ->
                cipher.encrypt(KOREAN_PLAINTEXT)
        );
    }

    @Test
    void rejectsNullPlaintext() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        assertEncryptionFailed(() ->
                cipher.encrypt(null)
        );
    }

    @Test
    void rejectsNullStoredValue() {
        CompanionTextCipher cipher =
                createCipher((byte) 1);

        assertEncryptionFailed(() ->
                cipher.decrypt(null)
        );
    }

    private CompanionTextCipher createCipher(
            byte fillValue
    ) {
        return createCipherWithLength(
                32,
                fillValue
        );
    }

    private CompanionTextCipher createCipherWithLength(
            int keyLength,
            byte fillValue
    ) {
        byte[] key =
                new byte[keyLength];

        Arrays.fill(
                key,
                fillValue
        );

        CompanionEncryptionProperties properties =
                new CompanionEncryptionProperties();

        properties.setKey(
                Base64.getEncoder()
                        .encodeToString(key)
        );

        return new CompanionTextCipher(properties);
    }

    private String tamperCiphertext(
            String encrypted
    ) {
        String[] parts =
                encrypted.split(":", -1);

        byte[] ciphertext =
                Base64.getUrlDecoder()
                        .decode(parts[2]);

        ciphertext[ciphertext.length - 1] =
                (byte) (
                        ciphertext[
                                ciphertext.length - 1
                                ] ^ 1
                );

        String tamperedCiphertext =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(ciphertext);

        return parts[0]
                + ":"
                + parts[1]
                + ":"
                + tamperedCiphertext;
    }

    private void assertEncryptionFailed(
            org.assertj.core.api.ThrowableAssert
                    .ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
                .isInstanceOf(
                        BusinessException.class
                )
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(
                            businessException.getCode()
                    ).isEqualTo(
                            ErrorCode
                                    .COMPANION_ENCRYPTION_FAILED
                    );
                });
    }
}