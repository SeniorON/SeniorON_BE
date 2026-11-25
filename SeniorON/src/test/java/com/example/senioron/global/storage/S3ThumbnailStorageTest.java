package com.example.senioron.global.storage;

import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3ThumbnailStorageTest {

    private final S3Client client = mock(S3Client.class);
    private final S3Service service = new S3Service(client, mock(S3Presigner.class));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @Test
    void downloadsOriginalBytesFromConfiguredBucket() {
        byte[] original = {1, 2, 3};
        given(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), original
                ));

        assertThat(service.download("original.jpg")).isEqualTo(original);

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("original.jpg");
    }

    @Test
    void uploadsThumbnailBytesWithJpegContentTypeAndDedicatedKey() throws Exception {
        byte[] thumbnail = {1, 2, 3};

        String key = service.uploadThumbnail(thumbnail);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(key).matches("family-photos/thumbnails/[0-9a-f-]{36}\\.jpg");
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        try (InputStream stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(thumbnail);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void propagatesSdkFailureForCallerFallbackOrCleanup(boolean downloading) {
        SdkClientException failure = SdkClientException.create("S3 unavailable");
        if (downloading) {
            given(client.getObjectAsBytes(any(GetObjectRequest.class))).willThrow(failure);
            assertThatThrownBy(() -> service.download("original.jpg"))
                    .isInstanceOf(IllegalStateException.class).hasCause(failure);
        } else {
            given(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willThrow(failure);
            assertThatThrownBy(() -> service.uploadThumbnail(new byte[]{1}))
                    .isInstanceOf(IllegalStateException.class).hasCause(failure);
        }
    }
}
