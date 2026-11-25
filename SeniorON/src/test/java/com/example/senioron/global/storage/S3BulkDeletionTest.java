package com.example.senioron.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3BulkDeletionTest {

    private final S3Client client = mock(S3Client.class);
    private final S3Service service = new S3Service(client, mock(S3Presigner.class));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @Test
    void deletesUpToOneThousandKeysInOneRequest() {
        List<String> keys = IntStream.range(0, 1000)
                .mapToObj(index -> "photo-" + index + ".jpg").toList();
        given(client.deleteObjects(any(DeleteObjectsRequest.class)))
                .willReturn(DeleteObjectsResponse.builder().build());

        assertThat(service.deleteObjects(keys)).isEmpty();

        ArgumentCaptor<DeleteObjectsRequest> requests =
                ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(client).deleteObjects(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.bucket()).isEqualTo("test-bucket");
            assertThat(request.delete().quiet()).isTrue();
            assertThat(request.delete().objects()).hasSizeLessThanOrEqualTo(1000);
        });
        assertThat(requests.getAllValues().stream()
                .flatMap(request -> request.delete().objects().stream())
                .map(ObjectIdentifier::key).toList())
                .containsExactlyElementsOf(keys);
    }

    @Test
    void rejectsMoreThanOneThousandKeysBeforeCallingS3() {
        List<String> keys = IntStream.range(0, 1001)
                .mapToObj(index -> "photo-" + index + ".jpg").toList();

        assertThatThrownBy(() -> service.deleteObjects(keys))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test
    void returnsPerObjectErrorsEvenWhenTheRequestSucceeds() {
        given(client.deleteObjects(any(DeleteObjectsRequest.class)))
                .willReturn(DeleteObjectsResponse.builder().errors(
                        S3Error.builder().key("failed.jpg").code("AccessDenied")
                                .message("Denied").build()).build());

        assertThat(service.deleteObjects(List.of("deleted.jpg", "failed.jpg")))
                .isEqualTo(Map.of("failed.jpg", "AccessDenied: Denied"));
    }

    @Test
    void propagatesRequestFailureSoTheWorkerCanRetryAllTasks() {
        given(client.deleteObjects(any(DeleteObjectsRequest.class)))
                .willThrow(SdkClientException.create("Network unavailable"));

        assertThatThrownBy(() -> service.deleteObjects(List.of("photo.jpg")))
                .isInstanceOf(SdkClientException.class);
    }

    @Test
    void makesNoRequestForEmptyKeys() {
        assertThat(service.deleteObjects(List.of())).isEmpty();
        verifyNoInteractions(client);
    }
}
